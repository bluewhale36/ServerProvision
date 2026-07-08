package com.example.serverprovision.global.job.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 백그라운드 Job 의 분류.
 * 프론트엔드의 타입 칩 표시에 {@code displayName} 이 쓰인다.
 * 새로운 배경 작업이 추가될 때마다 여기에 상수를 하나 더하고 서비스 등록부에서 해당 타입을 사용하면 된다.
 * (ISO 업로드는 foreground XHR 로 처리하므로 Job 타입에 포함되지 않는다.)
 */
@Getter
@RequiredArgsConstructor
public enum JobType {
	ISO_REGISTRATION("ISO 등록"),
	COMPS_EXTRACTION("환경·패키지 추출"),
	REPO_INDEXING("저장소 인덱싱"),
	PATH_RECONCILIATION("자원 무결성 점검"),
	MARKER_REISSUE("마커 서명 재발급"),
	INTEGRITY_VERIFICATION("무결성 검증"),
	// S5-2-4 — 휴지통 자동 hard-delete + 사전 알림 + 실패 알림 3 종
	TTL_NOTIFY("영구삭제 임박 알림"),
	TRASH_AUTO_PURGE("자동 영구삭제"),
	TRASH_PURGE_FAILED("영구삭제 실패 (재시도 대기)");

	private final String displayName;
}
