package com.example.serverprovision.global.redfish;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * {@code ComputerSystem.Reset} 의 ResetType — E0-4 실측 허용값 5 종(조사값의 GracefulRestart 는 부재).
 * wire 값을 상수가 보유하고, 화면 confirm 필요 여부({@link #destructive()})도 여기가 단일 소스다(E1.5 D5).
 */
@RequiredArgsConstructor
@Getter
public enum RedfishResetType {

    ON("On", "켜기"),
    FORCE_OFF("ForceOff", "강제 끄기"),
    FORCE_RESTART("ForceRestart", "재시작"),
    GRACEFUL_SHUTDOWN("GracefulShutdown", "정상 종료"),
    /** 켜짐 검증({@code RedfishPowerService.powerOnAndVerify})의 폴백 전용 — 화면 · REST 로는 쓰지 않는다(OQ1 확정). */
    POWER_CYCLE("PowerCycle", "전원 재투입");

    private final String wireValue;
    private final String displayName;

    /** 전원을 내리거나 껐다 켜는 액션 전부 — 화면이 confirm 을 거치는 근거(ON 만 예외). */
    public boolean destructive() {
        return this != ON;
    }
}
