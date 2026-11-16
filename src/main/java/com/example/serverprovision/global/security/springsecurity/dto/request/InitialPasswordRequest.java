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

	/** 문구 상수 — 검증 어노테이션과 상세 화면의 제출 전 확인(security/user-detail.js, data 속성으로 전달)이 같은 문장을 쓴다. */
	public static final String REQUIRED = "초기 비밀번호를 입력하십시오.";
	public static final String RETYPE_REQUIRED = "초기 비밀번호를 한 번 더 입력하십시오.";
	public static final String MISMATCH = "비밀번호가 일치하지 않습니다.";

	@NotBlank(message = REQUIRED)
	@Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
	private String password;

	@NotBlank(message = RETYPE_REQUIRED)
	private String retypedPassword;

	/** 두 값이 모두 있을 때만 판정한다 — 빈 값은 {@code @NotBlank} 의 몫이라 메시지가 겹치지 않는다. */
	@AssertTrue(message = MISMATCH)
	public boolean isPasswordConfirmed() {
		if (password == null || password.isBlank() || retypedPassword == null || retypedPassword.isBlank()) {
			return true;
		}
		return password.equals(retypedPassword);
	}
}
