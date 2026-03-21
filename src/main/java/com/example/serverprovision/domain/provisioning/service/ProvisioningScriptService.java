package com.example.serverprovision.domain.provisioning.service;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.provisioning.model.strategy.ProvisioningStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProvisioningScriptService {

    private final List<ProvisioningStrategy> strategies;

    public String generateIPXEScript(ServerNode node) {
        // 할당된 세팅이 없는 경우 로컬 디스크로 부팅
        if (node.getServerSetting() == null || node.getServerSetting().getSettingProcess() == null) {
            return generateLocalBootScript();
        }

        List<AbstractSettingProcess> processes = node.getServerSetting().getSettingProcess().processList();
        int currentIndex = node.getCurrentStepIndex();

        // 모든 프로세스가 끝난 경우
        if (currentIndex >= processes.size()) {
            return generateLocalBootScript();
        }

        AbstractSettingProcess currentProcess = processes.get(currentIndex);

        ProvisioningStrategy selectedStrategy = strategies.stream()
                .filter(strategy -> strategy.supports(currentProcess))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 프로세스 타입입니다: " + currentProcess.getClass().getSimpleName()));

        return selectedStrategy.generateIPXEScript(node, currentProcess);
    }

    private String generateLocalBootScript() {
        return """
               #!ipxe
               echo [Provisioning Portal] Booting from Local Disk...
               sanboot --no-describe --drive 0x80
               """;
    }
}