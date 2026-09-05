package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.exception.RetainedPasswordUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HF12 결함 B — 리눅스 축의 비밀값 다형 훅 둘(pre-fill 제거 · 수정 저장 병합)이 Windows 축과 같은 계약을 지킨다. */
class LinuxInstallationRequestTest {

    private static RHELInstallationRequest rhel(RootPasswordRequest root, List<UserRequest> users) {
        return new RHELInstallationRequest(1L, 10L, null, null, root, users, 1L, List.of(), false, null);
    }

    private static UbuntuInstallationRequest ubuntu(List<UserRequest> users) {
        return new UbuntuInstallationRequest(2L, 20L, null, null, users, "node1", List.of());
    }

    private static UserRequest user(String name, String password, boolean encrypted, boolean keep) {
        return new UserRequest(name, password, true, encrypted, keep);
    }

    @Test
    @DisplayName("withoutSecrets — root · 사용자 값을 지우고 유지 플래그로(root 없던 저장본은 root null 유지) · Ubuntu 는 사용자만")
    void stripped() {
        RHELInstallationRequest stored = rhel(new RootPasswordRequest("R00t!", true, false),
                List.of(user("ops", "Op5!", false, false)));
        RHELInstallationRequest stripped = (RHELInstallationRequest) stored.withoutSecrets();
        assertThat(stripped.getRootPassword().getPassword()).isNull();
        assertThat(stripped.getRootPassword().isKeepExistingPassword()).isTrue();
        assertThat(stripped.getUsers().get(0).getPassword()).isNull();
        assertThat(stripped.getUsers().get(0).isKeepExistingPassword()).isTrue();
        assertThat(stripped.getUsers().get(0).getUsername()).isEqualTo("ops");

        assertThat(((RHELInstallationRequest) rhel(null, List.of()).withoutSecrets()).getRootPassword()).isNull();
        UbuntuInstallationRequest u = (UbuntuInstallationRequest) ubuntu(List.of(user("ubuntu", "Ub!", false, false))).withoutSecrets();
        assertThat(u.getUsers().get(0).getPassword()).isNull();
        assertThat(u.getUsers().get(0).isKeepExistingPassword()).isTrue();
        assertThat(u.getHostname()).isEqualTo("node1");
    }

    @Test
    @DisplayName("withSecretsRetainedFrom — root · 사용자(username 매칭) 유지는 저장본 값 · 암호화 여부를 복사(keep 해제) · 유지 아님은 그대로")
    void retained() {
        RHELInstallationRequest stored = rhel(new RootPasswordRequest("R00t!", true, false),
                List.of(user("ops", "Op5!", false, false), user("dev", "D3v!", true, false)));
        RHELInstallationRequest request = rhel(new RootPasswordRequest(null, false, true),
                List.of(user("dev", null, false, true), user("ops", "N3w!", false, false), user("new", "Nw!", false, false)));

        RHELInstallationRequest merged = (RHELInstallationRequest) request.withSecretsRetainedFrom(stored);
        assertThat(merged.getRootPassword().getPassword()).isEqualTo("R00t!");
        assertThat(merged.getRootPassword().isPasswordEncrypted()).isTrue();
        assertThat(merged.getRootPassword().isKeepExistingPassword()).isFalse();
        assertThat(merged.getUsers()).extracting(UserRequest::getUsername).containsExactly("dev", "ops", "new");
        assertThat(merged.getUsers().get(0).getPassword()).isEqualTo("D3v!");     // 순서가 달라도 이름으로 찾는다
        assertThat(merged.getUsers().get(0).isPasswordEncrypted()).isTrue();
        assertThat(merged.getUsers().get(0).isKeepExistingPassword()).isFalse();
        assertThat(merged.getUsers().get(1).getPassword()).isEqualTo("N3w!");     // 새 값은 그대로
        assertThat(merged.getUsers().get(2).getPassword()).isEqualTo("Nw!");

        RHELInstallationRequest fresh = rhel(new RootPasswordRequest("X!", false, false), List.of(user("ops", "Y!", false, false)));
        RHELInstallationRequest same = (RHELInstallationRequest) fresh.withSecretsRetainedFrom(stored);
        assertThat(same.getRootPassword().getPassword()).isEqualTo("X!");
        assertThat(same.getUsers().get(0).getPassword()).isEqualTo("Y!");
    }

    @Test
    @DisplayName("Ubuntu — 사용자 유지는 저장본에서 복사 · 저장본이 RHEL 이어도(계열 변경) 같은 username 이면 이어받는다")
    void retainedUbuntu() {
        UbuntuInstallationRequest stored = ubuntu(List.of(user("ubuntu", "Ub!", false, false)));
        UbuntuInstallationRequest merged = (UbuntuInstallationRequest) ubuntu(List.of(user("ubuntu", null, false, true)))
                .withSecretsRetainedFrom(stored);
        assertThat(merged.getUsers().get(0).getPassword()).isEqualTo("Ub!");
        assertThat(merged.getUsers().get(0).isKeepExistingPassword()).isFalse();

        RHELInstallationRequest storedRhel = rhel(null, List.of(user("ubuntu", "Rh!", false, false)));
        assertThat(((UbuntuInstallationRequest) ubuntu(List.of(user("ubuntu", null, false, true))).withSecretsRetainedFrom(storedRhel))
                .getUsers().get(0).getPassword()).isEqualTo("Rh!");
    }

    @Test
    @DisplayName("유지할 값이 없으면 RetainedPasswordUnavailableException — root 는 rootPassword, 사용자는 users 직결 · 저장본 없음 · 다른 타입 · 이름 불일치 · 값 없는 저장본")
    void retainedUnavailable() {
        RHELInstallationRequest rootKeep = rhel(new RootPasswordRequest(null, false, true), List.of());
        assertThatThrownBy(() -> rootKeep.withSecretsRetainedFrom(null))
                .isInstanceOf(RetainedPasswordUnavailableException.class)
                .satisfies(e -> assertThat(((RetainedPasswordUnavailableException) e).fieldName()).isEqualTo("rootPassword"))
                .hasMessageContaining("root");
        assertThatThrownBy(() -> rootKeep.withSecretsRetainedFrom(new BasicSettingRequest(List.of())))
                .isInstanceOf(RetainedPasswordUnavailableException.class);
        // Ubuntu 저장본에는 root 가 없다 — 계열이 바뀐 수정에서 root 유지는 성립하지 않는다.
        assertThatThrownBy(() -> rootKeep.withSecretsRetainedFrom(ubuntu(List.of(user("u", "p", false, false)))))
                .isInstanceOf(RetainedPasswordUnavailableException.class);
        // 저장본 root 가 값 없이 저장된 구 저장본.
        assertThatThrownBy(() -> rootKeep.withSecretsRetainedFrom(rhel(new RootPasswordRequest(null, false, true), List.of())))
                .isInstanceOf(RetainedPasswordUnavailableException.class);

        RHELInstallationRequest userKeep = rhel(null, List.of(user("ops", null, false, true)));
        RHELInstallationRequest storedOther = rhel(null, List.of(user("dev", "D!", false, false)));
        assertThatThrownBy(() -> userKeep.withSecretsRetainedFrom(storedOther))
                .isInstanceOf(RetainedPasswordUnavailableException.class)
                .satisfies(e -> assertThat(((RetainedPasswordUnavailableException) e).fieldName()).isEqualTo("users"))
                .hasMessageContaining("ops");
        assertThatThrownBy(() -> userKeep.withSecretsRetainedFrom(null)).isInstanceOf(RetainedPasswordUnavailableException.class);
    }
}
