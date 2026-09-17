package com.example.serverprovision.execution.pxeinfra.spi;

import com.example.serverprovision.execution.asset.spi.AreaAvailability;
import com.example.serverprovision.execution.asset.spi.AssetContextItem;
import com.example.serverprovision.execution.asset.spi.AssetSlotStatus;
import com.example.serverprovision.execution.asset.spi.ObservationSeverity;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.execution.asset.spi.SystemAssetSlot;
import com.example.serverprovision.execution.asset.exception.SystemAssetSealNotSupportedException;
import com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures;
import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.inspect.DhcpLeaseReader;
import com.example.serverprovision.execution.pxeinfra.inspect.DnsmasqConfigInspector;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseEntry;
import com.example.serverprovision.execution.pxeinfra.inspect.LeaseSnapshot;
import com.example.serverprovision.execution.pxeinfra.inspect.SystemServiceInspector;
import com.example.serverprovision.execution.pxeinfra.service.PxeNetworkConfigService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * DHCP(dnsmasq) 자산 영역(Service Provider Interface 확장점) 검증(R16) — 미구성이면 슬롯을 NOT_CONFIGURED 로 판정하고
 * 관측 chip 을 비워 명령을 아예 spawn 하지 않으며(게이팅), 구성이면 조각 슬롯을 판정하고 서비스 · 모드 · 임대 chip 을
 * severity 매핑과 함께 조립한다. proxyDHCP 는 임대 chip 을 내지 않는다. 봉인은 미지원(default throw)임을 확인한다.
 */
class DhcpAssetAreaTest {

    @TempDir
    Path tmp;

    // ── 미구성 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("미구성 — 슬롯 NOT_CONFIGURED + context 빈 목록, 관측 협력자 전혀 호출 안 함(명령 미실행)")
    void unconfigured_absorbedWithoutSpawningCommands() {
        DnsmasqConfigInspector configInspector = mock(DnsmasqConfigInspector.class);
        SystemServiceInspector serviceInspector = mock(SystemServiceInspector.class);
        DhcpLeaseReader leaseReader = mock(DhcpLeaseReader.class);
        PxeNetworkConfigService configService = mock(PxeNetworkConfigService.class);
        DhcpAssetArea area = new DhcpAssetArea(
                providerOf(null), configInspector, serviceInspector, leaseReader, configService);

        assertThat(area.areaKey()).isEqualTo(SystemAssetAreaKey.DHCP);
        assertThat(area.displayName()).isEqualTo("PXE 인프라 (dnsmasq)");
        assertThat(area.availability()).isEqualTo(AreaAvailability.NOT_CONFIGURED);

        SystemAssetSlot slot = area.slots().get(0);
        assertThat(slot.slotKey()).isEqualTo("FRAGMENT");
        assertThat(slot.replaceable()).isFalse();

        AssetSlotStatus status = area.inspect(slot);
        assertThat(status.present()).isFalse();
        assertThat(status.condition()).isEqualTo(ConfigFileCondition.NOT_CONFIGURED);

        assertThat(area.context()).isEmpty();
        verifyNoInteractions(configInspector, serviceInspector, leaseReader, configService);
    }

    @Test
    @DisplayName("봉인 미지원 — supportsSeal false, 기본 seal() 은 SealNotSupported 로 거절")
    void sealUnsupported() {
        DhcpAssetArea area = new DhcpAssetArea(
                providerOf(null), mock(DnsmasqConfigInspector.class),
                mock(SystemServiceInspector.class), mock(DhcpLeaseReader.class), mock(PxeNetworkConfigService.class));

        assertThat(area.supportsSeal()).isFalse();
        assertThatThrownBy(area::seal).isInstanceOf(SystemAssetSealNotSupportedException.class);
    }

    // ── 구성 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("구성 + 조각 존재 — 슬롯 present(크기 · 판정), 파일명은 조각 파일명")
    void configured_fragmentPresent_slotJudged() throws IOException {
        Path fragment = tmp.resolve("serverprovision-pxe.conf");
        Files.writeString(fragment, "port=0\n");
        long size = Files.size(fragment);

        DnsmasqConfigInspector configInspector = mock(DnsmasqConfigInspector.class);
        given(configInspector.inspect()).willReturn(ConfigFileCondition.SYNTAX_OK);
        DhcpAssetArea area = new DhcpAssetArea(
                providerOf(propsFor(fragment)), configInspector,
                mock(SystemServiceInspector.class), mock(DhcpLeaseReader.class), mock(PxeNetworkConfigService.class));

        assertThat(area.availability()).isEqualTo(AreaAvailability.CONFIGURED);
        SystemAssetSlot slot = area.slots().get(0);
        assertThat(slot.filename()).isEqualTo("serverprovision-pxe.conf");

        AssetSlotStatus status = area.inspect(slot);
        assertThat(status.present()).isTrue();
        assertThat(status.sizeBytes()).isEqualTo(size);
        assertThat(status.condition()).isEqualTo(ConfigFileCondition.SYNTAX_OK);
    }

    @Test
    @DisplayName("구성 + 조각 부재 — 슬롯 notPresent, 판정은 config 검사기의 ABSENT")
    void configured_fragmentAbsent_slotAbsent() {
        Path fragment = tmp.resolve("missing-fragment.conf");   // 생성하지 않는다

        DnsmasqConfigInspector configInspector = mock(DnsmasqConfigInspector.class);
        given(configInspector.inspect()).willReturn(ConfigFileCondition.ABSENT);
        DhcpAssetArea area = new DhcpAssetArea(
                providerOf(propsFor(fragment)), configInspector,
                mock(SystemServiceInspector.class), mock(DhcpLeaseReader.class), mock(PxeNetworkConfigService.class));

        AssetSlotStatus status = area.inspect(area.slots().get(0));
        assertThat(status.present()).isFalse();
        assertThat(status.condition()).isEqualTo(ConfigFileCondition.ABSENT);
    }

    @Test
    @DisplayName("구성 · 자체 DHCP — context chip 3개(서비스 · 모드 · 임대) + severity 매핑(ACTIVE→OK, 모드 · 임대→INFO)")
    void configured_authoritative_contextChips() {
        SystemServiceInspector serviceInspector = mock(SystemServiceInspector.class);
        given(serviceInspector.status()).willReturn(ServiceState.ACTIVE);
        DhcpLeaseReader leaseReader = mock(DhcpLeaseReader.class);
        given(leaseReader.read()).willReturn(activeSnapshot(3));   // ends=MAX 라 now 무관하게 3건
        PxeNetworkConfigService configService = mock(PxeNetworkConfigService.class);
        given(configService.load()).willReturn(Optional.of(PxeNetworkConfigFixtures.full()));

        DhcpAssetArea area = new DhcpAssetArea(
                providerOf(propsFor(tmp.resolve("f.conf"))), mock(DnsmasqConfigInspector.class),
                serviceInspector, leaseReader, configService);

        List<AssetContextItem> context = area.context();
        assertThat(context).hasSize(3);
        assertThat(context.get(0).label()).isEqualTo("dnsmasq 서비스");
        assertThat(context.get(0).value()).isEqualTo("실행 중");
        assertThat(context.get(0).severity()).isEqualTo(ObservationSeverity.OK);
        assertThat(context.get(0).severity().badgeClass()).isEqualTo("n-badge-green");
        assertThat(context.get(1).label()).isEqualTo("모드");
        assertThat(context.get(1).value()).isEqualTo("자체 DHCP");
        assertThat(context.get(2).label()).isEqualTo("활성 임대");
        assertThat(context.get(2).value()).isEqualTo("3건");
        assertThat(context.get(2).severity()).isEqualTo(ObservationSeverity.INFO);
    }

    @Test
    @DisplayName("구성 · proxyDHCP — 임대 chip 없음(IP 를 주지 않는다) · 임대 파일도 읽지 않는다")
    void configured_proxy_noLeaseChip() {
        SystemServiceInspector serviceInspector = mock(SystemServiceInspector.class);
        given(serviceInspector.status()).willReturn(ServiceState.INACTIVE);
        DhcpLeaseReader leaseReader = mock(DhcpLeaseReader.class);
        PxeNetworkConfigService configService = mock(PxeNetworkConfigService.class);
        given(configService.load()).willReturn(Optional.of(PxeNetworkConfigFixtures.proxy()));

        DhcpAssetArea area = new DhcpAssetArea(
                providerOf(propsFor(tmp.resolve("f.conf"))), mock(DnsmasqConfigInspector.class),
                serviceInspector, leaseReader, configService);

        List<AssetContextItem> context = area.context();
        assertThat(context).extracting(AssetContextItem::label).containsExactly("dnsmasq 서비스", "모드");
        assertThat(context.get(0).severity()).isEqualTo(ObservationSeverity.WARN);
        assertThat(context.get(1).value()).isEqualTo("proxyDHCP");
        verify(leaseReader, never()).read();
    }

    @Test
    @DisplayName("구성 · 저장 전 — 모드 chip 은 '미저장' · 임대 chip 은 낸다(자체 DHCP 기본 관측)")
    void configured_unsaved_modeChip() {
        SystemServiceInspector serviceInspector = mock(SystemServiceInspector.class);
        given(serviceInspector.status()).willReturn(ServiceState.UNKNOWN);
        DhcpLeaseReader leaseReader = mock(DhcpLeaseReader.class);
        given(leaseReader.read()).willReturn(LeaseSnapshot.empty());
        PxeNetworkConfigService configService = mock(PxeNetworkConfigService.class);
        given(configService.load()).willReturn(Optional.empty());

        DhcpAssetArea area = new DhcpAssetArea(
                providerOf(propsFor(tmp.resolve("f.conf"))), mock(DnsmasqConfigInspector.class),
                serviceInspector, leaseReader, configService);

        List<AssetContextItem> context = area.context();
        assertThat(context).extracting(AssetContextItem::value).containsExactly("알 수 없음", "미저장", "0건");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private PxeInfraProperties propsFor(Path fragment) {
        return new PxeInfraProperties(
                fragment.toString(),
                tmp.resolve("dnsmasq.conf").toString(),
                tmp.resolve("dnsmasq.leases").toString());
    }

    /** ends=Instant.MAX 인 활성 임대 n건 스냅샷 — activeCount 가 관측 시각과 무관하게 n 이 되게 한다. */
    private static LeaseSnapshot activeSnapshot(int count) {
        List<LeaseEntry> entries = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new LeaseEntry(
                        IpAddressVO.of("10.0.2." + (10 + i)), null, Instant.MAX, LeaseBindingState.ACTIVE))
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
