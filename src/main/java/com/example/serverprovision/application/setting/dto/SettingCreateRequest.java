package com.example.serverprovision.application.setting.dto;

import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 세팅 주문서 생성 요청을 담는 최상위 Request DTO이다.
 *
 * <p>역할: {@code POST /pxe/v1/setting/api/new}의 요청 본문 타입이다.
 * 주문서 명칭({@code name})과 하나 이상의 프로세스 단계 요청 목록({@code processList})을 포함한다.</p>
 *
 * <p>유스케이스: 프론트엔드 JS가 폼 데이터를 JSON으로 조립하여 이 구조로 전송하면,
 * {@link com.example.serverprovision.application.setting.controller.SettingController#createSetting}이
 * {@code @Valid @RequestBody}로 수신하여 Bean Validation을 수행한다.
 * {@code processList}의 각 항목은 {@link AbstractProcessRequest}의 {@code @JsonSubTypes}에 따라
 * {@code "type"} 필드 값으로 구현체를 결정하여 역직렬화된다.
 * 이후 {@link com.example.serverprovision.application.setting.service.SettingService#save}에
 * 전달되어 {@link com.example.serverprovision.application.setting.domain.entity.ServerSetting}으로 변환·저장된다.</p>
 *
 * <p>확장 가이드: 주문서 레벨의 메타데이터(예: 설명, 적용 대상 서버 그룹)가 필요하면
 * 이 레코드에 필드를 추가하고, {@link com.example.serverprovision.application.setting.service.SettingService}와
 * {@link com.example.serverprovision.application.setting.domain.entity.ServerSetting}에도
 * 반영한다.</p>
 */
public record SettingCreateRequest(

        /** 세팅 주문서의 식별 명칭이다. DB의 {@code server_setting.name} 컬럼에 저장된다. */
        @NotBlank(message = "주문서 명칭은 필수 입력값입니다.")
        String name,

        /**
         * 이 주문서에 포함할 프로비저닝 단계 요청 목록이다.
         * 각 항목은 {@link AbstractProcessRequest} 구현체이며, Jackson이 {@code "type"} 판별자로
         * 올바른 구현체를 선택하여 역직렬화한다. {@code @Valid}로 각 항목에도 Bean Validation이 적용된다.
         */
        @Valid
        @NotEmpty(message = "하나 이상의 프로비저닝 단계를 선택해야 합니다.")
        List<AbstractProcessRequest> processList
) {
}
