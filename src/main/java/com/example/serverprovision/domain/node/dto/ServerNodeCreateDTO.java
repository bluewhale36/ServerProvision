package com.example.serverprovision.domain.node.dto;

import com.example.serverprovision.domain.node.model.enums.Vendor;
import lombok.Builder;

@Builder
public record ServerNodeCreateDTO(
        String macAddress,
        String ipmiIp,
        String ipmiUser,
        String ipmiPassword,
        String hostname,
        String assignedIp,
        Vendor vendor,
        Long boardModelId
) {
}
