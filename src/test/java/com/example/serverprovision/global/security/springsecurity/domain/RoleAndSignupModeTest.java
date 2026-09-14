package com.example.serverprovision.global.security.springsecurity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** S18 — 권한 포함 관계(ADMIN ⊃ USER)와 가입 모드의 상수별 규칙. */
class RoleAndSignupModeTest {

	@Test
	@DisplayName("expand — ADMIN 을 요청하면 USER 가 따라온다 · USER 만이면 USER · null · 빈 입력은 빈 집합")
	void expand_adminImpliesUser() {
		assertThat(Role.expand(List.of(Role.ADMIN))).containsExactly(Role.ADMIN, Role.USER);
		assertThat(Role.expand(List.of(Role.USER))).containsExactly(Role.USER);
		assertThat(Role.expand(List.of(Role.USER, Role.ADMIN))).containsExactly(Role.ADMIN, Role.USER);
		assertThat(Role.expand(null)).isEmpty();
		assertThat(Role.expand(List.of())).isEmpty();
	}

	@Test
	@DisplayName("BOOTSTRAP_ADMIN — 요청 역할을 무시하고 ADMIN(+USER) 고정 · 강제 변경 없음 · 역할 선택 불요 · 로그인으로")
	void bootstrapMode() {
		SignupMode mode = SignupMode.of(true);
		assertThat(mode).isEqualTo(SignupMode.BOOTSTRAP_ADMIN);
		assertThat(mode.rolesFor(List.of(Role.USER))).containsExactly(Role.ADMIN, Role.USER);
		assertThat(mode.mustChangePassword()).isFalse();
		assertThat(mode.requiresRoleSelection()).isFalse();
		assertThat(mode.view()).isEqualTo("security/signup-bootstrap");
		assertThat(mode.successRedirect()).isEqualTo("redirect:/login");
	}

	@Test
	@DisplayName("ADMIN_CREATES_USER — 요청 역할을 포함 관계로 닫고 · 강제 변경 · 역할 선택 필요 · 등록 화면으로")
	void adminCreatesUserMode() {
		SignupMode mode = SignupMode.of(false);
		assertThat(mode).isEqualTo(SignupMode.ADMIN_CREATES_USER);
		assertThat(mode.rolesFor(Set.of(Role.ADMIN))).containsExactly(Role.ADMIN, Role.USER);
		assertThat(mode.rolesFor(Set.of(Role.USER))).containsExactly(Role.USER);
		assertThat(mode.mustChangePassword()).isTrue();
		assertThat(mode.requiresRoleSelection()).isTrue();
		assertThat(mode.successRedirect()).isEqualTo("redirect:/signup");
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
