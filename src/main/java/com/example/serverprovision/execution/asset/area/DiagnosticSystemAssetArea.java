package com.example.serverprovision.execution.asset.area;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.SealedFileInspector;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SealedFileCondition;
import com.example.serverprovision.execution.asset.spi.SystemAssetArea;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.global.marker.MarkerLayout;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * 진단 리눅스 부팅 자산 영역({@link DiagnosticAsset} 6종)의 {@link SystemAssetArea} 어댑터. 통합 시스템 자산
 * 대시보드가 이 영역을 {@link SystemAssetAreaKey#DIAGNOSTIC} 로 라우팅한다. 슬롯 집합은 고정 enum 을 감싼
 * {@link DiagnosticAssetSlot} 이고, 슬롯별 판정·봉인은 {@link SealedFileInspector} 에 위임한다.
 *
 * <p><b>의존 최소화</b>: {@code DiagnosticAssetIntegrityService} 를 주입하지 않고 {@link SealedFileInspector} 를
 * 직접 쓴다 — 서비스 주입은 이중 판정과 (서비스↔어댑터) 순환 위험을 만든다. 서빙 여부는 원본 {@code isServing}
 * 과 동일하게 {@link PxeAssetsProperties} {@link ObjectProvider} 의 가용성으로 판정한다(미설정 환경은 빈 자체가
 * 없어 {@code getIfAvailable()==null}).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiagnosticSystemAssetArea implements SystemAssetArea {

    /** 마커 {@code resourceType} — 원본 {@code DiagnosticAssetIntegrityService.RESOURCE_TYPE} 과 동일. reconciliation 스윕 대상 아님. */
    private static final String RESOURCE_TYPE = "DIAGNOSTIC_ASSET";

    private static final List<SystemAssetSlot> SLOTS = List.of(DiagnosticAsset.values()).stream()
            .<SystemAssetSlot>map(DiagnosticAssetSlot::new)
            .toList();

    private final ObjectProvider<PxeAssetsProperties> propertiesProvider;
    private final SealedFileInspector inspector;

    @Override
    public SystemAssetAreaKey areaKey() {
        return SystemAssetAreaKey.DIAGNOSTIC;
    }

    @Override
    public String displayName() {
        return "진단 리눅스 부팅 자산";
    }

    @Override
    public AreaAvailability availability() {
        return propertiesProvider.getIfAvailable() != null
                ? AreaAvailability.CONFIGURED
                : AreaAvailability.NOT_CONFIGURED;
    }

    @Override
    public List<SystemAssetSlot> slots() {
        return SLOTS;
    }

    /**
     * 슬롯 하나의 현황·무결성 프로브. 서빙 비활성이면 자산 위치를 알 수 없어 NOT_CONFIGURED 로 흡수하고,
     * 활성이면 {@link SealedFileInspector} 가 존재·크기·수정시각·봉인 판정을 산출한다(조회는 실패하지 않음).
     */
    @Override
    public AssetSlotStatus inspect(SystemAssetSlot slot) {
        PxeAssetsProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            return AssetSlotStatus.notPresent(SealedFileCondition.NOT_CONFIGURED);
        }
        DiagnosticAsset asset = assetOf(slot);
        return inspector.inspect(asset.resolve(props.getRoot()), asset.layout());
    }

    /**
     * 영역 봉인 — 존재하는 슬롯 전량의 현재 상태를 마커에 기록한다(부재 슬롯은 건너뜀). 서빙 비활성은 자산 위치를
     * 알 수 없으므로 409 로 거절한다(UI 1차 차단의 서버 안전망 — 정상 흐름은 UI 가 봉인 버튼을 비활성화).
     */
    @Override
    public SealResult seal() {
        PxeAssetsProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            throw SystemAssetServingDisabledException.servingDisabled();
        }
        Path root = props.getRoot();
        int sealed = 0;
        int skipped = 0;
        for (DiagnosticAsset asset : DiagnosticAsset.values()) {
            boolean recorded = inspector.seal(
                    asset.resolve(root), asset.layout(), RESOURCE_TYPE, asset.ordinal(), asset.filename());
            if (recorded) {
                sealed++;
            } else {
                skipped++;
            }
        }
        log.info("[diagnostic-asset] 무결성 봉인 — 기록 {}건, 건너뜀(부재) {}건", sealed, skipped);
        return new SealResult(sealed, skipped);
    }

    private DiagnosticAsset assetOf(SystemAssetSlot slot) {
        if (slot instanceof DiagnosticAssetSlot(DiagnosticAsset asset)) {
            return asset;
        }
        // 이 영역이 발급한 슬롯만 넘어와야 한다 — 타 영역 슬롯이 오면 라우팅 버그다.
        throw new IllegalArgumentException("진단 영역 슬롯이 아닙니다 : " + slot.slotKey());
    }

    /**
     * 고정 {@link DiagnosticAsset} enum 을 {@link SystemAssetSlot} 로 래핑하는 어댑터 record. {@code slotKey} 는
     * enum 상수명이라 URL 경로 변수({@code /{slot}})가 {@code DiagnosticAsset.valueOf} 로 안전 매핑된다.
     */
    private record DiagnosticAssetSlot(DiagnosticAsset asset) implements SystemAssetSlot {

        @Override
        public String slotKey() {
            return asset.name();
        }

        @Override
        public String label() {
            return asset.label();
        }

        @Override
        public String filename() {
            return asset.filename();
        }

        @Override
        public String category() {
            return asset.category().label();
        }

        @Override
        public String layoutLabel() {
            return asset.layout() == MarkerLayout.IN_TREE ? "디렉토리" : "단일 파일";
        }

        @Override
        public boolean replaceable() {
            return asset.replaceable();
        }

        @Override
        public String replaceCadence() {
            return asset.replaceCadence();
        }
    }
}
