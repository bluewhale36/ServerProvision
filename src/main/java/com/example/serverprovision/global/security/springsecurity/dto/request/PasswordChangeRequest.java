package com.example.serverprovision.global.security.springsecurity.dto.request;

import com.example.serverprovision.global.security.springsecurity.domain.PasswordRule;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 비밀번호 변경 요청(S18 D-15). 현재 비밀번호 대조는 서비스가 한다(인코더 비교). */
public record PasswordChangeRequest(
		@NotBlank(message = "현재 비밀번호를 입력하십시오.")
		String currentPassword,

		@NotBlank(message = "새 비밀번호를 입력하십시오.")
		@Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
		String newPassword,

		@NotBlank(message = "새 비밀번호를 한 번 더 입력하십시오.")
		String retypedPassword
) {
	@AssertTrue(message = "새 비밀번호가 일치하지 않습니다.")
	public boolean isNewPasswordConfirmed() {
		if (isBlank(newPassword) || isBlank(retypedPassword)) {
			return true;
		}
		return newPassword.equals(retypedPassword);
	}

	@AssertTrue(message = "새 비밀번호는 현재 비밀번호와 달라야 합니다.")
	public boolean isNewPasswordDifferent() {
		if (isBlank(newPassword) || isBlank(currentPassword)) {
			return true;
		}
		return !newPassword.equals(currentPassword);
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
