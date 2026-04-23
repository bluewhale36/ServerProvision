package com.example.serverprovision.global.job.vo;

/**
 * 백그라운드 Job 의 진행률 VO.
 * 단계 이름은 호출 측이 문자열로 결정 — Job 종류마다 다른 파이프라인 단계를 강제 Enum 화 하지 않기 위함.
 * percent 가 음수이면 "미정" 의미. 프론트엔드는 이 경우 바 없이 텍스트만 노출한다.
 */
public record JobProgress(String stageLabel, int percent, String message) {

    public static final JobProgress INITIAL = new JobProgress("대기", 0, "대기 중");
}
