package com.example.serverprovision.execution.engine.firmware;

/**
 * 한 축(BIOS · BMC)의 해석 결과(E2-1-b). 선택됐으면 무엇이 선택됐는지를, 아니면 왜 그런지를 든다.
 *
 * @param outcome    결과 — 사유가 있으면 사유가 정한다(분기 대신 enum 보유값)
 * @param firmwareId 선택된 자원 id (SELECTED 만)
 * @param display    표시명 (SELECTED 만) — 예 "F27"
 * @param imagePath  굽을 파일의 서버 로컬 경로 (SELECTED 만) — 집행이 이 파일을 HTTP 로 내주고
 *                   BMC 가 당겨 간다(E2-2 D-5). 판정 시점에 존재를 이미 확인했으므로 그때 함께 싣는다
 * @param reason     사유 (SELECTED 는 null)
 */
public record AxisResolution(AxisOutcome outcome, Long firmwareId, String resourceName, String display,
                             String imagePath, FirmwareAxisReason reason) {

    public enum AxisOutcome { SELECTED, SKIPPED, BLOCKED }

    /** 이름 없는 변형 — 구 호출부 · 테스트 호환(화면은 버전만 표기). */
    public static AxisResolution selected(Long firmwareId, String display, String imagePath) {
        return selected(firmwareId, null, display, imagePath);
    }

    /** 자원 이름을 함께 싣는 변형(E2-4 R7) — 화면 표기는 "이름 (버전)", 대조 재료는 버전 그대로. */
    public static AxisResolution selected(Long firmwareId, String resourceName, String display, String imagePath) {
        return new AxisResolution(AxisOutcome.SELECTED, firmwareId, resourceName, display, imagePath, null);
    }

    /** 사유로부터 결과 등급을 받는다 — 사유와 등급이 어긋날 자리를 없앤다. */
    public static AxisResolution of(FirmwareAxisReason reason) {
        return new AxisResolution(reason.getOutcome(), null, null, null, null, reason);
    }

    /** 표시 라벨(E2-4 R7) — "이름 (버전)". 이름이 없으면 버전만(구 데이터 호환). */
    public String displayLabel() {
        return resourceName == null ? display : resourceName + " (" + display + ")";
    }

    public boolean isSelected() {
        return outcome == AxisOutcome.SELECTED;
    }

    public boolean isBlocked() {
        return outcome == AxisOutcome.BLOCKED;
    }

    /** 화면 · 스크립트가 쓰는 한 줄 — 선택이면 "이름 (버전)" 표기(E2-4 R7). */
    public String message(String axisLabel) {
        return isSelected() ? axisLabel + " " + displayLabel() + " 적용 예정" : axisLabel + " — " + reason.getUserMessage();
    }
}
