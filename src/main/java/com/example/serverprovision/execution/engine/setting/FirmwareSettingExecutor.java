package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.boot.IpxeScripts;
import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import org.springframework.stereotype.Component;

/**
 * 펌웨어 설정 phase 실행기(E3-1) — 빈 등록만으로 dispatch 의 HOLD 행이 위임으로 바뀐다(DEC-6). 게스트에게는
 * 대기 스크립트를 주고, 실제 일(PATCH · 재부팅 · readback)은 {@link BiosSettingWorker} 가 BMC 로 한다.
 * 준비도는 default(준비됨) — 목표의 유무 · 보드 일치는 워커의 행 판정(2 · 3)이 가린다.
 */
@Component
public class FirmwareSettingExecutor implements ProvisioningPhaseExecutor {

    @Override
    public ProvisioningPhase phase() {
        return ProvisioningPhase.FIRMWARE_SETTING;
    }

    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        return IpxeScripts.awaitingBiosSetting(rebootQuery);
    }
}
