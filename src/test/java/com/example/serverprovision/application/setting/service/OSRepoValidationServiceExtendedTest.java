package com.example.serverprovision.application.setting.service;

import com.example.serverprovision.application.setting.dto.ValidationWarning;
import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OSRepoValidationService} 커버리지 갭 보강 테스트.
 *
 * <p>기존 {@code OSRepoValidationServiceTest} 가 커버하지 못한 시나리오:
 * <ul>
 *   <li>동일 processList 에 OS_SETTING 이 2개 이상일 때 각기 다른 OSMetadata 인덱스 대조</li>
 *   <li>패키지 목록이 빈 경우 서비스만 검증 — lookupExisting 및 countBy 호출 경로</li>
 *   <li>서비스 목록이 빈 경우 패키지만 검증</li>
 *   <li>{@code findExistingNames} 가 null 을 반환하는 케이스 — lookupExisting 의 null 방어</li>
 *   <li>{@code additionalPackages} 에 null/blank 원소가 섞여 있을 때 해당 원소 skip</li>
 *   <li>{@code ServiceDirective.name} 이 null/blank 인 경우 해당 원소 skip</li>
 * </ul>
 * </p>
 */
class OSRepoValidationServiceExtendedTest {

    private OSPackageRefRepository packageRepo;
    private OSServiceRefRepository serviceRepo;
    private OSRepoValidationService service;

    @BeforeEach
    void setUp() {
        packageRepo = mock(OSPackageRefRepository.class);
        serviceRepo = mock(OSServiceRefRepository.class);
        service = new OSRepoValidationService(packageRepo, serviceRepo);
    }

    private OSMetadataDTO meta(Long id, String version) {
        return new OSMetadataDTO(
                id, OSName.ROCKY_LINUX, version,
                "/mnt/iso/" + id, null, true,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private OSSetting osSetting(OSMetadataDTO m,
                                List<ServiceDirective> services,
                                List<String> packages) {
        RockyLinux9Setting domain = RockyLinux9Setting.builder()
                .selinuxMode("enforcing")
                .services(services)
                .additionalPackages(packages)
                .build();
        return new OSSetting(m, domain);
    }

    @Nested
    @DisplayName("복수 OS_SETTING 처리")
    class MultipleOSSettingsTest {

        @Test
        @DisplayName("서로 다른 OSMetadata 를 대상으로 하는 두 OS_SETTING 은 각기 다른 인덱스와 대조한다")
        void validatesEachAgainstOwnMetadata() {
            // 두 OSMetadata ID 에 대해 서로 다른 인덱스 상태를 설정한다.
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(50L);
            when(packageRepo.countByOsMetadata_Id(2L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(2L)).thenReturn(50L);

            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of("vim"));
            when(serviceRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());
            when(packageRepo.findExistingNames(eq(2L), any())).thenReturn(Set.of("httpd"));
            when(serviceRepo.findExistingNames(eq(2L), any())).thenReturn(Set.of());

            OSSetting first = osSetting(meta(1L, "9.6"),
                    List.of(), List.of("vim", "bad-for-meta1"));
            OSSetting second = osSetting(meta(2L, "9.5"),
                    List.of(), List.of("httpd", "bad-for-meta2"));

            List<ValidationWarning> warnings = service.validate(
                    List.<AbstractSettingProcess>of(first, second));

            // 각 OS 에 대해 미존재 패키지 하나씩 = 총 2개 경고
            assertThat(warnings).hasSize(2);
            assertThat(warnings).extracting(ValidationWarning::field)
                    .containsExactlyInAnyOrder(
                            "processList[0].additionalPackages[1]",
                            "processList[1].additionalPackages[1]");
            assertThat(warnings).extracting(ValidationWarning::value)
                    .containsExactlyInAnyOrder("bad-for-meta1", "bad-for-meta2");
        }

        @Test
        @DisplayName("둘 중 하나의 OS 인덱스가 비어있으면 그 OS 에 대한 검증만 건너뛴다")
        void skipsOnlyForMetadataWithEmptyIndex() {
            // meta 1 은 인덱스 존재, meta 2 는 인덱스 미존재 (0 리턴) — 각기 다르게 동작해야 함.
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(50L);
            when(packageRepo.countByOsMetadata_Id(2L)).thenReturn(0L);
            when(serviceRepo.countByOsMetadata_Id(2L)).thenReturn(0L);

            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());
            when(serviceRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());

            OSSetting first = osSetting(meta(1L, "9.6"),
                    List.of(), List.of("typo"));
            OSSetting second = osSetting(meta(2L, "9.5"),
                    List.of(), List.of("also-typo-but-skipped"));

            List<ValidationWarning> warnings = service.validate(
                    List.<AbstractSettingProcess>of(first, second));

            // meta1 에서만 경고 — meta2 는 인덱싱 미완 상태로 건너뛐
            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).field()).isEqualTo("processList[0].additionalPackages[0]");
            // meta 2 에 대해 findExistingNames 는 호출되지 않아야 한다 (count 가 0 이므로)
            verify(packageRepo, never()).findExistingNames(eq(2L), any());
        }
    }

    @Nested
    @DisplayName("빈 리스트 분기")
    class EmptyListTest {

        @Test
        @DisplayName("패키지는 비어있고 서비스만 있으면 패키지 조회는 호출하지 않는다")
        void emptyPackagesSkipsPackageLookup() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(50L);
            when(serviceRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());

            OSSetting s = osSetting(meta(1L, "9.6"),
                    List.of(new ServiceDirective("unknown-svc", ServiceAction.ENABLE)),
                    List.of()); // 패키지 없음

            List<ValidationWarning> warnings = service.validate(List.<AbstractSettingProcess>of(s));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).field()).isEqualTo("processList[0].services[0].name");
            // 빈 리스트에 대해 findExistingNames 를 호출하면 IN () 가 DB 에러를 일으키므로 호출 금지.
            verify(packageRepo, never()).findExistingNames(eq(1L), any());
        }

        @Test
        @DisplayName("서비스만 비어있으면 서비스 조회는 호출하지 않는다")
        void emptyServicesSkipsServiceLookup() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(50L);
            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());

            OSSetting s = osSetting(meta(1L, "9.6"),
                    List.of(),
                    List.of("typo"));

            List<ValidationWarning> warnings = service.validate(List.<AbstractSettingProcess>of(s));

            assertThat(warnings).hasSize(1);
            verify(serviceRepo, never()).findExistingNames(eq(1L), any());
        }
    }

    @Nested
    @DisplayName("null/blank 값 방어")
    class NullDefenseTest {

        @Test
        @DisplayName("findExistingNames 가 null 을 반환해도 NullPointerException 없이 전체를 미존재로 본다")
        void nullFromFindExistingNames_treatedAsEmpty() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(0L);
            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(null);

            OSSetting s = osSetting(meta(1L, "9.6"),
                    List.of(),
                    List.of("any"));

            // NPE 없이 "any" 는 미존재 취급으로 경고 1개
            List<ValidationWarning> warnings = service.validate(List.<AbstractSettingProcess>of(s));
            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).value()).isEqualTo("any");
        }

        @Test
        @DisplayName("additionalPackages 원소 중 null/blank 는 경고 대상에서 제외된다")
        void nullOrBlankPackage_isSkipped() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(100L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(0L);
            when(packageRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());

            // null, "", "   " 는 경고 대상 아님. "real-typo" 만 경고로 수집.
            OSSetting s = osSetting(meta(1L, "9.6"),
                    List.of(),
                    java.util.Arrays.asList("real-typo", null, "", "   "));

            List<ValidationWarning> warnings = service.validate(List.<AbstractSettingProcess>of(s));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).value()).isEqualTo("real-typo");
        }

        @Test
        @DisplayName("ServiceDirective 의 name 이 blank 면 해당 지시 경고 대상에서 제외된다")
        void blankServiceName_isSkipped() {
            when(packageRepo.countByOsMetadata_Id(1L)).thenReturn(0L);
            when(serviceRepo.countByOsMetadata_Id(1L)).thenReturn(50L);
            when(serviceRepo.findExistingNames(eq(1L), any())).thenReturn(Set.of());

            OSSetting s = osSetting(meta(1L, "9.6"),
                    List.of(
                            new ServiceDirective("real-unknown", ServiceAction.ENABLE),
                            new ServiceDirective("   ",          ServiceAction.ENABLE)
                    ),
                    List.of());

            List<ValidationWarning> warnings = service.validate(List.<AbstractSettingProcess>of(s));

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0).value()).isEqualTo("real-unknown");
        }
    }

    @Nested
    @DisplayName("null/empty 입력 방어")
    class NullInputDefenseTest {

        @Test
        @DisplayName("null 프로세스 리스트는 빈 경고 반환")
        void nullProcessList_returnsEmpty() {
            assertThat(service.validate(null)).isEmpty();
        }

        @Test
        @DisplayName("빈 프로세스 리스트는 빈 경고 반환")
        void emptyProcessList_returnsEmpty() {
            assertThat(service.validate(List.of())).isEmpty();
        }
    }
}
