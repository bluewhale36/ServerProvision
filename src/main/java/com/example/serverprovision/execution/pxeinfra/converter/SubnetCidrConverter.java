package com.example.serverprovision.execution.pxeinfra.converter;

import com.example.serverprovision.execution.pxeinfra.vo.SubnetCidr;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link SubnetCidr} ↔ DB 컬럼(CIDR 문자열) 매핑. {@code IpAddressConverter} 와 같은 패턴 —
 * {@code autoApply = false}(엔티티가 {@code @Convert} 로 명시), null 통과.
 */
@Converter(autoApply = false)
public class SubnetCidrConverter implements AttributeConverter<SubnetCidr, String> {

    @Override
    public String convertToDatabaseColumn(SubnetCidr attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public SubnetCidr convertToEntityAttribute(String dbData) {
        return (dbData == null || dbData.isBlank()) ? null : SubnetCidr.of(dbData);
    }
}
