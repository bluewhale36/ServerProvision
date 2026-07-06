package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.ServiceAction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * systemd 서비스 지시 항목 (리눅스 스코프). 레거시의 {@code domain/os} 소속 {@code ServiceDirective} 를
 * 계약측 record 로 정정 이식 — 실행 도메인판은 Builder/Resolver 편입 슬라이스에서 별도 정의·매핑한다.
 */
public record ServiceDirectiveRequest(

        @NotBlank(message = "서비스 이름은 필수 값입니다.")
        String name,

        ServiceAction action
) {

    @JsonCreator
    public ServiceDirectiveRequest(
            @JsonProperty("name")   String name,
            @JsonProperty("action") ServiceAction action
    ) {
        this.name = name;
        // 명시적 지정이 없으면 기존 의미(활성화)를 유지하기 위해 ENABLE 로 기본 해석한다.
        this.action = action != null ? action : ServiceAction.ENABLE;
    }
}
