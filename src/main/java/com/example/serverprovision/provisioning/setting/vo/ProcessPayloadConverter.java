package com.example.serverprovision.provisioning.setting.vo;

import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link ProcessPayload} ↔ JSON 컬럼 — 계약 원문의 무변환 왕복.
 *
 * <p>Boot 관리 {@code ObjectMapper} 를 주입받는 Spring-managed Converter(레거시
 * {@code SettingProcessConverter} 선례 — Hibernate BeanContainer 통합). 이 mapper 에는
 * {@code @JacksonComponent ProcessRequestDeserializer}(2단 판별자 해석기)가 등록돼 있어
 * 역직렬화가 wire 계약과 동일한 경로를 탄다 — 저장본과 요청의 해석 SSOT 가 하나다.</p>
 */
@Converter(autoApply = false)
@RequiredArgsConstructor
public class ProcessPayloadConverter implements AttributeConverter<ProcessPayload, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(ProcessPayload attribute) {
        if (attribute == null) {
            return null;
        }
        // 직렬화 시 processType()/osFamily() 다형 accessor 가 판별자 속성으로 실린다(U2-1 확정 왕복 계약).
        return objectMapper.writeValueAsString(attribute.request());
    }

    @Override
    public ProcessPayload convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        // 미등록 판별자 저장본은 해석기가 명시적 입력 불일치로 거절한다 — silent 흡수 없음.
        return new ProcessPayload(objectMapper.readValue(dbData, AbstractProcessRequest.class));
    }
}
