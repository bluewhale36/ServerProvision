package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.provisioning.setting.enums.OSFamily;
import com.example.serverprovision.provisioning.setting.exception.RetainedPasswordUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-a-2 CP4 — Windows 설치 요청의 비밀값 다형 훅 둘(pre-fill 제거 · 수정 저장 병합). */
class WindowsInstallationRequestTest {

    private static final WindowsImageName IMAGE = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");

    private static WindowsInstallationRequest windows(String password, boolean keep) {
        return new WindowsInstallationRequest(2L, 60L, IMAGE, new WindowsAdministratorPasswordRequest(password, keep));
    }

    @Test
    @DisplayName("계열 = WINDOWS · withoutSecrets 는 비밀번호 값을 빼고 유지 플래그로(값 없던 저장본은 그대로)")
    void withoutSecrets() {
        WindowsInstallationRequest request = windows("S3rver!2025", false);
        assertThat(request.osFamily()).isEqualTo(OSFamily.WINDOWS);
        assertThat(request.hasAdministratorPassword()).isTrue();

        WindowsInstallationRequest stripped = request.withoutSecrets();
        assertThat(stripped.getAdministratorPassword().getPassword()).isNull();
        assertThat(stripped.getAdministratorPassword().isKeepExistingPassword()).isTrue();
        assertThat(stripped.getImageName()).isEqualTo(IMAGE);

        WindowsInstallationRequest legacy = new WindowsInstallationRequest(2L, 60L, null, null);
        assertThat(legacy.withoutSecrets()).isSameAs(legacy);
        assertThat(legacy.hasAdministratorPassword()).isFalse();
    }

    @Test
    @DisplayName("withSecretsRetainedFrom — 유지면 저장본 값 복사(keep 해제), 유지 아니면 그대로")
    void retained() {
        WindowsInstallationRequest existing = windows("Old!2025", false);

        WindowsInstallationRequest merged = windows(null, true).withSecretsRetainedFrom(existing);
        assertThat(merged.getAdministratorPassword().getPassword()).isEqualTo("Old!2025");
        assertThat(merged.getAdministratorPassword().isKeepExistingPassword()).isFalse();

        WindowsInstallationRequest fresh = windows("New!2025", false);
        assertThat(fresh.withSecretsRetainedFrom(existing)).isSameAs(fresh);
    }

    @Test
    @DisplayName("유지할 값이 없으면(다른 타입 · 구 저장본 · 없음) RetainedPasswordUnavailableException(administratorPassword)")
    void retainedUnavailable() {
        WindowsInstallationRequest keep = windows(null, true);
        assertThatThrownBy(() -> keep.withSecretsRetainedFrom(null)).isInstanceOf(RetainedPasswordUnavailableException.class)
                .satisfies(e -> assertThat(((RetainedPasswordUnavailableException) e).fieldName()).isEqualTo("administratorPassword"));
        assertThatThrownBy(() -> keep.withSecretsRetainedFrom(new BasicSettingRequest(List.of())))
                .isInstanceOf(RetainedPasswordUnavailableException.class);
        assertThatThrownBy(() -> keep.withSecretsRetainedFrom(new WindowsInstallationRequest(2L, 60L, IMAGE, null)))
                .isInstanceOf(RetainedPasswordUnavailableException.class);
    }
}
