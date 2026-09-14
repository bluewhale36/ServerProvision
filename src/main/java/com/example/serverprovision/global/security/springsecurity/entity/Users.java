package com.example.serverprovision.global.security.springsecurity.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class Users {

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
}
