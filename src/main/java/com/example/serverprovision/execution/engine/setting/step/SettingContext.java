package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.global.redfish.RedfishTarget;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 설정 적용 한 주기의 재료(E3-1) — 행 판정({@code matches})은 이것만 보고 답한다. {@code target} 이 null 이면
 * 창 밖(활성 할당 없음), 비어 있으면 적용할 설정이 없다. {@code provider} 는 BMC 를 다룰 흐름(null = 미지원).
 */
public record SettingContext(
        GuestServer server,
        ProvisioningProgress progress,
        GuestServerDetail detail,
        List<ProvisioningHistory> history,
        BiosSettingTarget target,
        FirmwareUpdateProvider provider,
        LocalDateTime now
) {

    public boolean bmcDetected() {
        return detail != null && detail.getBmcIp() != null && provider != null;
    }

    public RedfishTarget redfishTarget() {
        return new RedfishTarget(detail == null || detail.getBmcIp() == null ? null : detail.getBmcIp().value(),
                detail == null ? null : detail.getBoardSerial());
    }

    /** 열려 있는 BIOS_SETTING 행 — 있으면 착수 뒤(재부팅 전이거나 후)다. 최신 행 하나만 의미가 있다. */
    public Optional<ProvisioningHistory> runningRow() {
        return history.stream()
                .filter(h -> h.getStepCode() == ProvisioningPhaseStep.BIOS_SETTING
                        && h.getStatus() == ProvisioningStatus.RUNNING)
                .reduce((first, second) -> second);
    }
}
