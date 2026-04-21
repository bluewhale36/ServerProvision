package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.ServiceAction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * OS 후처리 단계에서 특정 systemd 서비스에 대해 수행할 단일 지시(directive).
 *
 * <p>{@link RHELBasedSetting#getServices()} 의 원소 타입이자, 프론트엔드 요청 DTO 에서도
 * 동일 구조로 수신된다. 서비스 이름의 shell-safe 여부 검증은 {@code RHELOSSettingBuilder}
 * 에서 수행한다.</p>
 *
 * @param name   systemctl 대상 서비스 이름 (예: {@code nginx}, {@code firewalld})
 * @param action 수행할 동작({@link ServiceAction#ENABLE} / {@link ServiceAction#DISABLE})
 */
public record ServiceDirective(
        @NotBlank(message = "서비스 이름은 필수 값입니다.")
        String name,
        @NotNull(message = "서비스 동작은 필수 값입니다.")
        ServiceAction action
) {

    @JsonCreator
    public ServiceDirective(
            @JsonProperty("name")   String name,
            @JsonProperty("action") ServiceAction action
    ) {
        this.name   = name;
        // 명시적 지정이 없으면 기존 의미를 유지하기 위해 ENABLE 로 기본 해석한다.
        this.action = action != null ? action : ServiceAction.ENABLE;
    }
}
