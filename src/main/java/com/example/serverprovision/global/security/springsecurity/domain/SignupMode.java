package com.example.serverprovision.global.security.springsecurity.domain;

import java.util.Collection;
import java.util.Set;

/**
 * 계정을 만드는 두 경로(S18 D-13 · MA8 개정). 부트스트랩 = 관리자가 아직 없어 누구나 최초 계정(최고 관리자)을 만드는 상태,
 * 관리 생성 = 최고 관리자가 사용자 관리 화면에서 초기 비밀번호를 정해 계정을 만드는 상태. 경로가 정하는 것(권한 · 강제 변경 ·
 * 뷰 · 폼 대상 · 성공 이동)은 상수별 메서드로 두어 컨트롤러 · 서비스에 분기가 자라지 않게 한다.
 */
public enum SignupMode {

	BOOTSTRAP {
		@Override
		public Set<Role> rolesFor(Role requested) {
			return Role.expand(Set.of(Role.SUPER_ADMIN));
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
		public String formAction() {
			return "/signup";
		}

		@Override
		public String successRedirect(Long createdId) {
			return "redirect:/login";
		}

		@Override
		public String successMessage(String username) {
			return "최초 관리자 계정을 만들었습니다. 로그인하십시오.";
		}
	},
	MANAGED {
		@Override
		public Set<Role> rolesFor(Role requested) {
			return Role.expand(requested == null ? Set.of() : Set.of(requested));
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
			return "users/user-new";
		}

		@Override
		public String formAction() {
			return "/users";
		}

		@Override
		public String successRedirect(Long createdId) {
			return "redirect:/users/" + createdId;
		}

		@Override
		public String successMessage(String username) {
			return "사용자 계정 " + username + " 등록을 마쳤습니다. 첫 로그인에서 비밀번호 변경을 요구합니다.";
		}
	};

	/** 저장할 권한 집합 — 부트스트랩은 요청을 무시하고 최고 관리자로 고정한다. */
	public abstract Set<Role> rolesFor(Role requested);

	public abstract boolean requiresRoleSelection();

	/** 만들어진 계정이 첫 로그인에서 비밀번호를 바꿔야 하는가. */
	public abstract boolean mustChangePassword();

	public abstract String view();

	/** 폼 조각이 제출할 경로 — 두 화면이 같은 폼 조각을 쓰므로 대상만 경로가 정한다. */
	public abstract String formAction();

	public abstract String successRedirect(Long createdId);

	public abstract String successMessage(String username);
}
