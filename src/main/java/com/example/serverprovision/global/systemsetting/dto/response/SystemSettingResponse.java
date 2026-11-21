package com.example.serverprovision.global.systemsetting.dto.response;

import com.example.serverprovision.global.security.springsecurity.domain.Role;
import com.example.serverprovision.global.systemsetting.enums.SystemSettingKey;

import java.time.LocalDateTime;

/** 설정 카드 하나. {@code value} 가 null 이면 아직 저장되지 않음, {@code updatedAt} 은 행이 없으면 null. */
public record SystemSettingResponse(
        SystemSettingKey key,
        String label,
        String value,
        Role minRole,
        LocalDateTime updatedAt
) {
}
