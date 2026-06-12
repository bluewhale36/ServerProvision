package com.example.serverprovision.management.os.enums;

/**
 * 격리된 오펀 ISO 의 복구 상태.
 * <ul>
 *   <li>{@link #PENDING} — 격리됨, 사용자 결정 대기 (재시도/폐기 가능).</li>
 *   <li>{@link #RECOVERED} — 재시도가 트리거됨 (파일 원위치 복원 + 재등록 job 시작). 행은 소비됨.</li>
 *   <li>{@link #DISCARDED} — 사용자가 폐기 (격리 파일 삭제).</li>
 * </ul>
 */
public enum OrphanRecoveryState {
	PENDING,
	RECOVERED,
	DISCARDED
}
