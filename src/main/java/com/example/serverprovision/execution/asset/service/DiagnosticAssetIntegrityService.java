package com.example.serverprovision.execution.asset.service;

import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SealedFileCondition;
import com.example.serverprovision.execution.config.PxeAssetsProperties;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.util.FileSize;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * 진단 리눅스 자산의 <b>단일 슬롯 조회 + 교체·롤백을 위한 봉인 부품</b>. 통합 대시보드의 6종 집계·전역
 * 봉인은 {@link com.example.serverprovision.execution.asset.area.DiagnosticSystemAssetArea} 어댑터가
 * {@code SystemAssetDashboardService} 를 통해 담당하고(집계 경로 SSOT 일원화), 이 서비스는 상세 화면의 단일
 * 슬롯 조회({@link #loadSlot})와 교체/롤백의 전제·재봉인({@link #requireServingRoot}·{@link #sealOne})만 갖는다.
 * DB 를 쓰지 않는다 — 슬롯은 고정 {@link DiagnosticAsset} enum 이고, 무결성 기준은 파일 옆 {@code .provision.json}
 * 마커 사이드카(디렉토리는 in-tree)가 SSOT 다(DEC-14 — 자원 도메인 아님, 엔티티 없음).
 *
 * <p><b>판정·봉인 위임(SSOT)</b>: 프로브·판정 사다리·마커 조립의 실 로직은 영역 무관 부품
 * {@link SealedFileInspector} 가 SSOT 다(진단·TFTP 영역이 공유). 이 서비스는 그 결과({@link AssetSlotStatus})
 * 를 진단 화면 계약({@link SystemAssetSlotResponse})으로 변환하는 얇은 층이다. 판정 문자열은
 * {@link SealedFileCondition} 이 기존 {@code IntegrityStatus} 와 byte 동일한 label/badge 를 승계하므로
 * E1-I-1/2 진단 테스트가 회귀하지 않는다.</p>
 *
 * <p><b>reconciliation 스윕 제외(격리)</b>: 이 마커들은 {@code pxe.assets.root} 아래에 있고, 그 루트는
 * reconciliation 스캔 루트/allowed-roots 에 포함되지 않는다 — 그래야 진단 자산 마커가 ORPHAN 으로
 * 오탐되지 않는다. 진단 자산은 이 화면의 자체 verify 만 갖는다.</p>
 *
 * <p><b>서빙 비활성 graceful</b>: 자산 서빙 설정({@code pxe.assets.root})이 없으면 {@link PxeAssetsProperties}
 * 빈 자체가 없다({@code @ConditionalOnProperty}). {@link ObjectProvider} 로 조회해 강결합을 피하고,
 * 미설정 환경에서도 상세 조회는 오류 없이 "서빙 비활성" 상태로 반환된다.</p>
 */
@Service
@RequiredArgsConstructor
public class DiagnosticAssetIntegrityService {

    /** 마커 {@code resourceType} 문자열. reconciliation 스윕 대상이 아니므로 {@code ResourceType} enum 을 건드리지 않는다. */
    private static final String RESOURCE_TYPE = "DIAGNOSTIC_ASSET";

    private final ObjectProvider<PxeAssetsProperties> propertiesProvider;
    private final SealedFileInspector inspector;

    /**
     * 단일 슬롯 조회(상세 화면용) — 상세는 이 슬롯 하나만 해시한다(행 클릭·교체/롤백 PRG 로 상세 진입이 잦으므로
     * 208MB 급 자산 6배 재해시를 피한다). 통합 대시보드의 6종 집계는 {@link com.example.serverprovision.execution.asset.area.DiagnosticSystemAssetArea}
     * 어댑터가 {@code SystemAssetDashboardService} 를 통해 담당한다 — 이 서비스는 상세·교체 도메인 관심사만 갖는다.
     * 서빙 비활성이면 offSlot, 아니면 인스펙터 프로브(어댑터와 같은 부품).
     */
    public SystemAssetSlotResponse loadSlot(DiagnosticAsset asset) {
        PxeAssetsProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            return offSlot(asset);
        }
        return toResponse(asset, inspector.inspect(asset.resolve(props.getRoot()), asset.layout()));
    }

    /** 서빙 활성 여부(비throwing) — 상세 화면의 교체·롤백 버튼 disabled 게이트. {@link #requireServingRoot()} 와 동일 조건 SSOT. */
    public boolean isServing() {
        return propertiesProvider.getIfAvailable() != null;
    }

    /**
     * 서빙 활성 검증 후 자산 루트 반환. 교체·롤백처럼 자산 위치가 필요한 쓰기 액션의 공통 전제다 —
     * 서빙 비활성(빈 부재)이면 자산 위치를 알 수 없으므로 409 로 거절한다(UI 1차 차단의 안전망).
     */
    public Path requireServingRoot() {
        PxeAssetsProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            throw SystemAssetServingDisabledException.servingDisabled();
        }
        return props.getRoot();
    }

    /**
     * 한 슬롯의 현재 상태를 신뢰 기준으로 마커에 기록/갱신한다 — 존재하면 기록(true), 부재면 건너뜀(false).
     * 교체(E1-I-2-a)가 스왑 직후 자동 재봉인에 재사용한다. 마커 조립·기록의 실 로직은 {@link SealedFileInspector}
     * 가 SSOT 이며, 여기서는 진단 자산의 resourceType/서수/파일명을 넘길 뿐이다(복붙 금지).
     */
    public boolean sealOne(DiagnosticAsset asset, Path root) {
        return inspector.seal(asset.resolve(root), asset.layout(), RESOURCE_TYPE, asset.ordinal(), asset.filename());
    }

    // ── 응답 조립 (뷰는 문자열만 받는다) ──────────────────────────────────────

    private SystemAssetSlotResponse toResponse(DiagnosticAsset asset, AssetSlotStatus status) {
        boolean present = status.present();
        return new SystemAssetSlotResponse(
                asset.name(), asset.label(), asset.category().label(), asset.filename(), layoutLabel(asset.layout()),
                present, asset.replaceable(), present ? FileSize.format(status.sizeBytes()) : "—", status.modifiedAt(),
                status.condition().label(), status.condition().badgeClass(), asset.replaceCadence());
    }

    /** 서빙 비활성 슬롯 — NOT_CONFIGURED 판정("서빙 비활성"/gray)을 부재 응답으로 변환한다(기존 offSlot 과 동일 출력). */
    private SystemAssetSlotResponse offSlot(DiagnosticAsset asset) {
        return toResponse(asset, AssetSlotStatus.notPresent(SealedFileCondition.NOT_CONFIGURED));
    }

    private static String layoutLabel(MarkerLayout layout) {
        return layout == MarkerLayout.IN_TREE ? "디렉토리" : "단일 파일";
    }

}
