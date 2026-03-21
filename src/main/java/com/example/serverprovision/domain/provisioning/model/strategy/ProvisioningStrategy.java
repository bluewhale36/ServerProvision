package com.example.serverprovision.domain.provisioning.model.strategy;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.domain.node.entity.ServerNode;

public interface ProvisioningStrategy {
    boolean supports(AbstractSettingProcess process);
    String generateIPXEScript(ServerNode node, AbstractSettingProcess process);
}
