package com.example.serverprovision.global.registration;

import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;

import java.util.UUID;

/**
 * 등록 실패의 처분 — 후처리 실행기가 실패 발생 시 취할 행동. sealed 3-case 로 exhaustive 분기(switch 패턴매칭).
 *
 * <ul>
 *   <li>{@link Cleanup} — 콘텐츠/영구 실패(해시 불일치·중복·파일명 충돌 등). 결정적 실패라 재시도가 무의미하고
 *       finalize 가 이미 업로드 파일을 정리했으므로, 격리하지 않고 단순 fail.</li>
 *   <li>{@link Nudge} — soft-deleted/Deprecated 자원과 해시 충돌(사용자 결정 대기). 임시 파일을 보존하고
 *       fail 메시지에 nudge marker 를 동봉한다(confirm 시 정식 등록에 재사용).</li>
 *   <li>{@link Quarantine} — 인프라/일시 실패(저장 IO · DB 제약 · 마커 기록). 파일을 삭제하지 않고 active 트리 밖으로
 *       격리하고 durable 복구 레코드를 남긴다(재시도/폐기 가능).</li>
 * </ul>
 *
 * <p>plan(CP1) 의 {@code Preserve} 를 {@code Nudge(nudgeId)} 로 구체화 — nudge 가 marker 에 nudgeId 를 필요로 하므로
 * 처분이 그 데이터를 직접 보유하면 실행기의 전용 nudge catch 가 사라지고 dispatch 가 완전히 균일해진다.</p>
 */
public sealed interface FailureDisposition
		permits FailureDisposition.Cleanup, FailureDisposition.Nudge, FailureDisposition.Quarantine {

	/** 업로드 파일 정리 후 단순 fail. (재시도 무의미한 결정적 실패) */
	record Cleanup() implements FailureDisposition {
	}

	/**
	 * nudge 결정 대기 — 임시 파일 보존 + nudge marker(nudgeId).
	 * <p>nudgeId 는 {@code NudgeRequiredResponse.nudgeId()} 와 동일하게 {@code UUID} (SSOT) — 처분을 경유해도
	 * lossy 변환이 없도록 타입을 보존한다. {@code String} 직렬화는 Runner 의 fail 메시지 조립
	 * ({@code NUDGE_REQUIRED:{uuid}}) 경계에서만 일어난다.</p>
	 */
	record Nudge(UUID nudgeId) implements FailureDisposition {
	}

	/** 인프라/일시 실패 — 파일 보존 + 격리 + durable 복구 레코드. */
	record Quarantine(OrphanFailureClass failureClass) implements FailureDisposition {
	}

	/** 무상태 Cleanup 싱글턴 (불필요한 인스턴스 생성 회피). */
	Cleanup CLEANUP = new Cleanup();
}
