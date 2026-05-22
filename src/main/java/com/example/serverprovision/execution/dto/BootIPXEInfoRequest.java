package com.example.serverprovision.execution.dto;


public record BootIPXEInfoRequest(
        String macAddress,
        String ipAddress,
        String systemUUID,
        String vendor,
        String boardModel
) {
}
