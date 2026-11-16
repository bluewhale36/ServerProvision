package com.example.serverprovision.global.security.springsecurity.dto.request;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 권한 등급 변경(MA8). setter 클래스인 이유는 {@link SignUpRequest} 와 같다 — 위조값({@code level=NOPE})이 record 생성자 바인딩을
 * 깨뜨리지 않고 typeMismatch 필드 오류(400)로 남는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class RoleChangeRequest {

	@NotNull(message = "권한 등급을 고르십시오.")
	private Role level;
}
