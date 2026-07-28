package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.dto.response.AssetContextItemResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetAreaGroupResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetOverviewResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.exception.SystemAssetAreaNotFoundException;
import com.example.serverprovision.execution.asset.spi.AssetCondition;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SystemAssetArea;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.global.util.FileSize;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 통합 시스템 자산 대시보드의 집계·라우팅. 등록된 {@link SystemAssetArea} 확장점 구현 전량을 주입받아
 * {@link SystemAssetAreaKey} 로 라우팅 맵을 세운다 — 영역이 늘어도(진단·TFTP·향후) 이 서비스는
 * 조건분기 없이 순회할 뿐이라 코드가 자라지 않는다(조건분기 legacy 확장 금지 — 다형성으로 흡수).
 *
 * <p><b>조회는 실패하지 않는다</b>: 각 영역의 {@link SystemAssetArea#inspect}가 부재·미봉인·서빙비활성을
 * 전부 판정({@link AssetCondition})으로 흡수하므로, 대시보드 조립은 예외로 거절하지 않는다. 봉인 같은
 * 쓰기 액션만이 라우팅 실패(404)·미지원/미구성(409)으로 거절된다.</p>
 */
@Service
public class SystemAssetDashboardService {

    /** 라우팅 맵 — enum 선언 순서(DIAGNOSTIC, TFTP)로 순회되도록 {@link EnumMap} 을 쓴다. */
    private final Map<SystemAssetAreaKey, SystemAssetArea> areasByKey;

    public SystemAssetDashboardService(List<SystemAssetArea> areas) {
        Map<SystemAssetAreaKey, SystemAssetArea> map = new EnumMap<>(SystemAssetAreaKey.class);
        for (SystemAssetArea area : areas) {
            SystemAssetArea previous = map.putIfAbsent(area.areaKey(), area);
            if (previous != null) {
                // 같은 라우팅 키를 두 구현이 주장하면 URL 이 어느 영역으로 가는지 모호해진다 — 기동 시 즉시 실패.
                throw new IllegalStateException("중복된 시스템 자산 영역 키 : " + area.areaKey());
            }
        }
        this.areasByKey = map;
    }

    /** 통합 대시보드 조립 — 등록 영역을 순회해 영역별 슬롯 현황·집계와 전 영역 합계를 채운다. */
    public SystemAssetOverviewResponse loadOverview() {
        List<SystemAssetAreaGroupResponse> groups = new ArrayList<>();
        int totalOk = 0;
        int totalCount = 0;
        for (SystemAssetArea area : areasByKey.values()) {
            SystemAssetAreaGroupResponse group = toGroup(area);
            groups.add(group);
            totalOk += group.okCount();
            totalCount += group.totalCount();
        }
        return new SystemAssetOverviewResponse(groups, totalOk, totalCount);
    }

    /** 영역 라우팅 — 없는 키(문자열 파싱 실패 또는 미등록)는 404 로 거절(주소 위조 방어). */
    public SystemAssetArea areaOf(String rawKey) {
        SystemAssetAreaKey key;
        try {
            key = SystemAssetAreaKey.valueOf(rawKey);
        } catch (IllegalArgumentException e) {
            throw SystemAssetAreaNotFoundException.of(rawKey);
        }
        SystemAssetArea area = areasByKey.get(key);
        if (area == null) {
            throw SystemAssetAreaNotFoundException.of(key);
        }
        return area;
    }

    /** 영역 단위 무결성 봉인. 봉인 미지원 영역은 확장점 default 가 {@code SystemAssetSealNotSupportedException}(409). */
    public SealResult seal(String areaKey) {
        return areaOf(areaKey).seal();
    }

    // ── 조립 헬퍼 ────────────────────────────────────────────────────────────

    private SystemAssetAreaGroupResponse toGroup(SystemAssetArea area) {
        List<SystemAssetSlot> slots = area.slots();
        List<SystemAssetSlotResponse> rows = new ArrayList<>(slots.size());
        int ok = 0;
        for (SystemAssetSlot slot : slots) {
            AssetSlotStatus status = area.inspect(slot);
            if (status.condition().healthy()) {
                ok++;
            }
            rows.add(toRow(slot, status));
        }
        List<AssetContextItemResponse> context = area.context().stream()
                .map(i -> new AssetContextItemResponse(i.label(), i.value(), i.severity().badgeClass()))
                .toList();
        return new SystemAssetAreaGroupResponse(
                area.areaKey().name(), area.displayName(), area.availability().name(),
                rows, ok, slots.size(), area.supportsSeal(), context);
    }

    private SystemAssetSlotResponse toRow(SystemAssetSlot slot, AssetSlotStatus status) {
        AssetCondition condition = status.condition();
        return new SystemAssetSlotResponse(
                slot.slotKey(), slot.label(), slot.category(), slot.filename(), slot.layoutLabel(),
                status.present(), slot.replaceable(),
                status.present() ? FileSize.format(status.sizeBytes()) : "—", status.modifiedAt(),
                condition.label(), condition.badgeClass(), slot.replaceCadence());
    }
}
