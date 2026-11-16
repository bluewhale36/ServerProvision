package com.example.serverprovision.global.security.springsecurity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** S18 · MA8 — 권한 포함 관계(SUPER_ADMIN ⊃ ADMIN ⊃ USER)와 계정 생성 경로의 상수별 규칙. */
class RoleAndSignupModeTest {

	@Test
	@DisplayName("expand — 상위 등급을 요청하면 하위가 따라온다 · 순서는 높은 등급부터 · null · 빈 입력은 빈 집합")
	void expand_impliesLower() {
		assertThat(Role.expand(List.of(Role.SUPER_ADMIN))).containsExactly(Role.SUPER_ADMIN, Role.ADMIN, Role.USER);
		assertThat(Role.expand(List.of(Role.ADMIN))).containsExactly(Role.ADMIN, Role.USER);
		assertThat(Role.expand(List.of(Role.USER))).containsExactly(Role.USER);
		assertThat(Role.expand(List.of(Role.USER, Role.ADMIN))).containsExactly(Role.ADMIN, Role.USER);
		assertThat(Role.expand(null)).isEmpty();
		assertThat(Role.expand(List.of())).isEmpty();
	}

	@Test
	@DisplayName("highest — 가진 권한 중 가장 높은 등급 · 비어 있으면 null")
	void highest() {
		assertThat(Role.highest(List.of(Role.USER, Role.ADMIN, Role.SUPER_ADMIN))).isEqualTo(Role.SUPER_ADMIN);
		assertThat(Role.highest(List.of(Role.USER, Role.ADMIN))).isEqualTo(Role.ADMIN);
		assertThat(Role.highest(List.of(Role.USER))).isEqualTo(Role.USER);
		assertThat(Role.highest(List.of())).isNull();
	}

	@Test
	@DisplayName("BOOTSTRAP — 요청 등급을 무시하고 SUPER_ADMIN(+ADMIN+USER) 고정 · 강제 변경 없음 · 등급 선택 불요 · /signup 제출 · 로그인으로")
	void bootstrapMode() {
		SignupMode mode = SignupMode.BOOTSTRAP;
		assertThat(mode.rolesFor(Role.USER)).containsExactly(Role.SUPER_ADMIN, Role.ADMIN, Role.USER);
		assertThat(mode.mustChangePassword()).isFalse();
		assertThat(mode.requiresRoleSelection()).isFalse();
		assertThat(mode.view()).isEqualTo("security/signup-bootstrap");
		assertThat(mode.formAction()).isEqualTo("/signup");
		assertThat(mode.successRedirect(7L)).isEqualTo("redirect:/login");
	}

	@Test
	@DisplayName("MANAGED — 요청 등급을 포함 관계로 닫고 · 강제 변경 · 등급 선택 필요 · /users 제출 · 만든 사용자의 상세로")
	void managedMode() {
		SignupMode mode = SignupMode.MANAGED;
		assertThat(mode.rolesFor(Role.ADMIN)).containsExactly(Role.ADMIN, Role.USER);
		assertThat(mode.rolesFor(Role.USER)).containsExactly(Role.USER);
		assertThat(mode.rolesFor(null)).isEmpty();
		assertThat(mode.mustChangePassword()).isTrue();
		assertThat(mode.requiresRoleSelection()).isTrue();
		assertThat(mode.view()).isEqualTo("users/user-new");
		assertThat(mode.formAction()).isEqualTo("/users");
		assertThat(mode.successRedirect(7L)).isEqualTo("redirect:/users/7");
		assertThat(mode.successMessage("hong.gildong")).contains("hong.gildong").contains("비밀번호 변경");
	}

	@Test
	@DisplayName("PasswordRule — 빈 값은 침묵(NotBlank 몫) · 허용 문자 8~72 · 한글 · 공백 · 7자 · 73자 거절")
	void passwordRule() {
		assertThat("".matches(PasswordRule.REGEX)).isTrue();
		assertThat("Passw0rd!".matches(PasswordRule.REGEX)).isTrue();
		assertThat("a1!@#$%^&*()-_=+.,?".matches(PasswordRule.REGEX)).isTrue();
		assertThat("Short1!".matches(PasswordRule.REGEX)).isFalse();
		assertThat("한글비밀번호1!".matches(PasswordRule.REGEX)).isFalse();
		assertThat("has space1!".matches(PasswordRule.REGEX)).isFalse();
		assertThat("a".repeat(73).matches(PasswordRule.REGEX)).isFalse();
		assertThat("a".repeat(72).matches(PasswordRule.REGEX)).isTrue();
	}
}
