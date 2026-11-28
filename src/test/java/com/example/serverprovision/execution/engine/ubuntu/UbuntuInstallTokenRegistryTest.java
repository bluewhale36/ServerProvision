package com.example.serverprovision.execution.engine.ubuntu;

import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ubuntu 서빙 URL: 종류 세그먼트 ubuntu, 트리 접두와 .iso 로 끝나는 ISO 주소. */
class UbuntuInstallTokenRegistryTest {

    private final UbuntuInstallTokenRegistry registry = new UbuntuInstallTokenRegistry("http://pxe.example:8080/");
    private final UUID token = UUID.fromString("00000000-0000-0000-0000-000000000031");

    @Test
    @DisplayName("트리 URL 과 ISO URL. ISO 는 casper 가 받도록 .iso 로 끝난다")
    void urls() {
        assertThat(registry.kind()).isEqualTo(GuestCredential.ServingKind.UBUNTU);
        assertThat(registry.treeUrlOf(token)).isEqualTo("http://pxe.example:8080/api/pxe/v1/ubuntu/" + token + "/tree");
        assertThat(registry.isoUrlOf(token)).isEqualTo("http://pxe.example:8080/api/pxe/v1/ubuntu/" + token + "/install.iso");
        assertThat(registry.isoUrlOf(token)).endsWith(".iso");
    }

    @Test
    @DisplayName("ubuntu 세그먼트는 게스트 체인의 서빙 종류로 읽힌다")
    void segmentRecognized() {
        assertThat(GuestCredential.ServingKind.ofSegment("ubuntu")).contains(GuestCredential.ServingKind.UBUNTU);
    }
}
