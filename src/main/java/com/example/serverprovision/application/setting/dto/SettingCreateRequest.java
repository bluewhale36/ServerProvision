package com.example.serverprovision.application.setting.dto;

import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SettingCreateRequest(

        @NotBlank(message = "주문서 명칭은 필수 입력값입니다.")
        String name,

        @Valid
        @NotEmpty(message = "하나 이상의 프로비저닝 단계를 선택해야 합니다.")
        List<AbstractProcessRequest> processList
) {
}
