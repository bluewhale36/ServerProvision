package com.example.serverprovision.application.setting.service;

import com.example.serverprovision.application.setting.dto.ValidationWarning;
import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.BasicSetting;
import com.example.serverprovision.application.setting.model.OSSetting;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.enums.ServiceAction;
import com.example.serverprovision.domain.os.model.setting.RockyLinux9Setting;
import com.example.serverprovision.domain.os.model.setting.ServiceDirective;
import com.example.serverprovision.domain.os.repository.OSPackageRefRepository;
import com.example.serverprovision.domain.os.repository.OSServiceRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OSRepoValidationService} 단위 테스트.
 *
 * <p>ref 테이블 조회를 Mock 으로 대체하고, OS_SETTING 구성별로 경고 수집 동작을 검증한다.</p>
 */
class OSRepoValidationServiceTest {

    private OSPackageRefRepository packageRepo;
    private OSServiceRefRepository serviceRepo;
    private OSRepoValidationService service;

    private OSMetadataDTO rocky96() {
        return new OSMetadataDTO(
                1L, OSName.ROCKY_LINUX, "9.6",
                "/mnt/iso/rocky9", null, true,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    private OSSetting osSetting(List<ServiceDirective> services, List<String> packages) {
        RockyLinux9Setting domain = RockyLinux9Setting.builder()
                .selinuxMode("enforcing")
                .services(services)
                .additionalPackages(packages)
                .build();
        return new OSSetting(rocky96(), domain);
    }

    @BeforeEach
    void setUp() {
        packageRepo = mock(OSPackageRefRepository.class);
        serviceRepo = mock(OSServiceRefRepository.class);
        service = new OSRepoValidationService(packageRepo, serviceRepo);
    }

    @Nested
    @DisplayName("인덱스 미존재 시 검증 건너뜀")
    class IndexNotPopulatedTest {

        @Test
        @DisplayName("패키지 인덱스가 없으면 패키지 검증을 건너뛰어 경고를 만들지 않는다")
        void skipsPackageValidation_whenIndexIsEmpty() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(0L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(0L);

            OSSetting setting = osSetting(
                    List.of(new ServiceDirective("httpd", ServiceAction.ENABLE)),
                    List.of("typo-package"));

            List<ValidationWarning> warnings =
                    service.validate(List.<AbstractSettingProcess>of(setting));

            assertThat(warnings).isEmpty();
        }
    }

    @Nested
    @DisplayName("RHEL OSSetting 경고 수집")
    class RHELWarningsTest {

        @BeforeEach
        void stubCounts() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(50L);
        }

        @Test
        @DisplayName("존재하는 패키지는 경고 없음, 미존재 패키지만 경고를 만든다")
        void onlyUnknownPackagesGenerateWarnings() {
            when(packageRepo.findExistingNames(eq(1L), any()))
                    .thenReturn(Set.of("vim"));
            when(serviceRepo.findExistingNames(eq(1L), any()))
                    .thenReturn(Set.of());

            OSSetting setting = osSetting(List.of(),
                    List.of("vim", "typo-pkg"));

            List<ValidationWarning> warnings =
                    service.validate(List.<AbstractSettingProcess>of(setting));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).field())
                    .isEqualTo("processList[0].additionalPackages[1]");
            assertThat(warnings.get(0).value()).isEqualTo("typo-pkg");
            assertThat(warnings.get(0).message()).contains("기본 저장소");
        }

        @Test
        @DisplayName("존재하는 서비스는 경고 없음, 미존재 서비스만 경고를 만든다")
        void onlyUnknownServicesGenerateWarnings() {
            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());
            when(serviceRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of("sshd"));

            OSSetting setting = osSetting(
                    List.of(
                            new ServiceDirective("sshd", ServiceAction.ENABLE),
                            new ServiceDirective("ngix", ServiceAction.ENABLE)
                    ),
                    List.of());

            List<ValidationWarning> warnings =
                    service.validate(List.<AbstractSettingProcess>of(setting));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).field())
                    .isEqualTo("processList[0].services[1].name");
            assertThat(warnings.get(0).value()).isEqualTo("ngix");
        }

        @Test
        @DisplayName("패키지·서비스 경고가 동시에 발생하면 모두 수집한다")
        void collectsBothPackageAndServiceWarnings() {
            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of("vim"));
            when(serviceRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of("httpd"));

            OSSetting setting = osSetting(
                    List.of(
                            new ServiceDirective("httpd", ServiceAction.ENABLE),
                            new ServiceDirective("ngix", ServiceAction.DISABLE)
                    ),
                    List.of("vim", "typopkg"));

            List<ValidationWarning> warnings =
                    service.validate(List.<AbstractSettingProcess>of(setting));

            assertThat(warnings).hasSize(2);
            assertThat(warnings).extracting(ValidationWarning::value)
                    .containsExactlyInAnyOrder("typopkg", "ngix");
        }
    }

    @Nested
    @DisplayName("OS_SETTING 이 아닌 프로세스는 무시")
    class NonOSSettingProcessTest {

        @Test
        @DisplayName("BASIC_SETTING 은 검증 대상 아님")
        void basicSetting_isIgnored() {
            List<ValidationWarning> warnings =
                    service.validate(List.<AbstractSettingProcess>of(new BasicSetting()));
            assertThat(warnings).isEmpty();
        }

        @Test
        @DisplayName("process 인덱스는 전체 목록 기준으로 유지된다")
        void preservesProcessIndex_withMixedList() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(10L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(10L);
            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());
            when(serviceRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());

            AbstractSettingProcess basic = new BasicSetting();
            OSSetting setting = osSetting(List.of(), List.of("bogus"));

            // 0번: BASIC_SETTING, 1번: OS_SETTING
            List<ValidationWarning> warnings = service.validate(List.of(basic, setting));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).field())
                    .isEqualTo("processList[1].additionalPackages[0]");
        }
    }
}
