package com.example.serverprovision.global.job.enums;

/**
 * BackgroundJob 의 단계별 상태. 프론트엔드 chunk progress bar 의 색상 매핑:
 * <ul>
 *   <li>{@code PENDING} — 아직 진행 안 됨 (grey)</li>
 *   <li>{@code RUNNING} — 현재 진행 중 (blue)</li>
 *   <li>{@code DONE} — 정상 완료 (green)</li>
 *   <li>{@code ERROR} — 이 단계에서 실패 (red)</li>
 * </ul>
 * 모든 단계가 DONE 이면 Job 자체가 COMPLETED. 한 단계라도 ERROR 면 Job 은 FAILED.
 */
public enum StageStatus {
    PENDING,
    RUNNING,
    DONE,
    ERROR
}
