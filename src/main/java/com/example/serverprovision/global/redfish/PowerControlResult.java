package com.example.serverprovision.global.redfish;

/**
 * 전원 제어의 결과 — 예외가 아니라 값이다 (E1.5 D3 · 사용자 확정 P4). 전원 제어 실패는 일상 사건이라
 * 소비처(엔진 · 화면)가 다형으로 받게 하고, 컨트롤러는 이 record 를 그대로 JSON 으로 직렬화한다.
 * 사용자 문구의 SSOT 는 서버(여기) — 화면 JS 는 message 를 그대로 표기한다(U4-1-3 관례).
 */
public record PowerControlResult(
        Kind kind,
        RedfishPowerState powerState,
        String message
) {

    public enum Kind {
        /** 대상 게스트에 BMC 가 없다(bmcIp null) — 제어 자체가 성립하지 않음. */
        UNSUPPORTED,
        /** 명령이 전달됐다(검증 주장 없음) — 조회 · 단발 발행의 결과. */
        SENT,
        /** 켜짐을 폴링으로 확인했다 — {@code powerOnAndVerify} 전용. */
        VERIFIED,
        /** 실패 — 연결 불가 · 자격증명 소진 · 폴백까지 불변 등. message 가 사유를 말한다. */
        FAILED
    }

    public static PowerControlResult unsupported() {
        return new PowerControlResult(Kind.UNSUPPORTED, null,
                "BMC 미검출 — 원격 전원 제어를 쓸 수 없습니다 (진단 수집이 BMC 를 찾지 못했습니다).");
    }

    public static PowerControlResult sent(RedfishPowerState state, String message) {
        return new PowerControlResult(Kind.SENT, state, message);
    }

    public static PowerControlResult verified(String message) {
        return new PowerControlResult(Kind.VERIFIED, RedfishPowerState.ON, message);
    }

    public static PowerControlResult failed(RedfishPowerState state, String message) {
        return new PowerControlResult(Kind.FAILED, state, message);
    }
}
