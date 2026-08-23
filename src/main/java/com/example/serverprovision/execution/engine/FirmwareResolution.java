package com.example.serverprovision.execution.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 펌웨어 갱신 phase 의 해석 결과(E2-1-b) — 축 둘의 결과를 묶고, phase 등급을 그로부터 파생한다.
 * 저장하지 않고 매 폴링 다시 계산한다: 판정과 소비 사이에 자원 세계가 움직이는 시간차 문제를
 * 저장본 무효화가 아니라 재계산으로 없애기 위함이다(토론 §3 첫째 사실).
 */
public record FirmwareResolution(AxisResolution bios, AxisResolution bmc) {

    /**
     * phase 등급 — 차단이 하나라도 있으면 BLOCKED(오염된 재료로는 시작하지 않는다),
     * 그 외에 건너뛰는 축이 있으면 DEGRADED, 둘 다 선택됐으면 READY.
     */
    public ReadinessGrade grade() {
        if (bios.isBlocked() || bmc.isBlocked()) {
            return ReadinessGrade.BLOCKED;
        }
        return (bios.isSelected() && bmc.isSelected()) ? ReadinessGrade.READY : ReadinessGrade.DEGRADED;
    }

    /** 등급의 근거 — 결손 축만 싣는다(READY 면 빈 목록). */
    public List<String> notes() {
        List<String> notes = new ArrayList<>();
        if (!bios.isSelected()) {
            notes.add(bios.message("BIOS"));
        }
        if (!bmc.isSelected()) {
            notes.add(bmc.message("BMC"));
        }
        return List.copyOf(notes);
    }

    public PhaseReadiness toReadiness() {
        return PhaseReadiness.of(grade(), notes(), wireSummary());
    }

    /**
     * 게스트 콘솔 · iPXE 스크립트에 실을 ASCII 요약 — 선택됐으면 버전, 아니면 사유 코드.
     * 사용자 문구(한글)는 화면 몫이고, 게스트 콘솔에는 글꼴이 없어 코드로 싣는다.
     */
    public String wireSummary() {
        return "BIOS=" + wire(bios) + " BMC=" + wire(bmc);
    }

    private static String wire(AxisResolution axis) {
        return axis.isSelected() ? axis.display() : axis.reason().name();
    }
}
