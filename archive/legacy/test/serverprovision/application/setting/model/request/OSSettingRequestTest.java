package com.example.serverprovision.application.setting.model.request;

import com.example.serverprovision.domain.os.model.enums.ServiceAction;
import com.example.serverprovision.domain.os.model.setting.ServiceDirective;
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

/**
 * OSSettingRequest 다형성 구조 및 Jakarta Validation 회귀 테스트.
 *
 * <p>리팩토링 후 {@code OSSettingRequest} 는 abstract 로, 구체 서브타입은 {@code RHELOSSettingRequest}
 * 등으로 분리되었다. Jackson 판별자는 1단계 {@code type=OS_SETTING} + 2단계
 * {@code osFamily=RHEL_BASED} 구조이다.</p>
 */
class OSSettingRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("RHELOSSettingRequest - selinuxMode 검증")
    class SelinuxModeValidationTest {

        @ParameterizedTest
        @ValueSource(strings = {"enforcing", "permissive", "disabled"})
        @DisplayName("유효한 selinuxMode 는 검증을 통과한다")
        void validSelinuxMode_passesValidation(String mode) {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, mode, List.of(), List.of());
            Set<ConstraintViolation<RHELOSSettingRequest>> violations = validator.validate(req);
            assertThat(violations).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"ENFORCING", "Permissive", "invalid", "on", "off"})
        @DisplayName("잘못된 selinuxMode 는 @Pattern 위반이 발생한다")
        void invalidSelinuxMode_failsPatternValidation(String mode) {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, mode, List.of(), List.of());
            Set<ConstraintViolation<RHELOSSettingRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("selinuxMode"));
        }

        @Test
        @DisplayName("null selinuxMode 는 @NotBlank 위반이 발생한다")
        void nullSelinuxMode_failsNotBlankValidation() {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, null, List.of(), List.of());
            Set<ConstraintViolation<RHELOSSettingRequest>> violations = validator.validate(req);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("selinuxMode"));
        }
    }

    @Nested
    @DisplayName("RHELOSSettingRequest - osMetadataId 검증")
    class OsMetadataIdValidationTest {

        @Test
        @DisplayName("null osMetadataId 는 @NotNull 위반이 발생한다")
        void nullOsMetadataId_failsNotNull() {
            RHELOSSettingRequest req = new RHELOSSettingRequest(null, "enforcing", List.of(), List.of());
            Set<ConstraintViolation<RHELOSSettingRequest>> violations = validator.validate(req);

            assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("osMetadataId"));
        }
    }

    @Nested
    @DisplayName("생성자 null 방어")
    class NullDefenseTest {

        @Test
        @DisplayName("null services 는 빈 리스트로 변환된다")
        void nullServices_convertedToEmptyList() {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, "enforcing", null, List.of());
            assertThat(req.getServices()).isEmpty();
        }

        @Test
        @DisplayName("null additionalPackages 는 빈 리스트로 변환된다")
        void nullAdditionalPackages_convertedToEmptyList() {
            RHELOSSettingRequest req = new RHELOSSettingRequest(1L, "enforcing", List.of(), null);
            assertThat(req.getAdditionalPackages()).isEmpty();
        }

        @Test
        @DisplayName("services 원소의 action 이 null 이면 ENABLE 로 기본 해석된다")
        void nullActionInDirective_defaultsToEnable() {
            ServiceDirective d = new ServiceDirective("httpd", null);
            RHELOSSettingRequest req = new RHELOSSettingRequest(
                    1L, "enforcing", List.of(d), List.of());

            assertThat(req.getServices()).hasSize(1);
            assertThat(req.getServices().get(0).action()).isEqualTo(ServiceAction.ENABLE);
        }
    }

    @Nested
    @DisplayName("Jackson 판별자 구조 (구조적 검증)")
    class JacksonAnnotationTest {

        @Test
        @DisplayName("OSSettingRequest 는 abstract + @JsonTypeInfo(osFamily) + RHEL_BASED 서브타입을 보유한다")
        void abstractClassHasPolymorphicAnnotations() {
            Class<?> clazz = OSSettingRequest.class;
            assertThat(java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())).isTrue();

            com.fasterxml.jackson.annotation.JsonTypeInfo typeInfo =
                    clazz.getAnnotation(com.fasterxml.jackson.annotation.JsonTypeInfo.class);
            assertThat(typeInfo).isNotNull();
            assertThat(typeInfo.property()).isEqualTo("osFamily");

            com.fasterxml.jackson.annotation.JsonSubTypes subTypes =
                    clazz.getAnnotation(com.fasterxml.jackson.annotation.JsonSubTypes.class);
            assertThat(subTypes).isNotNull();
            assertThat(subTypes.value()).hasSize(1);
            assertThat(subTypes.value()[0].name()).isEqualTo("RHEL_BASED");
            assertThat(subTypes.value()[0].value()).isEqualTo(RHELOSSettingRequest.class);
        }
    }
}
