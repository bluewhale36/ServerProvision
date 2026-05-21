package com.example.serverprovision.management.common.nudge;

import java.util.Map;

/**
 * MK2 WAVE 2 — 단계 A (intent) nudge payload.
 *
 * <p>파일이 아직 업로드되지 않은 상태에서 메타 키 (boardId / osImageId, version, kind 등) 충돌이
 * 사전 검출됐을 때 사용. confirm 시 도메인 NudgeService 가 본 payload 의 {@link #attributes} 를 읽어
 * 새 intent token 을 발급해서 클라이언트에 반환한다 (클라이언트는 그 token 으로 정식 업로드 시작).</p>
 *
 * <p>임시 파일이 없으므로 cancel 시 cleanup 책임도 없음. 단순히 nudge 세션 제거만 수행.</p>
 *
 * @param attributes 도메인별 intent 재발급에 필요한 모든 메타 (도메인 NudgeService 가 정의한 키 어휘로 보관)
 */
public record IntentMetaNudgePayload(
		Map<String, String> attributes
) implements NudgePayload {

}
