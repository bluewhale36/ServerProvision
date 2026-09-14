package com.example.serverprovision.global.security.springsecurity.domain;

import java.util.Collection;
import java.util.Set;

/**
 * 가입 화면의 두 모드(S18 D-13). 부트스트랩 = 관리자가 아직 없어 누구나 최초 관리자를 만드는 상태,
 * 관리자 생성 = 관리자가 초기 비밀번호를 정해 사용자를 만드는 상태. 모드가 정하는 것(역할 · 강제 변경 · 뷰 · 성공 이동)은
 * 상수별 메서드로 두어 컨트롤러 · 서비스에 분기가 자라지 않게 한다.
 */
public enum SignupMode {

	BOOTSTRAP_ADMIN {
		@Override
		public Set<Role> rolesFor(Collection<Role> requested) {
			return Role.expand(Set.of(Role.ADMIN));
		}

		@Override
		public boolean requiresRoleSelection() {
			return false;
		}

		@Override
		public boolean mustChangePassword() {
			return false;
		}

		@Override
		public String view() {
			return "security/signup-bootstrap";
		}

		@Override
		public String successRedirect() {
			return "redirect:/login";
		}

		@Override
		public String successMessage(String username) {
			return "최초 관리자 계정을 만들었습니다. 로그인하십시오.";
		}
	},
	ADMIN_CREATES_USER {
		@Override
		public Set<Role> rolesFor(Collection<Role> requested) {
			return Role.expand(requested);
		}

		@Override
		public boolean requiresRoleSelection() {
			return true;
		}

		@Override
		public boolean mustChangePassword() {
			return true;
		}

		@Override
		public String view() {
			return "security/signup";
		}

		@Override
		public String successRedirect() {
			return "redirect:/signup";
		}

		@Override
		public String successMessage(String username) {
			return "사용자 계정 " + username + " 등록을 마쳤습니다. 첫 로그인에서 비밀번호 변경을 요구합니다.";
		}
	};

	/** 저장할 권한 집합 — 부트스트랩은 요청을 무시하고 관리자로 고정한다. */
	public abstract Set<Role> rolesFor(Collection<Role> requested);

	public abstract boolean requiresRoleSelection();

	/** 만들어진 계정이 첫 로그인에서 비밀번호를 바꿔야 하는가. */
	public abstract boolean mustChangePassword();

	public abstract String view();

	public abstract String successRedirect();

	public abstract String successMessage(String username);

	/** 부트스트랩 판정(관리자 부재)에서 모드를 고른다 — 판정 자체는 {@code UsersService.bootstrapOpen()} 이 SSOT. */
	public static SignupMode of(boolean bootstrapOpen) {
		return bootstrapOpen ? BOOTSTRAP_ADMIN : ADMIN_CREATES_USER;
	}
}
