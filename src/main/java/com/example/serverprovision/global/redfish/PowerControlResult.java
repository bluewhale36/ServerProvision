package com.example.serverprovision.global.redfish;

import com.example.serverprovision.global.ui.enums.FlashTone;

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

    private static final String RETRY_SENT =
            "재시도를 접수했습니다. 서버가 네트워크로 부팅하면 이어서 진행합니다.";
    private static final String RETRY_POWER_FAILED =
            "재시도는 접수했지만 전원 제어에 실패해 서버가 스스로 다시 부팅하지 않습니다. %s 서버를 직접 켜거나 원인을 고친 뒤 [재시도 후 전원 켜기] 를 다시 눌러야 합니다.";

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
                "BMC 미검출: 원격 전원 제어를 쓸 수 없습니다.");
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

    /** 전원 명령이 전달되지 않았다 — BMC 미검출이거나 실패. */
    private boolean powerFailed() {
        return kind == Kind.UNSUPPORTED || kind == Kind.FAILED;
    }

    /** [재시도 후 전원 켜기] 의 페이지 맨 위 알림 문장(S22-3, HF24 병합 뒤 부트 지정 관찰 제거). */
    public String retryBootNotice() {
        return powerFailed() ? RETRY_POWER_FAILED.formatted(message) : RETRY_SENT;
    }

    /** 위 알림의 색 — 전원 실패는 빨강, 전달은 초록. */
    public FlashTone retryBootTone() {
        return powerFailed() ? FlashTone.DANGER : FlashTone.SUCCESS;
    }
}
