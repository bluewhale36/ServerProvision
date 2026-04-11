package com.example.serverprovision.application.setting.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * {@link AbstractSettingProcess} 목록을 감싸는 래퍼 레코드이다.
 *
 * <p>역할: {@link com.example.serverprovision.application.setting.domain.entity.ServerSetting}의
 * {@code process} DB 컬럼에 JSON 문자열로 저장되는 단위이다.
 * {@link com.example.serverprovision.application.setting.converter.SettingProcessConverter}가
 * JPA {@code AttributeConverter}로서 {@link tools.jackson.databind.ObjectMapper}를 통해
 * 이 객체를 JSON으로 직렬화하고 다시 역직렬화한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService#save}에서
 * {@code new SettingProcess(resolvedProcesses)}로 생성되며, 이후 {@link ServerSetting} 빌더에
 * {@code settingProcess} 필드로 전달된다. Jackson 역직렬화 시 {@code @JsonCreator}로
 * 표시된 생성자가 호출된다. {@code processList}는 {@code @NotEmpty}로 빈 목록을 방지한다.</p>
 *
 * <p>확장 가이드: {@code processList} 외에 세팅 프로세스 수준의 메타데이터(예: 전체 예상 시간)가
 * 필요하면 이 레코드에 필드를 추가하고 {@code @JsonCreator} 생성자 파라미터에도 함께 반영한다.
 * DB 컬럼({@code process}) 타입은 {@code TEXT} 또는 {@code JSON}으로 충분한 크기를 확보해야 한다.</p>
 */
public record SettingProcess(
        @NotEmpty(message = "하나 이상의 단계를 선택해야 합니다.") List<AbstractSettingProcess> processList
) {

    @JsonCreator
    public SettingProcess(
            @JsonProperty("processList")
            List<AbstractSettingProcess> processList
    ) {
        this.processList = processList;
    }
}
