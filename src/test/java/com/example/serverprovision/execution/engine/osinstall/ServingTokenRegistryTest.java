package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.rhel.RhelInstallTokenRegistry;
import com.example.serverprovision.execution.engine.rhel.RhelServing;
import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** E4-1-b-4-2 — 공통 발급소: 게스트당 토큰 하나 · 재발급하면 옛 토큰은 죽음 · 회수 · 신원 · URL 은 종류 세그먼트에서. */
class ServingTokenRegistryTest {

    private final RhelInstallTokenRegistry registry = new RhelInstallTokenRegistry("http://10.0.0.7:8080/");
    private final UUID guest = UUID.randomUUID();

    @Test
    @DisplayName("발급 · 해석 · 신원 · 지금 내용, URL 은 /api/pxe/v1/rhel/{token}(끝 슬래시 제거)")
    void issueAndResolve() {
        RhelServing serving = mock(RhelServing.class);
        UUID token = registry.issue(guest, serving);

        assertThat(registry.kind()).isEqualTo(GuestCredential.ServingKind.RHEL);
        assertThat(registry.resolve(token)).contains(serving);
        assertThat(registry.guestOf(token)).contains(guest);
        assertThat(registry.currentOf(guest)).contains(serving);
        assertThat(registry.issuedUrlOf(token)).isEqualTo("http://10.0.0.7:8080/api/pxe/v1/rhel/" + token);
        assertThat(registry.treeUrlOf(token)).endsWith(token + "/tree");
        assertThat(registry.kickstartUrlOf(token)).endsWith(token + "/ks.cfg");
    }

    @Test
    @DisplayName("재발급하면 옛 토큰은 그 자리에서 죽는다 · 회수하면 해석 · 신원 · URL 모두 사라진다")
    void reissueAndRevoke() {
        UUID first = registry.issue(guest, mock(RhelServing.class));
        UUID second = registry.issue(guest, mock(RhelServing.class));

        assertThat(registry.resolve(first)).isEmpty();
        assertThat(registry.guestOf(first)).isEmpty();

        registry.revoke(guest);
        assertThat(registry.resolve(second)).isEmpty();
        assertThat(registry.currentOf(guest)).isEmpty();
        assertThatThrownBy(() -> registry.issuedUrlOf(second)).isInstanceOf(IllegalStateException.class);
    }
}
