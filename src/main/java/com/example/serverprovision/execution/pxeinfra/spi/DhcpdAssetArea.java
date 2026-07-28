package com.example.serverprovision.execution.pxeinfra.spi;

import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetContextItem;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.ObservationSeverity;
import com.example.serverprovision.execution.asset.spi.SystemAssetArea;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpdConfigInspector;
import com.example.serverprovision.execution.pxeinfra.inspect.SystemServiceInspector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * dhcpd(DHCP 서버) PXE 인프라 자산 영역(Service Provider Interface — 도메인이 구현해 끼우는 확장점). 진단·TFTP
 * 영역과 달리 <b>파일 봉인 모델이 아니다</b> — 조각 파일 존재·전체 구성 문법({@code dhcpd -t})·systemd 서비스
 * 상태·임대 현황을 관측해 판정한다. 대시보드 서비스는 이 영역을 다른 영역과 동일하게 순회할 뿐이라 집계에
 * 영역 분기가 늘지 않는다(조건분기 legacy 확장 금지 — 다형성으로 흡수).
 *
 * <p><b>봉인 미지원</b>: 해시 봉인 개념이 없으므로 {@link #supportsSeal()} 를 {@code false} 로 명시 오버라이드한다 —
 * 그러면 기본 {@link SystemAssetArea#seal()} 가 {@code SystemAssetSealNotSupportedException} 을 던지고(UI 1차 차단의
 * 서버 안전망), 대시보드·설정 화면은 봉인 버튼 자체를 렌더하지 않는다(같은 플래그 SSOT). {@code seal()} 은 오버라이드하지 않는다.</p>
 *
 * <p><b>미구성 게이팅</b>: {@code pxe.dhcpd.fragment-path} 미설정이면 {@link PxeInfraProperties} 빈이 통째로 없다
 * ({@code @ConditionalOnProperty}). {@link ObjectProvider} 로 조회해 미설정 환경에서도 대시보드가 오류 없이
 * "서빙 비활성" 으로 조회되며, {@link #context()} 는 미구성일 때 특권 명령을 아예 spawn 하지 않는다(빈 목록).</p>
 */
@Slf4j
@Component
public class DhcpdAssetArea implements SystemAssetArea {

    /** 조각 파일명을 알 수 없는 미구성 상태에서 슬롯 표에 노출할 대체 표기. */
    private static final String FRAGMENT_LABEL = "dhcpd 조각";
    private static final String UNCONFIGURED_FILENAME = "(미구성)";

    private final ObjectProvider<PxeInfraProperties> propertiesProvider;
    private final DhcpdConfigInspector configInspector;
    private final SystemServiceInspector serviceInspector;
    private final DhcpLeaseReader leaseReader;

    public DhcpdAssetArea(ObjectProvider<PxeInfraProperties> propertiesProvider,
                          DhcpdConfigInspector configInspector,
                          SystemServiceInspector serviceInspector,
                          DhcpLeaseReader leaseReader) {
        this.propertiesProvider = propertiesProvider;
        this.configInspector = configInspector;
        this.serviceInspector = serviceInspector;
        this.leaseReader = leaseReader;
    }

    @Override
    public SystemAssetAreaKey areaKey() {
        return SystemAssetAreaKey.DHCPD;
    }

    @Override
    public String displayName() {
        return "PXE 인프라 (dhcpd)";
    }

    @Override
    public AreaAvailability availability() {
        return propertiesProvider.getIfAvailable() != null
                ? AreaAvailability.CONFIGURED
                : AreaAvailability.NOT_CONFIGURED;
    }

    @Override
    public List<SystemAssetSlot> slots() {
        PxeInfraProperties props = propertiesProvider.getIfAvailable();
        String filename = props != null
                ? props.getFragmentPath().getFileName().toString()
                : UNCONFIGURED_FILENAME;
        return List.of(new DhcpdFragmentSlot(filename));
    }

    /**
     * 조각 슬롯 하나의 현황·무결성을 프로브한다. 미구성이면 서빙 비활성 부재 판정으로 흡수하고, 구성이면 조각
     * 존재 여부에 따라 크기·수정시각과 전체 구성 문법 판정을 조립한다. 부재·미구성·문법 오류를 전부 판정으로
     * 흡수하므로 예외로 거절하지 않는다(조회는 실패하지 않음).
     */
    @Override
    public AssetSlotStatus inspect(SystemAssetSlot slot) {
        PxeInfraProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            return AssetSlotStatus.notPresent(ConfigFileCondition.NOT_CONFIGURED);
        }
        Path fragment = props.getFragmentPath();
        if (!Files.exists(fragment)) {
            // configInspector.inspect() 는 조각 부재를 ABSENT 로 판정한다(문법 검사 결과와 무관).
            return AssetSlotStatus.notPresent(configInspector.inspect());
        }
        return AssetSlotStatus.present(sizeOf(fragment), modifiedAtOf(fragment), configInspector.inspect());
    }

    @Override
    public boolean supportsSeal() {
        return false;
    }

    /**
     * 영역 헤더 관측 chip — dhcpd 서비스 상태·활성 임대 건수. <b>구성일 때만</b> 조립한다(미구성이면 빈 목록):
     * 미구성 상태에서 특권 명령({@code systemctl}·임대 파일 읽기)을 spawn 하지 않기 위한 게이팅이다.
     */
    @Override
    public List<AssetContextItem> context() {
        if (availability() != AreaAvailability.CONFIGURED) {
            return List.of();
        }
        ServiceState service = serviceInspector.status();
        int activeLeases = leaseReader.read().activeCount(Instant.now());
        return List.of(
                new AssetContextItem("dhcpd 서비스", service.label(), service.severity()),
                new AssetContextItem("활성 임대", activeLeases + "건", ObservationSeverity.INFO));
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private Instant modifiedAtOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * dhcpd 조각 파일 슬롯의 정적 메타. 조각은 세팅 반영으로 갱신될 뿐 UI 업로드 교체 대상이 아니라
     * {@code replaceable} 은 false 다. 파일명은 미구성 시 알 수 없어 {@link #UNCONFIGURED_FILENAME} 로 대체된다.
     */
    private record DhcpdFragmentSlot(String filename) implements SystemAssetSlot {

        @Override
        public String slotKey() {
            return "FRAGMENT";
        }

        @Override
        public String label() {
            return FRAGMENT_LABEL;
        }

        @Override
        public String category() {
            return "DHCP 구성";
        }

        @Override
        public String layoutLabel() {
            return "단일 파일";
        }

        @Override
        public boolean replaceable() {
            return false;
        }

        @Override
        public String replaceCadence() {
            return "세팅 반영 시(E1-I-3-c)";
        }
    }
}
