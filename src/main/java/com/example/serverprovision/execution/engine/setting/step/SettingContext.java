package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.engine.setting.BmcSettingTarget;
import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.global.redfish.RedfishTarget;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 설정 적용 한 주기의 재료(E3-1 · E3-2) — 행 판정({@code matches})은 이것만 보고 답한다. {@code target}(BIOS) 이
 * null 이면 창 밖(활성 할당 없음), 비어 있으면 적용할 BIOS 설정이 없다. {@code bmcTarget} 은 표준이라 늘 있다.
 * {@code axis} 는 커서 step 에서 파생한다 — 두 축이 한 진리표를 쓰므로 행마다 자기 축을 확인한다(E3-2 D-2).
 */
public record SettingContext(
        GuestServer server,
        ProvisioningProgress progress,
        GuestServerDetail detail,
        List<ProvisioningHistory> history,
        BiosSettingTarget target,
        BmcSettingTarget bmcTarget,
        FirmwareUpdateProvider provider,
        LocalDateTime now
) {

    public SettingAxis axis() {
        return SettingAxis.of(progress.getCurrentStep()).orElse(null);
    }

    public boolean bmcDetected() {
        return detail != null && detail.getBmcIp() != null && provider != null;
    }

    public RedfishTarget redfishTarget() {
        return new RedfishTarget(detail == null || detail.getBmcIp() == null ? null : detail.getBmcIp().value(),
                detail == null ? null : detail.getBoardSerial());
    }

    /** 현재 축의 열린 행 — 있으면 착수 뒤(재부팅 · 재접속 전이거나 후)다. 최신 행 하나만 의미가 있다. */
    public Optional<ProvisioningHistory> runningRow() {
        SettingAxis axis = axis();
        if (axis == null) {
            return Optional.empty();
        }
        return history.stream()
                .filter(h -> h.getStepCode() == axis.getStep() && h.getStatus() == ProvisioningStatus.RUNNING)
                .reduce((first, second) -> second);
    }
}
