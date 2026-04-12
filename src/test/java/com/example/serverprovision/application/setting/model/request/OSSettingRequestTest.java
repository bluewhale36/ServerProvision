package com.example.serverprovision.application.setting.model.request;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OSSettingRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    // ========== Jakarta Validation ==========

    @Nested
    @DisplayName("Jakarta Validation - selinuxMode")
    class SelinuxModeValidationTest {

        @ParameterizedTest
        @ValueSource(strings = {"enforcing", "permissive", "disabled"})
        @DisplayName("유효한 selinuxMode는 검증을 통과한다")
        void validSelinuxMode_passesValidation(String mode) {
            OSSettingRequest request = new OSSettingRequest(mode, List.of(), List.of());
            Set<ConstraintViolation<OSSettingRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ENFORCING", "Permissive", "DISABLED", "invalid", "selinux", "on", "off"})
        @DisplayName("잘못된 selinuxMode는 @Pattern 위반이 발생한다")
        void invalidSelinuxMode_failsPatternValidation(String mode) {
            OSSettingRequest request = new OSSettingRequest(mode, List.of(), List.of());
            Set<ConstraintViolation<OSSettingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("selinuxMode"));
        }

        @Test
        @DisplayName("null selinuxMode는 @NotBlank 위반이 발생한다")
        void nullSelinuxMode_failsNotBlankValidation() {
            OSSettingRequest request = new OSSettingRequest(null, List.of(), List.of());
            Set<ConstraintViolation<OSSettingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("selinuxMode"));
        }

        @Test
        @DisplayName("빈 문자열 selinuxMode는 @NotBlank 위반이 발생한다")
        void emptySelinuxMode_failsNotBlankValidation() {
            OSSettingRequest request = new OSSettingRequest("", List.of(), List.of());
            Set<ConstraintViolation<OSSettingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v ->
                    v.getPropertyPath().toString().equals("selinuxMode"));
        }
    }

    @Nested
    @DisplayName("Jakarta Validation - List 필드")
    class ListFieldValidationTest {

        @Test
        @DisplayName("빈 리스트는 @NotNull 위반 없이 통과한다")
        void emptyLists_passValidation() {
            OSSettingRequest request = new OSSettingRequest("enforcing", List.of(), List.of());
            Set<ConstraintViolation<OSSettingRequest>> violations = validator.validate(request);
            assertThat(violations).isEmpty();
        }
    }

    // ========== null 방어 ==========

    @Nested
    @DisplayName("생성자 null 방어")
    class NullDefenseTest {

        @Test
        @DisplayName("null enabledServices는 빈 리스트로 변환된다")
        void nullEnabledServices_convertedToEmptyList() {
            OSSettingRequest request = new OSSettingRequest("enforcing", null, List.of());
            assertThat(request.getEnabledServices()).isEmpty();
        }

        @Test
        @DisplayName("null additionalPackages는 빈 리스트로 변환된다")
        void nullAdditionalPackages_convertedToEmptyList() {
            OSSettingRequest request = new OSSettingRequest("enforcing", List.of(), null);
            assertThat(request.getAdditionalPackages()).isEmpty();
        }
    }

    // ========== Jackson 역직렬화 ==========

    @Nested
    @DisplayName("Jackson 역직렬화")
    class JsonDeserializationTest {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("JSON에서 OSSettingRequest로 정상 역직렬화된다")
        void deserializesFromJson() throws Exception {
            String json = """
                    {
                        "type": "OS_SETTING",
                        "selinuxMode": "enforcing",
                        "enabledServices": ["httpd", "sshd"],
                        "additionalPackages": ["vim"]
                    }
                    """;

            AbstractProcessRequest result = objectMapper.readValue(json, AbstractProcessRequest.class);

            assertThat(result).isInstanceOf(OSSettingRequest.class);
            OSSettingRequest request = (OSSettingRequest) result;
            assertThat(request.getSelinuxMode()).isEqualTo("enforcing");
            assertThat(request.getEnabledServices()).containsExactly("httpd", "sshd");
            assertThat(request.getAdditionalPackages()).containsExactly("vim");
        }

        @Test
        @DisplayName("JSON에서 리스트 필드가 없으면 빈 리스트로 역직렬화된다")
        void deserializesFromJson_withMissingLists() throws Exception {
            String json = """
                    {
                        "type": "OS_SETTING",
                        "selinuxMode": "disabled"
                    }
                    """;

            AbstractProcessRequest result = objectMapper.readValue(json, AbstractProcessRequest.class);
            OSSettingRequest request = (OSSettingRequest) result;

            assertThat(request.getEnabledServices()).isEmpty();
            assertThat(request.getAdditionalPackages()).isEmpty();
        }
    }
}
