package com.example.serverprovision.execution.pxeinfra.converter;

import com.example.serverprovision.execution.pxeinfra.vo.LeaseSeconds;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link LeaseSeconds} ↔ DB 컬럼(BIGINT) 매핑. {@code IpAddressConverter} 와 같은 패턴 —
 * {@code autoApply = false}(엔티티가 {@code @Convert} 로 명시), null 통과.
 */
@Converter(autoApply = false)
public class LeaseSecondsConverter implements AttributeConverter<LeaseSeconds, Long> {

    @Override
    public Long convertToDatabaseColumn(LeaseSeconds attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public LeaseSeconds convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : LeaseSeconds.of(dbData);
    }
}
