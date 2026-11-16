package com.example.serverprovision.global.security.springsecurity.dto.response;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.security.springsecurity.domain.UserAction;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 사용자 상세(MA8) — 요약 + 동작별 차단 사유 + 등급 선택지별 차단 사유. 사유는 {@code UserAction.blockReason} ·
 * {@code UserAction.levelChangeBlockReason} 결과 그대로이며, 뷰는 null 이 아니면 그 버튼 · 선택지를 disabled 로 두고 tooltip 에
 * 사유를 싣는다(서버 가드와 같은 판정 · SSOT).
 *
 * <p>뷰는 동작 · 등급을 상수 이름 문자열로 묻는다({@code ${detail.reason('DISABLE')}}) — 템플릿의 {@code T()} 평가는 devtools 재시작 뒤
 * 깨지는 사례가 있어 쓰지 않는다.</p>
 */
public record UserDetailResponse(UserSummaryResponse user, Map<UserAction, String> blockReasons, Map<Role, String> levelReasons) {

	public UserDetailResponse {
		EnumMap<UserAction, String> actions = new EnumMap<>(UserAction.class);
		actions.putAll(blockReasons);
		blockReasons = Collections.unmodifiableMap(actions);
		EnumMap<Role, String> levels = new EnumMap<>(Role.class);
		levels.putAll(levelReasons);
		levelReasons = Collections.unmodifiableMap(levels);
	}

	/** 동작이 막혔으면 사유, 허용이면 null. */
	public String reason(String actionName) {
		return blockReasons.get(UserAction.valueOf(actionName));
	}

	/** 그 등급을 고를 수 없으면 사유(현재 등급), 고를 수 있으면 null. */
	public String levelReason(String roleName) {
		return levelReasons.get(Role.valueOf(roleName));
	}
}
