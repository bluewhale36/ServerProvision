package com.example.serverprovision.global.security.springsecurity.dto.request;

import com.example.serverprovision.global.security.springsecurity.domain.PasswordRule;
import com.example.serverprovision.global.security.springsecurity.domain.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 가입 요청(S18 D-4) — 아이디는 사내 메일 도메인 앞부분을 그대로 쓰므로 형식 규칙이 없다. 역할은 관리자 모드에서만 필요해
 * 어노테이션 대신 컨트롤러가 {@code SignupMode.requiresRoleSelection()} 으로 판정한다.
 *
 * <p>record 가 아니라 setter 클래스인 이유: 역할 체크박스 값이 위조되면(예 {@code roles=NOPE}) record 의 생성자 바인딩은 대상 객체를
 * 만들지 못해 폼 재렌더가 500 으로 새지만, 프로퍼티 바인딩은 그 필드만 typeMismatch 로 남기고 나머지 입력을 보존한다(R15-1 선례).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequest {

	@NotBlank(message = "아이디를 입력하십시오.")
	@Size(max = 64, message = "아이디는 64자 이하로 입력하십시오.")
	private String username;

	@NotBlank(message = "비밀번호를 입력하십시오.")
	@Pattern(regexp = PasswordRule.REGEX, message = PasswordRule.MESSAGE)
	private String password;

	@NotBlank(message = "비밀번호를 한 번 더 입력하십시오.")
	private String retypedPassword;

	@NotBlank(message = "이름을 입력하십시오.")
	@Size(max = 20, message = "이름은 20자 이하로 입력하십시오.")
	private String name;

	private List<Role> roles;

	/** 두 값이 모두 있을 때만 판정한다 — 빈 값은 {@code @NotBlank} 의 몫이라 메시지가 겹치지 않는다. */
	@AssertTrue(message = "비밀번호가 일치하지 않습니다.")
	public boolean isPasswordConfirmed() {
		if (password == null || password.isBlank() || retypedPassword == null || retypedPassword.isBlank()) {
			return true;
		}
		return password.equals(retypedPassword);
	}

	public List<Role> rolesOrEmpty() {
		return roles == null ? List.of() : roles;
	}

	public String username() {
		return username;
	}

	public String password() {
		return password;
	}

	public String retypedPassword() {
		return retypedPassword;
	}

	public String name() {
		return name;
	}
}
