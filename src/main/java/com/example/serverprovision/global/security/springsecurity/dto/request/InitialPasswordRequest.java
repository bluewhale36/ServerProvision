package com.example.serverprovision.global.security.springsecurity.dto.request;

import com.example.serverprovision.global.security.springsecurity.domain.PasswordRule;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 초기 비밀번호 지정(MA8). 규칙 · 재입력 대조는 가입 폼과 같다({@link PasswordRule} · {@link SignUpRequest#isPasswordConfirmed}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InitialPasswordRequest {

	@NotBlank(message = PasswordRule.BLANK)   // HF29 — 문구는 PasswordRule 한 벌(사용자 추가 폼과 같은 문장)
	@Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
	private String password;

	@NotBlank(message = PasswordRule.RETYPE_BLANK)
	private String retypedPassword;

	/** 두 값이 모두 있을 때만 판정한다 — 빈 값은 {@code @NotBlank} 의 몫이라 메시지가 겹치지 않는다. */
	@AssertTrue(message = PasswordRule.MISMATCH)
	public boolean isPasswordConfirmed() {
		if (password == null || password.isBlank() || retypedPassword == null || retypedPassword.isBlank()) {
			return true;
		}
		return password.equals(retypedPassword);
	}
}
