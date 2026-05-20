package com.example.serverprovision.global.trash.dto.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5-2-4 — cron 식 문법 검증이 정확한 field name 에 rejectValue 되는지 회귀.
 */
class TrashSettingsRequestValidatorTest {

    TrashSettingsRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TrashSettingsRequestValidator();
    }

    private TrashSettingsRequest build(String purgeCron, String notifyCron) {
        return new TrashSettingsRequest(
                30, true,
                purgeCron, notifyCron,
                "7,1", "BACKGROUND_JOB,SERVER_LOG",
                3, 1000L);
    }

    @Test
    @DisplayName("(15) 유효한 cron 두 개 — 에러 없음")
    void bothValid() {
        TrashSettingsRequest req = build("0 0 * * * *", "0 0 0 * * *");
        Errors errors = new BeanPropertyBindingResult(req, "form");

        validator.validate(req, errors);

        assertThat(errors.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("(16) purgeCron 문법 오류 — 정확한 field 에 rejectValue")
    void purgeCronInvalid() {
        TrashSettingsRequest req = build("not-a-cron", "0 0 * * * *");
        Errors errors = new BeanPropertyBindingResult(req, "form");

        validator.validate(req, errors);

        assertThat(errors.getFieldErrorCount("purgeCronExpression")).isEqualTo(1);
        FieldError fe = errors.getFieldError("purgeCronExpression");
        assertThat(fe).isNotNull();
        assertThat(fe.getDefaultMessage()).contains("cron 식의 형식이 올바르지 않아요");
    }

    @Test
    @DisplayName("(17) notifyCron 문법 오류 — 정확한 field 에 rejectValue")
    void notifyCronInvalid() {
        TrashSettingsRequest req = build("0 0 * * * *", "garbage");
        Errors errors = new BeanPropertyBindingResult(req, "form");

        validator.validate(req, errors);

        assertThat(errors.getFieldErrorCount("notifyCronExpression")).isEqualTo(1);
        assertThat(errors.hasFieldErrors("purgeCronExpression")).isFalse();
    }

    @Test
    @DisplayName("(18) 둘 다 문법 오류 — 둘 다 각 field 에 등록")
    void bothInvalid() {
        TrashSettingsRequest req = build("aa", "bb");
        Errors errors = new BeanPropertyBindingResult(req, "form");

        validator.validate(req, errors);

        assertThat(errors.getFieldErrorCount("purgeCronExpression")).isEqualTo(1);
        assertThat(errors.getFieldErrorCount("notifyCronExpression")).isEqualTo(1);
    }

    @Test
    @DisplayName("(19) blank cron — validator skip (NotBlank 가 별도 메시지 처리)")
    void blankSkipped() {
        TrashSettingsRequest req = build("", "  ");
        Errors errors = new BeanPropertyBindingResult(req, "form");

        validator.validate(req, errors);

        assertThat(errors.hasFieldErrors("purgeCronExpression")).isFalse();
        assertThat(errors.hasFieldErrors("notifyCronExpression")).isFalse();
    }
}
