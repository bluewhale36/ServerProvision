package com.example.serverprovision.global.systemsetting.vo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 시스템 설정 값. 키마다 모양이 다르므로 키별 구현을 두고, JSON 컬럼에는 판별자 {@code type} 과 함께 저장한다.
 * JPA 변환기는 같은 행의 {@code setting_key} 를 볼 수 없어 판별자를 JSON 안에 둔다.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProvisioningAllowedRange.class, name = "PROVISIONING_ALLOWED_RANGE")
})
public sealed interface SystemSettingValue permits ProvisioningAllowedRange {

    /** 화면에 보일 값 문자열. */
    String display();
}
