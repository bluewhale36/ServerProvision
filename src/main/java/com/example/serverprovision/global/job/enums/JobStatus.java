package com.example.serverprovision.global.job.enums;

/**
 * 백그라운드 Job 의 생명주기 상태.
 * 프론트엔드는 {@code isActive()} 로 폴링 지속 여부를 판단하고, {@code isTerminal()} 인 Job 만 닫기/자동 정리 대상이 된다.
 */
public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;

    public boolean isActive() {
        return this == PENDING || this == RUNNING;
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
