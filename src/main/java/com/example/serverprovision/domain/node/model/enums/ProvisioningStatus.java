package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ProvisioningStatus {
    NEW("신규"),
    IN_PROGRESS("진행 중"),
    COMPLETED("완료"),
    FAILED("실패");

    private final String description;
}
