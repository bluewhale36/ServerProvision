package com.example.serverprovision.global.security.springsecurity.domain;

import com.example.serverprovision.global.security.springsecurity.entity.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** MA8 — 사용자 관리 동작의 차단 사유 SSOT(화면 disabled 와 서버 가드가 함께 쓰는 판정). */
class UserActionTest {

	private static final Long ACTOR = 1L;

	private static Users user(long id, boolean enabled) {
		return Users.builder().id(id).username("u" + id).name("이름").isEnabled(enabled).isLocked(false).build();
	}

	@Test
	@DisplayName("다른 활성 사용자 — 비활성화 · 권한 변경 · 비밀번호 지정 허용(null), 활성화는 '이미 활성'")
	void otherEnabledUser() {
		Users target = user(2, true);
		assertThat(UserAction.CHANGE_ROLE.blockReason(target, ACTOR)).isNull();
		assertThat(UserAction.DISABLE.blockReason(target, ACTOR)).isNull();
		assertThat(UserAction.RESET_PASSWORD.blockReason(target, ACTOR)).isNull();
		assertThat(UserAction.ENABLE.blockReason(target, ACTOR)).isEqualTo("이미 활성 상태인 계정입니다.");
	}

	@Test
	@DisplayName("다른 비활성 사용자 — 활성화만 허용 · 비활성화는 '이미 비활성' · 비밀번호 지정은 '먼저 활성화'")
	void otherDisabledUser() {
		Users target = user(2, false);
		assertThat(UserAction.ENABLE.blockReason(target, ACTOR)).isNull();
		assertThat(UserAction.DISABLE.blockReason(target, ACTOR)).isEqualTo("이미 비활성화된 계정입니다.");
		assertThat(UserAction.RESET_PASSWORD.blockReason(target, ACTOR)).isEqualTo("비활성화된 계정입니다. 먼저 활성화해야 합니다.");
		assertThat(UserAction.CHANGE_ROLE.blockReason(target, ACTOR)).isNull();
	}

	@Test
	@DisplayName("자기 자신 — 권한 변경 · 비활성화 · 비밀번호 지정 모두 자기 보호 사유(상태 사유보다 먼저)")
	void self() {
		Users me = user(ACTOR, true);
		assertThat(UserAction.CHANGE_ROLE.blockReason(me, ACTOR)).isEqualTo("자기 자신의 권한 등급은 바꿀 수 없습니다.");
		assertThat(UserAction.DISABLE.blockReason(me, ACTOR)).isEqualTo("자기 자신은 비활성화할 수 없습니다.");
		assertThat(UserAction.RESET_PASSWORD.blockReason(me, ACTOR)).isEqualTo("본인 비밀번호는 비밀번호 변경 화면에서 바꿔야 합니다.");
		assertThat(UserAction.ENABLE.blockReason(me, ACTOR)).isEqualTo("이미 활성 상태인 계정입니다.");   // 자기 보호 없음 → 상태 사유
	}

	@Test
	@DisplayName("등급 선택지(CP5 D-1) — 현재 등급과 같으면 사유, 다르면 null")
	void levelChangeBlockReason() {
		assertThat(UserAction.levelChangeBlockReason(Role.ADMIN, Role.ADMIN)).isEqualTo("이미 그 권한 등급입니다.");
		assertThat(UserAction.levelChangeBlockReason(Role.ADMIN, Role.SUPER_ADMIN)).isNull();
		assertThat(UserAction.levelChangeBlockReason(Role.ADMIN, Role.USER)).isNull();
	}

	@Test
	@DisplayName("세션 만료 대상 — 권한 변경 · 비활성화 · 비밀번호 지정은 만료, 활성화는 아니다")
	void expiresSessions() {
		assertThat(UserAction.CHANGE_ROLE.expiresSessions()).isTrue();
		assertThat(UserAction.DISABLE.expiresSessions()).isTrue();
		assertThat(UserAction.RESET_PASSWORD.expiresSessions()).isTrue();
		assertThat(UserAction.ENABLE.expiresSessions()).isFalse();
	}
}
