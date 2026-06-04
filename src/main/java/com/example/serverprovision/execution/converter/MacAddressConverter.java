package com.example.serverprovision.execution.converter;

import com.example.serverprovision.execution.vo.MacAddressVO;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link MacAddressVO} ↔ DB 컬럼(정규화된 17자 문자열) 매핑.
 * VO 가 {@code @Embeddable} 이 아닌 순수 application VO 이므로, 단일 컬럼 매핑을 컨버터가 담당한다.
 * <p>DB → VO 경로에서 {@link MacAddressVO#of}(검증 + 정규화)를 거치므로, 외부 변조로 비정규 값이
 * 들어와 있어도 로드 시점에 거절된다.</p>
 */
@Converter(autoApply = false)
public class MacAddressConverter implements AttributeConverter<MacAddressVO, String> {

	@Override
	public String convertToDatabaseColumn(MacAddressVO attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public MacAddressVO convertToEntityAttribute(String dbData) {
		return (dbData == null || dbData.isBlank()) ? null : MacAddressVO.of(dbData);
	}
}
