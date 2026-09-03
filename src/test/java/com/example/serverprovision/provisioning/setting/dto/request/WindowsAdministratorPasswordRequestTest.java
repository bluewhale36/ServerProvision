package com.example.serverprovision.provisioning.setting.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-a-2 CP4 — Administrator 비밀번호 Layer A(값 또는 기존 유지 · 공백/제어문자 금지 · 127자) + 비밀값 사본. */
class WindowsAdministratorPasswordRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> messages(WindowsAdministratorPasswordRequest request) {
        return validator.validate(request).stream().map(ConstraintViolation::getMessage).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("값 없음 + 유지 아님 → 입력 요구 · 값 있음 → 통과 · 유지 → 값 없어도 통과")
    void providedOrKept() {
        assertThat(messages(new WindowsAdministratorPasswordRequest(null, false)))
                .containsExactly("Administrator 비밀번호를 입력하거나 기존 비밀번호 유지를 선택해야 합니다.");
        // 빈 문자열도 문구 하나만 — 패턴이 빈 값을 통과시켜 겹치지 않는다(CP5 O-1).
        assertThat(messages(new WindowsAdministratorPasswordRequest("", false)))
                .containsExactly("Administrator 비밀번호를 입력하거나 기존 비밀번호 유지를 선택해야 합니다.");
        assertThat(messages(new WindowsAdministratorPasswordRequest("S3rver!2025", false))).isEmpty();
        assertThat(messages(new WindowsAdministratorPasswordRequest(null, true))).isEmpty();
        assertThat(new WindowsAdministratorPasswordRequest(null, null).isKeepExistingPassword()).isFalse(); // 누락 = false
    }

    @Test
    @DisplayName("공백 · 제어문자 → 패턴 위반, 128자 → 길이 위반")
    void patternAndSize() {
        assertThat(messages(new WindowsAdministratorPasswordRequest("has space", false)))
                .contains("비밀번호에는 공백 · 제어문자를 쓸 수 없습니다.");
        assertThat(messages(new WindowsAdministratorPasswordRequest("A".repeat(128), false)))
                .contains("Administrator 비밀번호는 127자를 넘을 수 없습니다.");
    }

    @Test
    @DisplayName("withoutSecret 은 값 제거 + 유지 플래그, retaining 은 저장값 이어받기 + 유지 해제")
    void copies() {
        WindowsAdministratorPasswordRequest original = new WindowsAdministratorPasswordRequest("S3rver!2025", false);
        WindowsAdministratorPasswordRequest stripped = original.withoutSecret();
        assertThat(stripped.getPassword()).isNull();
        assertThat(stripped.isKeepExistingPassword()).isTrue();
        assertThat(stripped.hasPassword()).isFalse();

        WindowsAdministratorPasswordRequest retained = new WindowsAdministratorPasswordRequest(null, true).retaining("Old!2025");
        assertThat(retained.getPassword()).isEqualTo("Old!2025");
        assertThat(retained.isKeepExistingPassword()).isFalse();
    }
}
