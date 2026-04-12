package com.example.serverprovision.application.setting.converter;

import com.example.serverprovision.application.setting.model.SettingProcess;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link SettingProcess}와 DB 컬럼 JSON 문자열 간 직렬화/역직렬화를 담당하는 JPA {@code AttributeConverter}이다.
 *
 * <p>역할: {@link ServerSetting}의 {@code process} 컬럼에 {@link SettingProcess}를
 * JSON 문자열로 저장하고, 조회 시 다시 {@link SettingProcess}로 복원한다.
 * {@code @Converter(autoApply = true)}로 선언되어 {@link SettingProcess} 타입 필드에
 * 자동 적용된다. Jackson 3 런타임({@code tools.jackson.databind.ObjectMapper})을 사용한다.</p>
 *
 * <p>유스케이스: {@link ServerSetting#getSettingProcess()}가 {@link SettingProcess} 타입이므로
 * JPA가 엔티티를 저장할 때 이 Converter의 {@link #convertToDatabaseColumn}을 호출하고,
 * 조회 시 {@link #convertToEntityAttribute}를 호출한다.
 * 역직렬화 시 {@link SettingProcess} 내부의 {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}
 * 구현체는 JSON의 {@code "type"} 필드 판별자로 선택되며, 이는
 * {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}의
 * {@code @JsonSubTypes}에 등록된 4종({@code BasicUpdate}, {@code BasicSetting},
 * {@code OSInstallation}, {@code OSSetting}) 중 하나이다.</p>
 *
 * <p>확장 가이드: 새 {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}
 * 구현체를 추가할 때 이 Converter 자체는 수정할 필요가 없다.
 * {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}의
 * {@code @JsonSubTypes}에 새 타입을 등록하는 것으로 충분하다.
 * {@code ObjectMapper}는 Spring 컨텍스트에서 주입받으므로, 애플리케이션의 공통 Jackson 설정
 * (예: 날짜 포맷, 다형성 설정)이 이 Converter에도 자동으로 적용된다.</p>
 */
@Converter(autoApply = true)
@RequiredArgsConstructor
public class SettingProcessConverter implements AttributeConverter<SettingProcess, String> {

    private final ObjectMapper objectMapper;

    /**
     * {@link SettingProcess}를 DB 저장용 JSON 문자열로 직렬화한다.
     *
     * @param attribute 직렬화할 {@link SettingProcess}. {@code null}이면 {@code null}을 반환한다.
     * @return JSON 문자열. {@code attribute}가 {@code null}이면 {@code null}.
     * @throws IllegalArgumentException Jackson 직렬화 실패 시
     */
    @Override
    public String convertToDatabaseColumn(SettingProcess attribute) {
        if (attribute == null) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to serialize SettingProcess", e);
        }
    }

    /**
     * DB의 JSON 문자열을 {@link SettingProcess}로 역직렬화한다.
     *
     * @param dbData DB에서 읽은 JSON 문자열. {@code null} 또는 빈 문자열이면 {@code null}을 반환한다.
     * @return 역직렬화된 {@link SettingProcess}. {@code dbData}가 {@code null}/빈 문자열이면 {@code null}.
     * @throws IllegalArgumentException Jackson 역직렬화 실패 시 (JSON 형식 오류 또는 알 수 없는 {@code type})
     */
    @Override
    public SettingProcess convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) return null;
        try {
            return objectMapper.readValue(dbData, SettingProcess.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize SettingProcess", e);
        }
    }
}
