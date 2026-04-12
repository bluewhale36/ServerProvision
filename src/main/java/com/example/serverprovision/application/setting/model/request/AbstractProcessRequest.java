package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 세팅 주문서 생성 요청에서 개별 프로세스 단계를 표현하는 다형성 추상 Request 클래스이다.
 *
 * <p>역할: 프론트엔드 Fetch API가 JSON으로 전송하는 {@code processList}의 각 항목 타입이다.
 * {@code @JsonTypeInfo} + {@code @JsonSubTypes}로 Jackson 다형성 역직렬화를 구현하여,
 * JSON의 {@code "type"} 필드 값에 따라 구현체 4종 중 하나로 역직렬화된다.
 * {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}와
 * 동일한 {@code type} 판별자 값을 사용하여 Request → Domain Model 매핑이 일관된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.dto.SettingCreateRequest}의
 * {@code processList} 필드 타입으로 사용된다. {@code POST /pxe/v1/setting/api/new} 요청 수신 시
 * Jackson이 이 추상 클래스를 통해 올바른 구현체로 역직렬화하며, 이후
 * {@link com.example.serverprovision.application.setting.service.SettingService}가
 * {@link com.example.serverprovision.application.setting.service.resolver.SettingProcessResolver}
 * 구현체에 dispatch하여 도메인 모델로 변환한다.</p>
 *
 * <p>확장 가이드: 새 프로세스 타입 추가 시 이 클래스의 {@code @JsonSubTypes}에
 * 새 {@code @JsonSubTypes.Type}을 등록한다. 동시에
 * {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}의
 * {@code @JsonSubTypes}에도 대응하는 도메인 모델 타입을 등록해야 하며,
 * {@link com.example.serverprovision.application.setting.service.resolver.SettingProcessResolver}
 * 구현체도 새로 작성해야 한다.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicUpdateRequest.class,      name = "BASIC_UPDATES"),
        @JsonSubTypes.Type(value = BasicSettingRequest.class,     name = "BASIC_SETTING"),
        @JsonSubTypes.Type(value = OSInstallationRequest.class,   name = "OS_INSTALLATION"),
        @JsonSubTypes.Type(value = OSSettingRequest.class,        name = "OS_SETTING")
})
public abstract class AbstractProcessRequest {
}
