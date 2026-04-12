package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OSSettingTest {

    @Nested
    @DisplayName("생성자 - 방어적 null 처리")
    class ConstructorTest {

        @Test
        @DisplayName("정상 파라미터로 인스턴스를 생성한다")
        void createsInstance_withValidParams() {
            OSSetting setting = new OSSetting("enforcing", List.of("httpd"), List.of("vim"));

            assertThat(setting.getSelinuxMode()).isEqualTo("enforcing");
            assertThat(setting.getEnabledServices()).containsExactly("httpd");
            assertThat(setting.getAdditionalPackages()).containsExactly("vim");
        }

        @Test
        @DisplayName("null enabledServices는 빈 리스트로 변환된다")
        void convertsNullEnabledServices_toEmptyList() {
            OSSetting setting = new OSSetting("permissive", null, List.of("vim"));
            assertThat(setting.getEnabledServices()).isEmpty();
        }

        @Test
        @DisplayName("null additionalPackages는 빈 리스트로 변환된다")
        void convertsNullAdditionalPackages_toEmptyList() {
            OSSetting setting = new OSSetting("disabled", List.of("httpd"), null);
            assertThat(setting.getAdditionalPackages()).isEmpty();
        }

        @Test
        @DisplayName("둘 다 null이면 둘 다 빈 리스트로 변환된다")
        void convertsBothNulls_toEmptyLists() {
            OSSetting setting = new OSSetting("enforcing", null, null);
            assertThat(setting.getEnabledServices()).isEmpty();
            assertThat(setting.getAdditionalPackages()).isEmpty();
        }
    }

    @Nested
    @DisplayName("processStep 검증")
    class ProcessStepTest {

        @Test
        @DisplayName("processStep이 OS_SETTING이다")
        void processStepIsOSSetting() {
            OSSetting setting = new OSSetting("enforcing", List.of(), List.of());
            assertThat(setting.getProcessStep()).isEqualTo(SettingProcessStep.OS_SETTING);
        }
    }

    @Nested
    @DisplayName("Jackson 직렬화/역직렬화")
    class JsonSerializationTest {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("JSON 라운드트립 시 type 판별자 OS_SETTING이 포함된다")
        void jsonRoundTrip_containsTypeDiscriminator() throws Exception {
            OSSetting original = new OSSetting(
                    "enforcing", List.of("httpd", "sshd"), List.of("vim", "net-tools"));

            String json = objectMapper.writeValueAsString(original);

            assertThat(json).contains("\"type\"");
            assertThat(json).contains("OS_SETTING");
            assertThat(json).contains("\"selinuxMode\":\"enforcing\"");
        }

        @Test
        @DisplayName("JSON 역직렬화 시 AbstractSettingProcess로 정확히 복원된다")
        void deserializesFromJson_toOSSetting() throws Exception {
            String json = """
                    {
                        "type": "OS_SETTING",
                        "selinuxMode": "permissive",
                        "enabledServices": ["httpd"],
                        "additionalPackages": ["vim", "net-tools"]
                    }
                    """;

            AbstractSettingProcess result = objectMapper.readValue(json, AbstractSettingProcess.class);

            assertThat(result).isInstanceOf(OSSetting.class);
            OSSetting osSetting = (OSSetting) result;
            assertThat(osSetting.getSelinuxMode()).isEqualTo("permissive");
            assertThat(osSetting.getEnabledServices()).containsExactly("httpd");
            assertThat(osSetting.getAdditionalPackages()).containsExactly("vim", "net-tools");
        }

        @Test
        @DisplayName("JSON에서 enabledServices가 없으면 빈 리스트로 역직렬화된다")
        void deserializesFromJson_withMissingLists() throws Exception {
            String json = """
                    {
                        "type": "OS_SETTING",
                        "selinuxMode": "disabled"
                    }
                    """;

            AbstractSettingProcess result = objectMapper.readValue(json, AbstractSettingProcess.class);
            OSSetting osSetting = (OSSetting) result;

            assertThat(osSetting.getEnabledServices()).isEmpty();
            assertThat(osSetting.getAdditionalPackages()).isEmpty();
        }
    }
}
