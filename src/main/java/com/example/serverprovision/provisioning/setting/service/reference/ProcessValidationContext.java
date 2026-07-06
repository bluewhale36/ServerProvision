package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;

import java.util.List;
import java.util.Optional;

/**
 * 검증 컨텍스트 — 형제 단계를 봐야 하는 cross-process 영속 검증(예: SPECIFIED 보드 ↔ 템플릿
 * 보드 일치, U2-2-3 D3)을 위해 검사기에 전달된다. 대부분의 검사기는 무시한다.
 */
public record ProcessValidationContext(List<AbstractProcessRequest> processList) {

    /** 펌웨어 업데이트 단계(보드 selector 보유) — 정의서당 최대 1개(D7 UNIQUE). */
    public Optional<BasicUpdateRequest> firmwareStep() {
        return processList.stream()
                .filter(BasicUpdateRequest.class::isInstance)
                .map(BasicUpdateRequest.class::cast)
                .findFirst();
    }
}
