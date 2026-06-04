package com.example.serverprovision.execution.converter;

import com.example.serverprovision.execution.vo.IpAddressVO;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link IpAddressVO} ↔ DB 컬럼(IPv4 문자열) 매핑.
 * VO 가 {@code @Embeddable} 이 아닌 순수 application VO 이므로, 단일 컬럼 매핑을 컨버터가 담당한다.
 * <p>{@code lan_ip} 는 nullable 이므로 null 을 그대로 통과시킨다.
 * DB → VO 경로에서 {@link IpAddressVO#of}(IPv4 검증)를 거친다.</p>
 */
@Converter(autoApply = false)
public class IpAddressConverter implements AttributeConverter<IpAddressVO, String> {

	@Override
	public String convertToDatabaseColumn(IpAddressVO attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public IpAddressVO convertToEntityAttribute(String dbData) {
		return (dbData == null || dbData.isBlank()) ? null : IpAddressVO.of(dbData);
	}
}
