package com.example.serverprovision.execution.engine;

/**
 * 한 축(BIOS · BMC)의 해석 결과(E2-1-b). 선택됐으면 무엇이 선택됐는지를, 아니면 왜 그런지를 든다.
 *
 * @param outcome    결과 — 사유가 있으면 사유가 정한다(분기 대신 enum 보유값)
 * @param firmwareId 선택된 자원 id (SELECTED 만)
 * @param display    표시명 (SELECTED 만) — 예 "F27"
 * @param reason     사유 (SELECTED 는 null)
 */
public record AxisResolution(AxisOutcome outcome, Long firmwareId, String display, FirmwareAxisReason reason) {

    public enum AxisOutcome { SELECTED, SKIPPED, BLOCKED }

    public static AxisResolution selected(Long firmwareId, String display) {
        return new AxisResolution(AxisOutcome.SELECTED, firmwareId, display, null);
    }

    /** 사유로부터 결과 등급을 받는다 — 사유와 등급이 어긋날 자리를 없앤다. */
    public static AxisResolution of(FirmwareAxisReason reason) {
        return new AxisResolution(reason.getOutcome(), null, null, reason);
    }

    public boolean isSelected() {
        return outcome == AxisOutcome.SELECTED;
    }

    public boolean isBlocked() {
        return outcome == AxisOutcome.BLOCKED;
    }

    /** 화면 · 스크립트가 쓰는 한 줄. */
    public String message(String axisLabel) {
        return isSelected() ? axisLabel + " " + display + " 적용 예정" : axisLabel + " — " + reason.getUserMessage();
    }
}
