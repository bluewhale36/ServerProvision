package com.example.serverprovision.execution.engine.phase;

import java.util.List;

/**
 * 준비도 판정 결과(E2-1-b) — 등급과 그 근거 문구를 함께 든다. 문구는 화면 카드와 게스트 대기
 * 스크립트가 같이 쓰므로, 사유를 등급에서 다시 추론하는 코드가 생기지 않는다.
 *
 * @param grade 등급
 * @param notes 사유 문구(READY 면 빈 목록) — 축이 여럿이면 축마다 한 줄. 화면 · 로그용
 * @param wire  게스트 콘솔 · iPXE 스크립트용 ASCII 요약 — 게스트 콘솔에는 한글 글꼴이 없다
 */
public record PhaseReadiness(ReadinessGrade grade, List<String> notes, String wire) {

    private static final PhaseReadiness READY = new PhaseReadiness(ReadinessGrade.READY, List.of(), "ok");

    public static PhaseReadiness ready() {
        return READY;
    }

    public static PhaseReadiness of(ReadinessGrade grade, List<String> notes, String wire) {
        return new PhaseReadiness(grade, List.copyOf(notes), wire);
    }

    public boolean isBlocked() {
        return grade == ReadinessGrade.BLOCKED;
    }

    /** 한 줄 요약 — 게스트 스크립트 · 로그처럼 여러 줄을 실을 수 없는 자리에서 쓴다. */
    public String summary() {
        return notes.isEmpty() ? grade.getDescription() : String.join(" / ", notes);
    }
}
