package com.example.serverprovision.execution.pxeinfra.spi;

import com.example.serverprovision.execution.asset.exception.SystemAssetSealNotSupportedException;
import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetContextItem;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.ObservationSeverity;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpdConfigInspector;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseEntry;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseSnapshot;
import com.example.serverprovision.execution.pxeinfra.inspect.SystemServiceInspector;
import com.example.serverprovision.execution.vo.IpAddressVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * dhcpd 자산 영역(Service Provider Interface 확장점) 검증 — 미구성이면 슬롯을 NOT_CONFIGURED 로 흡수하고 헤더
 * 관측 chip 을 비워 특권 명령을 아예 spawn 하지 않으며(게이팅), 구성이면 조각 슬롯을 판정하고 서비스·임대 chip
 * 2개를 severity 매핑과 함께 조립한다. 봉인은 미지원(default throw)임을 확인한다.
 */
class DhcpdAssetAreaTest {

    @TempDir
    Path tmp;

    // ── 미구성 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("미구성 — 슬롯 NOT_CONFIGURED + context 빈 목록, 관측 협력자 전혀 호출 안 함(특권 명령 미실행)")
    void unconfigured_absorbedWithoutSpawningPrivilegedCommands() {
        DhcpdConfigInspector configInspector = mock(DhcpdConfigInspector.class);
        SystemServiceInspector serviceInspector = mock(SystemServiceInspector.class);
        DhcpLeaseReader leaseReader = mock(DhcpLeaseReader.class);
        DhcpdAssetArea area = new DhcpdAssetArea(
                providerOf(null), configInspector, serviceInspector, leaseReader);

        assertThat(area.areaKey()).isEqualTo(SystemAssetAreaKey.DHCPD);
        assertThat(area.availability()).isEqualTo(AreaAvailability.NOT_CONFIGURED);

        SystemAssetSlot slot = area.slots().get(0);
        assertThat(slot.slotKey()).isEqualTo("FRAGMENT");
        assertThat(slot.replaceable()).isFalse();

        AssetSlotStatus status = area.inspect(slot);
        assertThat(status.present()).isFalse();
        assertThat(status.condition()).isEqualTo(ConfigFileCondition.NOT_CONFIGURED);

        assertThat(area.context()).isEmpty();

        // 미구성 게이팅의 핵심: 조각 문법 검사·서비스 조회·임대 읽기 어느 것도 spawn 되지 않는다.
        verifyNoInteractions(configInspector, serviceInspector, leaseReader);
    }

    @Test
    @DisplayName("봉인 미지원 — supportsSeal false, 기본 seal() 은 SealNotSupported 로 거절")
    void sealUnsupported() {
        DhcpdAssetArea area = new DhcpdAssetArea(
                providerOf(null), mock(DhcpdConfigInspector.class),
                mock(SystemServiceInspector.class), mock(DhcpLeaseReader.class));

        assertThat(area.supportsSeal()).isFalse();
        assertThatThrownBy(area::seal).isInstanceOf(SystemAssetSealNotSupportedException.class);
    }

    // ── 구성 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("구성 + 조각 존재 — 슬롯 present(크기·판정), 파일명은 조각 파일명")
    void configured_fragmentPresent_slotJudged() throws IOException {
        Path fragment = tmp.resolve("pxe-dhcpd.conf");
        Files.writeString(fragment, "subnet 10.0.2.0 netmask 255.255.255.0 {}\n");
        long size = Files.size(fragment);

        DhcpdConfigInspector configInspector = mock(DhcpdConfigInspector.class);
        given(configInspector.inspect()).willReturn(ConfigFileCondition.SYNTAX_OK);
        DhcpdAssetArea area = new DhcpdAssetArea(
                providerOf(propsFor(fragment)), configInspector,
                mock(SystemServiceInspector.class), mock(DhcpLeaseReader.class));

        assertThat(area.availability()).isEqualTo(AreaAvailability.CONFIGURED);
        SystemAssetSlot slot = area.slots().get(0);
        assertThat(slot.filename()).isEqualTo("pxe-dhcpd.conf");

        AssetSlotStatus status = area.inspect(slot);
        assertThat(status.present()).isTrue();
        assertThat(status.sizeBytes()).isEqualTo(size);
        assertThat(status.condition()).isEqualTo(ConfigFileCondition.SYNTAX_OK);
    }

    @Test
    @DisplayName("구성 + 조각 부재 — 슬롯 notPresent, 판정은 config 검사기의 ABSENT")
    void configured_fragmentAbsent_slotAbsent() {
        Path fragment = tmp.resolve("missing-fragment.conf");   // 생성하지 않는다

        DhcpdConfigInspector configInspector = mock(DhcpdConfigInspector.class);
        given(configInspector.inspect()).willReturn(ConfigFileCondition.ABSENT);
        DhcpdAssetArea area = new DhcpdAssetArea(
                providerOf(propsFor(fragment)), configInspector,
                mock(SystemServiceInspector.class), mock(DhcpLeaseReader.class));

        AssetSlotStatus status = area.inspect(area.slots().get(0));
        assertThat(status.present()).isFalse();
        assertThat(status.condition()).isEqualTo(ConfigFileCondition.ABSENT);
    }

    @Test
    @DisplayName("구성 — context chip 2개(서비스·임대) + severity 매핑(ACTIVE→OK, 임대→INFO)")
    void configured_contextChips() {
        Path fragment = tmp.resolve("pxe-dhcpd.conf");

        SystemServiceInspector serviceInspector = mock(SystemServiceInspector.class);
        given(serviceInspector.status()).willReturn(ServiceState.ACTIVE);
        DhcpLeaseReader leaseReader = mock(DhcpLeaseReader.class);
        given(leaseReader.read()).willReturn(activeSnapshot(3));   // ends=MAX 라 now 무관하게 3건

        DhcpdAssetArea area = new DhcpdAssetArea(
                providerOf(propsFor(fragment)), mock(DhcpdConfigInspector.class),
                serviceInspector, leaseReader);

        List<AssetContextItem> context = area.context();
        assertThat(context).hasSize(2);

        AssetContextItem service = context.get(0);
        assertThat(service.label()).isEqualTo("dhcpd 서비스");
        assertThat(service.value()).isEqualTo("실행 중");
        assertThat(service.severity()).isEqualTo(ObservationSeverity.OK);
        assertThat(service.severity().badgeClass()).isEqualTo("n-badge-green");

        AssetContextItem leases = context.get(1);
        assertThat(leases.label()).isEqualTo("활성 임대");
        assertThat(leases.value()).isEqualTo("3건");
        assertThat(leases.severity()).isEqualTo(ObservationSeverity.INFO);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private PxeInfraProperties propsFor(Path fragment) {
        return new PxeInfraProperties(
                fragment.toString(),
                tmp.resolve("dhcpd.conf").toString(),
                tmp.resolve("dhcpd.leases").toString());
    }

    /** ends=Instant.MAX 인 활성 임대 n건 스냅샷 — activeCount 가 관측 시각과 무관하게 n 이 되게 한다. */
    private static LeaseSnapshot activeSnapshot(int count) {
        List<LeaseEntry> entries = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new LeaseEntry(
                        IpAddressVO.of("10.0.2." + (10 + i)), null, null, Instant.MAX, LeaseBindingState.ACTIVE))
                .toList();
        return new LeaseSnapshot(entries);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PxeInfraProperties> providerOf(PxeInfraProperties props) {
        ObjectProvider<PxeInfraProperties> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(props);
        return provider;
    }
}
