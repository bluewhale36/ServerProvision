package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-2 D-5 — 펌웨어 파일을 BMC 에게 내주는 일회용 URL. <b>일회용이라는 말이 지켜지는지</b>가 요점이다
 * (CP5 F-3 — 굽기가 끝난 뒤에도 URL 이 살아 있었다).
 */
class FirmwareImageTokenRegistryTest {

    private static final UUID GUEST = UUID.randomUUID();
    private final FirmwareImageTokenRegistry registry = new FirmwareImageTokenRegistry("http://server:7798/");

    @Test
    @DisplayName("발급한 토큰으로 파일을 찾고, URL 은 게스트 자산과 같은 원천을 쓴다")
    void issueAndResolve() {
        UUID token = registry.issue(GUEST, FirmwareAxis.BIOS, Path.of("/opt/fw/image.RBU"));

        assertThat(registry.resolve(token)).contains(Path.of("/opt/fw/image.RBU"));
        assertThat(registry.urlFor(token)).isEqualTo("http://server:7798/api/pxe/v1/firmware/" + token);
    }

    @Test
    @DisplayName("굽기가 끝나면 회수한다 — URL 은 로그에 평문으로 남으므로 필요가 끝나면 죽어야 한다")
    void revokeClosesTheDoor() {
        UUID token = registry.issue(GUEST, FirmwareAxis.BIOS, Path.of("/opt/fw/image.RBU"));

        registry.revoke(GUEST, FirmwareAxis.BIOS);

        assertThat(registry.resolve(token)).isEmpty();
    }

    @Test
    @DisplayName("같은 축을 다시 구우면 앞의 토큰은 그 자리에서 죽는다(재시도가 옛 URL 을 살려 두지 않게)")
    void reissueKillsPrevious() {
        UUID first = registry.issue(GUEST, FirmwareAxis.BIOS, Path.of("/opt/fw/old.RBU"));
        UUID second = registry.issue(GUEST, FirmwareAxis.BIOS, Path.of("/opt/fw/new.RBU"));

        assertThat(registry.resolve(first)).isEmpty();
        assertThat(registry.resolve(second)).contains(Path.of("/opt/fw/new.RBU"));
    }

    @Test
    @DisplayName("축이 다르면 서로의 토큰을 건드리지 않는다 — 두 축은 따로 굽고 따로 끝난다")
    void axesAreIndependent() {
        UUID bios = registry.issue(GUEST, FirmwareAxis.BIOS, Path.of("/opt/fw/bios.RBU"));
        UUID bmc = registry.issue(GUEST, FirmwareAxis.BMC, Path.of("/opt/fw/bmc.ima_enc"));

        registry.revoke(GUEST, FirmwareAxis.BIOS);

        assertThat(registry.resolve(bios)).isEmpty();
        assertThat(registry.resolve(bmc)).contains(Path.of("/opt/fw/bmc.ima_enc"));
    }
}
