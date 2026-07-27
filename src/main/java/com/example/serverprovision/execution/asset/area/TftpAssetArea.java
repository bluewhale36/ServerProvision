package com.example.serverprovision.execution.asset.area;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.enums.TftpAsset;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.SealedFileInspector;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.SealedFileCondition;
import com.example.serverprovision.execution.asset.spi.SystemAssetArea;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.config.TftpAssetsProperties;
import com.example.serverprovision.global.marker.MarkerLayout;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * TFTP 부팅 인프라 자산 영역(Service Provider Interface — 도메인이 구현해 끼우는 확장점). 진단 자산과 같은
 * 파일 봉인({@code .provision.json} 사이드카) 모델을 쓰므로 probe/verify/seal 의 실 로직을 공유 부품
 * {@link SealedFileInspector} 에 위임하고, 이 어댑터는 슬롯 메타 조립과 영역 라우팅만 맡는다(중복 회수, 층만 남김).
 *
 * <p><b>읽기·봉인 전용</b>: {@link TftpAsset} 은 배포판 산출물이라 UI 업로드 교체 대상이 아니다(활성화 SPI
 * 합의 유지 — 교체·롤백·업로드 없음). {@link #supportsSeal()} 는 기본 {@code true} 를 유지한다 — {@code ipxe.efi}
 * 는 정적 파일이라 파일 봉인이 의미가 있고, 갱신 절차 "복사 후 재봉인" 의 재봉인이 곧 {@link #seal()} 이다.</p>
 *
 * <p><b>서빙 비활성 graceful</b>: {@code pxe.tftp.root} 미설정이면 {@link TftpAssetsProperties} 빈 자체가
 * 없다({@code @ConditionalOnProperty}). {@link ObjectProvider} 로 조회해 강결합을 피하고, 미설정 환경에서도
 * 대시보드는 오류 없이 {@link SealedFileCondition#NOT_CONFIGURED} 판정으로 조회된다(진단 영역과 동형).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TftpAssetArea implements SystemAssetArea {

    /** 마커 {@code resourceType} 문자열. reconciliation 스윕 대상이 아니므로 {@code ResourceType} enum 을 건드리지 않는다. */
    private static final String RESOURCE_TYPE = "TFTP_ASSET";

    private final ObjectProvider<TftpAssetsProperties> propertiesProvider;
    private final SealedFileInspector inspector;

    @Override
    public SystemAssetAreaKey areaKey() {
        return SystemAssetAreaKey.TFTP;
    }

    @Override
    public String displayName() {
        return "TFTP 부팅 인프라 자산";
    }

    @Override
    public AreaAvailability availability() {
        return propertiesProvider.getIfAvailable() != null
                ? AreaAvailability.CONFIGURED
                : AreaAvailability.NOT_CONFIGURED;
    }

    @Override
    public List<SystemAssetSlot> slots() {
        return Arrays.stream(TftpAsset.values())
                .map(asset -> (SystemAssetSlot) new TftpSlot(asset))
                .toList();
    }

    /**
     * 슬롯 하나의 현황·무결성을 프로브한다. 서빙 비활성이면 {@link SealedFileCondition#NOT_CONFIGURED} 부재
     * 판정으로 흡수하고, 활성이면 진단 자산과 같은 봉인 검증 사다리를 {@link SealedFileInspector} 에 위임한다.
     * 부재·미봉인·서빙비활성을 전부 판정으로 흡수하므로 예외로 거절하지 않는다(조회는 실패하지 않음).
     */
    @Override
    public AssetSlotStatus inspect(SystemAssetSlot slot) {
        TftpAssetsProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            return AssetSlotStatus.notPresent(SealedFileCondition.NOT_CONFIGURED);
        }
        TftpAsset asset = unwrap(slot);
        return inspector.inspect(asset.resolve(props.getRoot()), asset.layout());
    }

    /**
     * 영역 단위 무결성 봉인 — 존재하는 슬롯의 현재 해시를 마커로 기록/갱신한다(자산 파일은 건드리지 않음).
     * 서빙 비활성이면 자산 위치를 알 수 없으므로 409 로 거절한다(UI 1차 차단의 안전망 — 정상 흐름은 버튼 disabled).
     * 마커 조립·기록의 실 로직은 진단 자산과 공유하는 {@link SealedFileInspector} 가 SSOT 다(복붙 금지).
     */
    @Override
    public SealResult seal() {
        TftpAssetsProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            // TFTP 는 PXE_TFTP_ROOT 미설정이 원인이나, 공유 예외 메시지는 진단 자산의 PXE_ASSETS_ROOT 를
            // 예시로 든다 — "서빙 비활성 → 봉인 불가" 라는 의미·상태코드(409)는 동일하다. 영역별 메시지가
            // 필요해지면 후속 슬라이스에서 팩토리를 분화한다.
            throw SystemAssetServingDisabledException.servingDisabled();
        }
        Path root = props.getRoot();
        int sealed = 0;
        int skipped = 0;
        for (TftpAsset asset : TftpAsset.values()) {
            boolean recorded = inspector.seal(
                    asset.resolve(root), asset.layout(), RESOURCE_TYPE, asset.ordinal(), asset.filename());
            if (recorded) {
                sealed++;
            } else {
                skipped++;
            }
        }
        log.info("[tftp-asset] 무결성 봉인 — 기록 {}건, 건너뜀(부재) {}건", sealed, skipped);
        return new SealResult(sealed, skipped);
    }

    /** {@link #slots()} 가 발급한 어댑터에서 원본 enum 을 복원한다 — 대시보드는 항상 이 영역의 슬롯만 되돌려준다. */
    private TftpAsset unwrap(SystemAssetSlot slot) {
        if (slot instanceof TftpSlot tftpSlot) {
            return tftpSlot.asset();
        }
        throw new IllegalArgumentException("TFTP 영역 슬롯이 아닙니다 : " + slot.slotKey());
    }

    /**
     * {@link TftpAsset} enum 을 SPI 슬롯 메타로 감싸는 어댑터. enum 자신은 읽기·봉인 전용이라 {@code slotKey}/
     * {@code layoutLabel}/{@code replaceable} 같은 SPI 표시 개념을 갖지 않으므로, 그 조립을 여기서만 한다.
     */
    private record TftpSlot(TftpAsset asset) implements SystemAssetSlot {

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

        /** TFTP 자산은 읽기·봉인 전용 — UI 교체 폼 없음. */
        @Override
        public boolean replaceable() {
            return false;
        }

        @Override
        public String replaceCadence() {
            return asset.replaceCadence();
        }
    }
}
