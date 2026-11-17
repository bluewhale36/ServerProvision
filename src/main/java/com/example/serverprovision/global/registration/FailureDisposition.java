package com.example.serverprovision.global.registration;

import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;

/**
 * 등록 실패의 처분 — 후처리 실행기가 실패 발생 시 취할 행동. sealed 2-case 로 exhaustive 분기(switch 패턴매칭).
 *
 * <ul>
 *   <li>{@link Cleanup} — 콘텐츠/영구 실패(해시 불일치·중복·파일명 충돌 등). 결정적 실패라 재시도가 무의미하고
 *       finalize 가 이미 업로드 파일을 정리했으므로, 격리하지 않고 단순 fail.</li>
 *   <li>{@link Quarantine} — 인프라/일시 실패(저장 IO · DB 제약 · 마커 기록). 파일을 삭제하지 않고 active 트리 밖으로
 *       격리하고 durable 복구 레코드를 남긴다(재시도/폐기 가능).</li>
 * </ul>
 *
 * <p>HF30-2 — 휴지통 자원과의 해시 충돌로 사용자 결정을 기다리던 {@code Nudge} 처분은 없앴다. 그 창은 화면에 연결되지 않았고,
 * 같은 내용의 휴지통 후보는 업로드 전 중복 확인 창이 묻는다.</p>
 */
public sealed interface FailureDisposition
		permits FailureDisposition.Cleanup, FailureDisposition.Quarantine {

	/** 업로드 파일 정리 후 단순 fail. (재시도 무의미한 결정적 실패) */
	record Cleanup() implements FailureDisposition {
	}

	/** 인프라/일시 실패 — 파일 보존 + 격리 + durable 복구 레코드. */
	record Quarantine(OrphanFailureClass failureClass) implements FailureDisposition {
	}

	/** 무상태 Cleanup 싱글턴 (불필요한 인스턴스 생성 회피). */
	Cleanup CLEANUP = new Cleanup();
}
