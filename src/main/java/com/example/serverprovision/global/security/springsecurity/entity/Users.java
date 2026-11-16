package com.example.serverprovision.global.security.springsecurity.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/** 접근 사용자. MA8 부터 등록 시각(created_at)을 갖는다 — 사용자 관리 목록의 등록 기간 조건이 이것을 본다. */
@Entity
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class Users extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false, length = 64)
	private String username;

	@ToString.Exclude
	@Column(nullable = false, length = 60)
	private String password;

	@Column(length = 20, nullable = false)
	private String name;

	private Boolean isEnabled;

	private Boolean isLocked;

	/** S18 — 관리자가 초기 비밀번호로 만든 계정은 첫 로그인에서 비밀번호를 바꿔야 한다. */
	@Column(nullable = false)
	private boolean mustChangePassword;

	/** 비밀번호 교체 — 강제 변경 플래그도 함께 내린다. */
	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
		this.mustChangePassword = false;
	}

	/** MA8 — 최고 관리자가 초기 비밀번호를 지정한다. 사용자는 다음 로그인에서 반드시 바꿔야 한다. */
	public void assignInitialPassword(String encodedPassword) {
		this.password = encodedPassword;
		this.mustChangePassword = true;
	}

	/** 로그인 가능 여부 — 컬럼이 nullable 이라 null 은 비활성으로 읽는다({@code UserDetails_Impl.isEnabled} 와 같은 해석). */
	public boolean enabled() {
		return Boolean.TRUE.equals(isEnabled);
	}

	public void disable() {
		this.isEnabled = false;
	}

	public void enable() {
		this.isEnabled = true;
	}
}
