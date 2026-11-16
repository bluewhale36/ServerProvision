package com.example.serverprovision.global.security.springsecurity.domain;

import com.example.serverprovision.global.security.springsecurity.entity.Users;

/**
 * 사용자 관리 동작과 그 차단 사유(MA8 SSOT). {@link #blockReason} 이 null 이면 허용, 문자열이면 <b>화면 tooltip 이자 서버 거절
 * 사유</b>다 — 상세 화면의 disabled 판정과 {@code UserManagementService} 의 가드가 이 한 메서드를 함께 부른다
 * ({@code childEnableBlockReason()} · {@code GuestServerGroup.addBlockReason()} 선례).
 *
 * <p>'마지막 최고 관리자' 규칙이 여기 없는 이유: 동작은 최고 관리자만 할 수 있고 자기 자신은 대상이 될 수 없으므로, 다른 최고
 * 관리자를 대상으로 삼는 것 자체가 두 명 이상이라는 뜻이다. 남는 구멍(동시 요청)은 서비스의 잠금 재확인이 막는다(D-9 · D-7).</p>
 */
public enum UserAction {

	CHANGE_ROLE(true) {
		@Override
		protected String stateBlockReason(Users target) {
			return null;   // 같은 등급 재요청은 요청값이 있어야 판정되므로 levelChangeBlockReason 이 따로 본다
		}

		@Override
		protected String selfBlockReason() {
			return "자기 자신의 권한 등급은 바꿀 수 없습니다.";
		}
	},
	DISABLE(true) {
		@Override
		protected String stateBlockReason(Users target) {
			return target.enabled() ? null : "이미 비활성화된 계정입니다.";
		}

		@Override
		protected String selfBlockReason() {
			return "자기 자신은 비활성화할 수 없습니다.";
		}
	},
	ENABLE(false) {
		@Override
		protected String stateBlockReason(Users target) {
			return target.enabled() ? "이미 활성 상태인 계정입니다." : null;
		}

		@Override
		protected String selfBlockReason() {
			return null;   // 비활성 계정은 로그인할 수 없으므로 자기 자신을 활성화하는 경로가 없다
		}
	},
	RESET_PASSWORD(true) {
		@Override
		protected String stateBlockReason(Users target) {
			return target.enabled() ? null : "비활성화된 계정입니다. 먼저 활성화해야 합니다.";
		}

		@Override
		protected String selfBlockReason() {
			return "본인 비밀번호는 비밀번호 변경 화면에서 바꿔야 합니다.";
		}
	};

	/** 성공하면 대상의 열린 세션을 만료시키는가 — 권한 · 로그인 가능 여부 · 비밀번호가 바뀌는 동작이 해당한다(D-8). */
	private final boolean expiresSessions;

	UserAction(boolean expiresSessions) {
		this.expiresSessions = expiresSessions;
	}

	public boolean expiresSessions() {
		return expiresSessions;
	}

	/**
	 * @param target  동작 대상
	 * @param actorId 동작하는 최고 관리자의 id
	 * @return 막혔으면 사유 문장, 허용이면 null
	 */
	public String blockReason(Users target, Long actorId) {
		if (target.getId().equals(actorId)) {
			String self = selfBlockReason();
			if (self != null) {
				return self;
			}
		}
		return stateBlockReason(target);
	}

	/**
	 * 권한 등급 변경에서 이 등급을 고를 수 있는가(CP5 D-1) — 현재 등급과 같으면 사유. 상세 화면은 등급 선택지마다 이것을 불러
	 * 현재 등급 선택지를 disabled + tooltip 으로 그리고, 서비스는 같은 메서드로 direct POST 를 409 로 막는다(SSOT).
	 */
	public static String levelChangeBlockReason(Role current, Role requested) {
		return current == requested ? "이미 그 권한 등급입니다." : null;
	}

	protected abstract String stateBlockReason(Users target);

	protected abstract String selfBlockReason();
}
