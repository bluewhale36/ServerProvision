package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/**
 * 펌웨어 설정 축(E3-2 D-2 — E3-1 D-10 이 예고한 승격) — 같은 phase 안에서 BIOS 다음 BMC 순으로 밟는다.
 * 선언 순서가 곧 실행 순서이고, 축이 자기 step 을 들어 소비처는 축 이름으로 분기하지 않는다(E2-2 {@code FirmwareAxis} 결).
 */
@RequiredArgsConstructor
@Getter
public enum SettingAxis {

    BIOS(ProvisioningPhaseStep.BIOS_SETTING),
    BMC(ProvisioningPhaseStep.BMC_SETTING);

    private final ProvisioningPhaseStep step;

    /** 다음 축 — 없으면 phase 완주. */
    public Optional<SettingAxis> next() {
        int index = ordinal() + 1;
        return index < values().length ? Optional.of(values()[index]) : Optional.empty();
    }

    /** 커서 step 이 가리키는 축 — 설정 phase 밖 step 이면 empty. */
    public static Optional<SettingAxis> of(ProvisioningPhaseStep step) {
        return Arrays.stream(values()).filter(axis -> axis.step == step).findFirst();
    }
}
