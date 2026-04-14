package com.example.serverprovision.application.setting.converter;

import com.example.serverprovision.application.setting.model.OSInstallation;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.domain.os.dto.OSEnvironmentDTO;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.installation.CentOS7Installation;
import com.example.serverprovision.domain.os.model.installation.Environment;
import com.example.serverprovision.domain.os.model.installation.Partition;
import com.example.serverprovision.domain.os.model.installation.RockyLinux10Installation;
import com.example.serverprovision.domain.os.model.installation.RockyLinux8Installation;
import com.example.serverprovision.domain.os.model.installation.RockyLinux9Installation;
import com.example.serverprovision.domain.os.model.installation.RootPassword;
import com.example.serverprovision.domain.os.model.installation.Timezone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SettingProcessConverter} 의 레거시 {@code "osType":"ROCKY_LINUX"} 판별자 마이그레이션 동작을 검증한다.
 *
 * <p>테스트 전략: 현재 직렬화 포맷으로 정상 오브젝트를 만든 뒤, 판별자(+ 필요한 경우 버전) 를
 * 레거시 값으로 다운그레이드한 JSON 을 컨버터에 넘겨 역직렬화 결과가
 * 올바른 메이저 버전별 도메인 클래스로 승격되는지 확인한다.</p>
 */
class SettingProcessConverterMigrationTest {

    private ObjectMapper objectMapper;
    private SettingProcessConverter converter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        converter = new SettingProcessConverter(objectMapper);
    }

    // --- Helper: 메이저 버전별 Rocky/CentOS SettingProcess 직렬화 JSON 을 만든다 --------------------

    private String serializeRocky9ProcessJson() {
        OSMetadataDTO metadata = OSMetadataDTO.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5").isEnabled(true).build();
        RockyLinux9Installation installation = RockyLinux9Installation.builder()
                .partitions(defaultPartitionsWithBootEfi())
                .users(List.of())
                .rootPassword(defaultRootPassword())
                .installVersion("9.5")
                .environment(defaultEnvironment())
                .timezone(defaultTimezone())
                .isKDumpEnabled(true)
                .build();
        return serialize(new OSInstallation(metadata, installation));
    }

    private String serializeRocky8ProcessJson() {
        OSMetadataDTO metadata = OSMetadataDTO.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("8.10").isEnabled(true).build();
        RockyLinux8Installation installation = RockyLinux8Installation.builder()
                .partitions(defaultPartitionsWithBootEfi())
                .users(List.of())
                .rootPassword(defaultRootPassword())
                .installVersion("8.10")
                .environment(defaultEnvironment())
                .timezone(defaultTimezone())
                .isKDumpEnabled(true)
                .build();
        return serialize(new OSInstallation(metadata, installation));
    }

    private String serializeRocky10ProcessJson() {
        OSMetadataDTO metadata = OSMetadataDTO.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("10.0").isEnabled(true).build();
        RockyLinux10Installation installation = RockyLinux10Installation.builder()
                .partitions(defaultPartitionsWithBootEfi())
                .users(List.of())
                .rootPassword(defaultRootPassword())
                .installVersion("10.0")
                .environment(defaultEnvironment())
                .timezone(defaultTimezone())
                .isKDumpEnabled(true)
                .allowSshRoot(false)
                .build();
        return serialize(new OSInstallation(metadata, installation));
    }

    private String serializeCentOS7ProcessJson() {
        OSMetadataDTO metadata = OSMetadataDTO.builder()
                .id(1L).osName(OSName.CENTOS).osVersion("7.9").isEnabled(true).build();
        // CentOS 7 은 /boot/efi 불필요 — 그래도 요청 JSON 에 포함해도 무방
        CentOS7Installation installation = CentOS7Installation.builder()
                .partitions(defaultPartitionsWithoutBootEfi())
                .users(List.of())
                .rootPassword(defaultRootPassword())
                .installVersion("7.9")
                .environment(defaultEnvironment())
                .timezone(defaultTimezone())
                .isKDumpEnabled(true)
                .build();
        return serialize(new OSInstallation(metadata, installation));
    }

    private String serialize(OSInstallation osInstallation) {
        SettingProcess process = new SettingProcess(List.of(osInstallation));
        return converter.convertToDatabaseColumn(process);
    }

    // --- Helper: 공통 설치 구성 요소 -----------------------------------------------------------

    private List<Partition> defaultPartitionsWithBootEfi() {
        return List.of(
                Partition.builder().mountPoint("/").fileSystem(FileSystem.EXT4).sizeInMB(0).isGrow(true).build(),
                Partition.builder().mountPoint("/boot").fileSystem(FileSystem.EXT4).sizeInMB(1024).isGrow(false).build(),
                Partition.builder().mountPoint("/boot/efi").fileSystem(FileSystem.EFI).sizeInMB(1024).isGrow(false).build(),
                Partition.builder().mountPoint("swap").fileSystem(FileSystem.SWAP).sizeInMB(8192).isGrow(false).build()
        );
    }

    private List<Partition> defaultPartitionsWithoutBootEfi() {
        return List.of(
                Partition.builder().mountPoint("/").fileSystem(FileSystem.EXT4).sizeInMB(0).isGrow(true).build(),
                Partition.builder().mountPoint("/boot").fileSystem(FileSystem.EXT4).sizeInMB(1024).isGrow(false).build(),
                Partition.builder().mountPoint("swap").fileSystem(FileSystem.SWAP).sizeInMB(8192).isGrow(false).build()
        );
    }

    private RootPassword defaultRootPassword() {
        return RootPassword.builder().password("TestPass1!").isPasswordEncrypted(false).build();
    }

    private Environment defaultEnvironment() {
        OSEnvironmentDTO osEnvDto = new OSEnvironmentDTO(
                1L, null, "server-product-environment", "Server", "Server env", false
        );
        // Environment 의 생성자는 package-private — 패키지 경계를 넘는 테스트는 @Builder 경유
        return Environment.builder()
                .osEnvironment(osEnvDto)
                .packageGroups(List.of())
                .build();
    }

    private Timezone defaultTimezone() {
        return Timezone.builder().timezone("Asia/Seoul").isUTC(true).build();
    }

    // --- 레거시 다운그레이드 유틸 ---------------------------------------------------------------

    /**
     * 현재 포맷의 JSON 을 레거시 포맷으로 다운그레이드한다.
     *
     * @param modernJson 메이저 버전 판별자로 직렬화된 현재 JSON
     * @param majorDiscriminator {@code ROCKY_LINUX_8} / {@code ROCKY_LINUX_9} / {@code ROCKY_LINUX_10}
     */
    private String downgradeToLegacyRockyDiscriminator(String modernJson, String majorDiscriminator) {
        return modernJson.replace(
                "\"osType\":\"" + majorDiscriminator + "\"",
                "\"osType\":\"ROCKY_LINUX\""
        );
    }

    // ================================================================================================
    // 실제 테스트
    // ================================================================================================

    @Nested
    @DisplayName("레거시 ROCKY_LINUX 판별자 승격")
    class LegacyRockyDiscriminatorPromotion {

        @Test
        @DisplayName("osVersion 이 9.x 이면 ROCKY_LINUX_9 로 승격되어 RockyLinux9Installation 으로 역직렬화된다")
        void promotesRocky9() {
            String modern = serializeRocky9ProcessJson();
            String legacy = downgradeToLegacyRockyDiscriminator(modern, "ROCKY_LINUX_9");
            // 다운그레이드 결과물에 레거시 판별자가 포함되어야 함 (전제 확인)
            assertThat(legacy).contains("\"osType\":\"ROCKY_LINUX\"");

            SettingProcess restored = converter.convertToEntityAttribute(legacy);

            OSInstallation osInstallation = (OSInstallation) restored.processList().get(0);
            assertThat(osInstallation.getOsInstallation()).isInstanceOf(RockyLinux9Installation.class);
        }

        @Test
        @DisplayName("osVersion 이 8.x 이면 ROCKY_LINUX_8 로 승격되어 RockyLinux8Installation 으로 역직렬화된다")
        void promotesRocky8() {
            String modern = serializeRocky8ProcessJson();
            String legacy = downgradeToLegacyRockyDiscriminator(modern, "ROCKY_LINUX_8");

            SettingProcess restored = converter.convertToEntityAttribute(legacy);

            OSInstallation osInstallation = (OSInstallation) restored.processList().get(0);
            assertThat(osInstallation.getOsInstallation()).isInstanceOf(RockyLinux8Installation.class);
        }

        @Test
        @DisplayName("osVersion 이 10.x 이면 ROCKY_LINUX_10 으로 승격되어 RockyLinux10Installation 으로 역직렬화된다")
        void promotesRocky10() {
            String modern = serializeRocky10ProcessJson();
            String legacy = downgradeToLegacyRockyDiscriminator(modern, "ROCKY_LINUX_10");

            SettingProcess restored = converter.convertToEntityAttribute(legacy);

            OSInstallation osInstallation = (OSInstallation) restored.processList().get(0);
            assertThat(osInstallation.getOsInstallation()).isInstanceOf(RockyLinux10Installation.class);
        }

        @Test
        @DisplayName("osVersion 접두사가 8/9/10 어디에도 해당하지 않으면 ROCKY_LINUX_9 로 폴백 (트리 레벨 검증)")
        void fallsBackToRocky9_whenVersionPrefixUnknown() throws Exception {
            // 트리 레벨 검증: 완전 역직렬화까지 가면 application OSInstallation 의
            // isCompatible() 검증이 (COMPATIBLE_OS_VERSIONS.contains("99.x")==false) 로 실패하므로,
            // fixup 단독 동작은 트리에서 확인한다.
            String modern = serializeRocky9ProcessJson();
            String legacy = downgradeToLegacyRockyDiscriminator(modern, "ROCKY_LINUX_9");
            String legacyWithBadVersion = legacy.replace("\"osVersion\":\"9.5\"", "\"osVersion\":\"99.x\"");

            JsonNode root = objectMapper.readTree(legacyWithBadVersion);
            converter.migrateLegacyRockyDiscriminator(root);

            String newOsType = root
                    .get("processList")
                    .get(0)
                    .get("osInstallation")
                    .get("osType")
                    .asString();
            assertThat(newOsType).isEqualTo("ROCKY_LINUX_9");
        }

        @Test
        @DisplayName("osMetadata.osVersion 부재 시 ROCKY_LINUX_9 로 폴백 (트리 레벨 검증)")
        void fallsBackToRocky9_whenVersionMissing() throws Exception {
            String modern = serializeRocky9ProcessJson();
            String legacy = downgradeToLegacyRockyDiscriminator(modern, "ROCKY_LINUX_9");
            // osVersion 필드 자체를 제거
            String legacyWithoutVersion = legacy.replaceAll(",\"osVersion\":\"[^\"]+\"", "");

            JsonNode root = objectMapper.readTree(legacyWithoutVersion);
            converter.migrateLegacyRockyDiscriminator(root);

            String newOsType = root
                    .get("processList")
                    .get(0)
                    .get("osInstallation")
                    .get("osType")
                    .asString();
            assertThat(newOsType).isEqualTo("ROCKY_LINUX_9");
        }
    }

    @Nested
    @DisplayName("신 판별자는 fixup 없이 그대로 역직렬화")
    class ModernDiscriminatorRoundTrip {

        @Test
        @DisplayName("ROCKY_LINUX_9 판별자 JSON 은 그대로 RockyLinux9Installation 으로 복원된다")
        void roundTripRocky9() {
            String modern = serializeRocky9ProcessJson();
            SettingProcess restored = converter.convertToEntityAttribute(modern);

            OSInstallation osInstallation = (OSInstallation) restored.processList().get(0);
            assertThat(osInstallation.getOsInstallation()).isInstanceOf(RockyLinux9Installation.class);
        }

        @Test
        @DisplayName("비-Rocky (CentOS 7) JSON 은 fixup 영향을 받지 않는다")
        void roundTripCentOS7_unaffected() {
            String modern = serializeCentOS7ProcessJson();
            // 전제: 이미 신 판별자 CENTOS_7 으로 직렬화되어 있음
            assertThat(modern).contains("\"osType\":\"CENTOS_7\"");

            SettingProcess restored = converter.convertToEntityAttribute(modern);

            OSInstallation osInstallation = (OSInstallation) restored.processList().get(0);
            assertThat(osInstallation.getOsInstallation()).isInstanceOf(CentOS7Installation.class);
        }
    }

    @Nested
    @DisplayName("빈/NULL 처리")
    class NullAndEmptyHandling {

        @Test
        @DisplayName("null 입력은 null 반환")
        void nullInput_returnsNull() {
            assertThat(converter.convertToEntityAttribute(null)).isNull();
        }

        @Test
        @DisplayName("빈 문자열 입력은 null 반환")
        void emptyInput_returnsNull() {
            assertThat(converter.convertToEntityAttribute("")).isNull();
        }
    }
}
