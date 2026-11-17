package com.example.serverprovision.global.redfish;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * {@code ComputerSystem.PowerState} — 원시 String 을 밖으로 내보내지 않기 위한 타입(E1.5, Primitive Obsession 금지).
 * 실측 관찰값은 On · Off 지만 표준에 과도 상태가 있어 함께 담고, 모르는 값은 {@link #UNKNOWN} 으로 눕힌다.
 */
@RequiredArgsConstructor
@Getter
public enum RedfishPowerState {

    ON("On", "켜짐"),
    OFF("Off", "꺼짐"),
    POWERING_ON("PoweringOn", "켜는 중"),
    POWERING_OFF("PoweringOff", "끄는 중"),
    UNKNOWN(null, "미확인");

    private final String wireValue;
    /** 화면에 보이는 이름(S22-3) — 결과 문장이 상수 원문 대신 이 이름을 쓴다. */
    private final String description;

    public static RedfishPowerState of(String wire) {
        if (wire == null) {
            return UNKNOWN;
        }
        for (RedfishPowerState state : values()) {
            if (wire.equals(state.wireValue)) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
