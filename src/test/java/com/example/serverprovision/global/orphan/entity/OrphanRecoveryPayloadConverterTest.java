package com.example.serverprovision.global.orphan.entity;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.OrphanRecoveryPayload;
import com.example.serverprovision.management.os.service.iso.IsoRecoveryPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R1-4-4 — payload 의 polymorphic JSON 직렬화({@code @JsonTypeInfo(Id.CLASS)}) 라운드트립 검증.
 * 영속 Converter 가 도메인-로컬 구현체({@link IsoRecoveryPayload})를 global 수정 없이 복원하는지 못박는다
 * (@DataJpaTest 선례 부재로 스키마/직렬화 검증을 본 단위 테스트로 대체).
 */
class OrphanRecoveryPayloadConverterTest {

	private final OrphanRecoveryPayloadConverter converter = new OrphanRecoveryPayloadConverter();

	@Test
	@DisplayName("IsoRecoveryPayload ↔ JSON 라운드트립 — 구체 타입·필드 보존")
	void roundTrip_preservesConcreteTypeAndFields() {
		IsoRecoveryPayload original = new IsoRecoveryPayload("Rocky Linux 9.5", "abc123def456hash");

		String json = converter.convertToDatabaseColumn(original);
		assertThat(json).isNotBlank();
		// Id.CLASS 핵심 : type-id = FQCN 이어야 global 이 도메인 구현체를 무수정 복원(리네임/이동 시 회귀 잡힘).
		assertThat(json).contains("com.example.serverprovision.management.os.service.iso.IsoRecoveryPayload");

		OrphanRecoveryPayload restored = converter.convertToEntityAttribute(json);

		assertThat(restored).isInstanceOf(IsoRecoveryPayload.class);
		assertThat(restored.resourceType()).isEqualTo(ResourceType.OS_ISO);
		IsoRecoveryPayload iso = (IsoRecoveryPayload) restored;
		assertThat(iso.description()).isEqualTo("Rocky Linux 9.5");
		assertThat(iso.clientHash()).isEqualTo("abc123def456hash");
	}

	@Test
	@DisplayName("null / blank 안전")
	void nullSafe() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
		assertThat(converter.convertToEntityAttribute("")).isNull();
		assertThat(converter.convertToEntityAttribute("   ")).isNull();
	}
}
