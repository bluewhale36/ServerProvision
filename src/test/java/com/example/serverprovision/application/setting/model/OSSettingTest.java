package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.enums.ServiceAction;
import com.example.serverprovision.domain.os.model.setting.RockyLinux9Setting;
import com.example.serverprovision.domain.os.model.setting.ServiceDirective;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Application 계층 OSSetting 래퍼 회귀 테스트.
 *
 * <p>리팩토링 후 OSSetting 은 {@code OSMetadataDTO + domain OSSetting} 조합의 wrapper 로 동작한다.
 * 구 API(flat selinuxMode/services/additionalPackages 필드) 는 더 이상 존재하지 않으며,
 * 해당 로직은 {@code RHELBasedSettingTest} 에서 직접 도메인 레이어를 검증한다.</p>
 */
class OSSettingTest {

    private OSMetadataDTO rocky96() {
        return new OSMetadataDTO(
                1L,
                OSName.ROCKY_LINUX,
                "9.6",
                "/mnt/iso/rocky9",
                null,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private RockyLinux9Setting sampleDomainSetting() {
        return RockyLinux9Setting.builder()
                .selinuxMode("enforcing")
                .services(List.of(new ServiceDirective("httpd", ServiceAction.ENABLE)))
                .additionalPackages(List.of("vim"))
                .build();
    }

    @Nested
    @DisplayName("생성자 검증")
    class ConstructorTest {

        @Test
        @DisplayName("호환되는 메타데이터 + 도메인 설정으로 인스턴스를 생성한다")
        void createsInstance_whenCompatible() {
            OSSetting setting = new OSSetting(rocky96(), sampleDomainSetting());

            assertThat(setting.getOsMetadata().osName()).isEqualTo(OSName.ROCKY_LINUX);
            assertThat(setting.getOsSetting()).isInstanceOf(RockyLinux9Setting.class);
            assertThat(setting.getProcessStep()).isEqualTo(SettingProcessStep.OS_SETTING);
        }

        @Test
        @DisplayName("OS 메타데이터와 도메인 설정의 버전이 불일치하면 IllegalArgumentException 을 던진다")
        void throwsWhenIncompatible() {
            OSMetadataDTO rocky100 = new OSMetadataDTO(
                    2L, OSName.ROCKY_LINUX, "10.0",
                    "/mnt/iso/rocky10", null, true,
                    LocalDateTime.now(), LocalDateTime.now()
            );

            assertThatThrownBy(() -> new OSSetting(rocky100, sampleDomainSetting()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("호환");
        }
    }

    @Nested
    @DisplayName("Jackson 직렬화")
    class JsonSerializationTest {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("직렬화 시 type=OS_SETTING, 도메인 osType 판별자가 포함된다")
        void serializesWithDiscriminators() throws Exception {
            OSSetting setting = new OSSetting(rocky96(), sampleDomainSetting());

            String json = objectMapper.writeValueAsString(setting);

            assertThat(json).contains("\"type\":\"OS_SETTING\"");
            assertThat(json).contains("\"osType\":\"ROCKY_LINUX_9\"");
            assertThat(json).contains("\"selinuxMode\":\"enforcing\"");
        }

        @Test
        @DisplayName("JSON 에서 AbstractSettingProcess 로 역직렬화된다")
        void deserializesAsAbstractProcess() throws Exception {
            OSSetting original = new OSSetting(rocky96(), sampleDomainSetting());
            String json = objectMapper.writeValueAsString(original);

            AbstractSettingProcess restored = objectMapper.readValue(json, AbstractSettingProcess.class);

            assertThat(restored).isInstanceOf(OSSetting.class);
            OSSetting asSetting = (OSSetting) restored;
            assertThat(asSetting.getOsMetadata().osVersion()).isEqualTo("9.6");
            assertThat(asSetting.getOsSetting()).isInstanceOf(RockyLinux9Setting.class);
        }
    }
}
