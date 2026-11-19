package com.example.serverprovision.provisioning.setting.vo;

import com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest;
import com.example.serverprovision.provisioning.setting.dto.request.UserRequest;
import com.example.serverprovision.provisioning.setting.service.LinuxPasswordHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-2 D-18 — 평문 · 해시 타입, SHA-512 crypt, 옛 저장본 해석, 해시 사본. */
class LinuxPasswordTest {

    private final LinuxPasswordHasher hasher = new LinuxPasswordHasher();

    @Test
    @DisplayName("해시는 $6$ 형식 · 같은 평문도 salt 가 달라 매번 다르다 · 검증은 해시 안 salt 로 다시 계산")
    void hasher() {
        String a = hasher.apply("R00t!pw");
        String b = hasher.apply("R00t!pw");
        assertThat(a).startsWith("$6$");
        assertThat(a).isNotEqualTo(b);
        assertThat(LinuxPasswordHasher.matches("R00t!pw", a)).isTrue();
        assertThat(LinuxPasswordHasher.matches("wrong", a)).isFalse();
    }

    @Test
    @DisplayName("toCrypt — 평문은 해시하고 해시는 그대로(kickstart 에 평문이 나가지 않는다)")
    void toCrypt() {
        assertThat(new LinuxPassword.Plain("pw").toCrypt(hasher)).startsWith("$6$");
        assertThat(new LinuxPassword.Hashed("$6$x$y").toCrypt(hasher)).isEqualTo("$6$x$y");
        assertThat(new LinuxPassword.Plain("secret").toString()).doesNotContain("secret");
    }

    @Test
    @DisplayName("root — 평문 입력은 해시 사본이 되고, 옛 저장본 isPasswordEncrypted=true 는 해시로 읽힌다")
    void rootPassword() {
        RootPasswordRequest plain = new RootPasswordRequest("R00t!", null, null, false);
        assertThat(plain.credential()).containsInstanceOf(LinuxPassword.Plain.class);
        assertThat(plain.carriesHash()).isFalse();
        RootPasswordRequest hashed = plain.hashed(hasher);
        assertThat(hashed.getPassword()).isNull();
        assertThat(LinuxPasswordHasher.matches("R00t!", hashed.getPasswordHash())).isTrue();
        assertThat(hashed.hashed(hasher).getPasswordHash()).isEqualTo(hashed.getPasswordHash());   // 두 번 해시하지 않는다

        RootPasswordRequest legacy = new RootPasswordRequest("$6$old$hash", null, true, false);
        assertThat(legacy.getPassword()).isNull();
        assertThat(legacy.getPasswordHash()).isEqualTo("$6$old$hash");
        assertThat(legacy.carriesHash()).isTrue();

        assertThat(RootPasswordRequest.keepExisting().hasPassword()).isFalse();
        assertThat(RootPasswordRequest.keepExisting().isKeepExistingPassword()).isTrue();
    }

    @Test
    @DisplayName("사용자 — 해시 사본 · 비밀번호 뺀 사본(기존 유지 표시) · 이름과 sudo 는 그대로")
    void userPassword() {
        UserRequest user = new UserRequest("ops", "Op5!", null, true, null, false);
        UserRequest hashed = user.hashed(hasher);
        assertThat(hashed.getUsername()).isEqualTo("ops");
        assertThat(hashed.getIsSudoer()).isTrue();
        assertThat(hashed.getPassword()).isNull();
        assertThat(LinuxPasswordHasher.matches("Op5!", hashed.getPasswordHash())).isTrue();

        UserRequest stripped = hashed.withoutPassword();
        assertThat(stripped.hasPassword()).isFalse();
        assertThat(stripped.isKeepExistingPassword()).isTrue();

        UserRequest noPassword = new UserRequest("guest", null, null, false, null, false);
        assertThat(noPassword.hashed(hasher)).isSameAs(noPassword);
    }
}
