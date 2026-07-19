package com.example.serverprovision.management.os.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HF4-2 (F-6) — OS 도메인 Request 의 문자열 길이 계약 단위 검증.
 *
 * <p>배경 : {@code ISOCreateRequest.description} 에 {@code @Size} 가 없어 1,600자 설명이 검증을 통과한 뒤
 * background 등록 잡의 INSERT 에서 MariaDB 1406(Data too long, VARCHAR(1024)) 으로 실패했다.
 * 앞단 Bean Validation 이 엔티티 {@code @Column(length)} 와 같은 상한으로 즉시 거절하는지를 경계값으로 고정한다.</p>
 */
class OsRequestSizeValidationTest {

	static ValidatorFactory factory;
	static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	private static String chars(int n) {
		return "가".repeat(n);
	}

	@Test
	@DisplayName("ISOCreateRequest.description 1,025자 → @Size 위반 1건 + 한국어 메시지 (F-6 재현 차단)")
	void isoCreate_descriptionOverLimit_violates() {
		var request = new ISOCreateRequest("/opt/iso/dvd.iso", chars(1025), false);

		Set<ConstraintViolation<ISOCreateRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		ConstraintViolation<ISOCreateRequest> violation = violations.iterator().next();
		assertThat(violation.getPropertyPath().toString()).isEqualTo("description");
		assertThat(violation.getMessage()).isEqualTo("설명은 1024자 이하로 입력해주세요.");
	}

	@Test
	@DisplayName("ISOCreateRequest.description 정확히 1,024자 → 위반 없음 (경계값 = 엔티티 length)")
	void isoCreate_descriptionAtLimit_passes() {
		var request = new ISOCreateRequest("/opt/iso/dvd.iso", chars(1024), false);

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	@DisplayName("ISOCreateRequest.isoPath 1,025자 → @Size 위반 (iso_path VARCHAR(1024))")
	void isoCreate_isoPathOverLimit_violates() {
		var request = new ISOCreateRequest("/" + chars(1024), null, false);

		Set<ConstraintViolation<ISOCreateRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("isoPath");
	}

	@Test
	@DisplayName("OSMetadataCreateRequest.osVersion 65자 → @Size 위반 (os_version VARCHAR(64))")
	void osMetadataCreate_osVersionOverLimit_violates() {
		var request = new OSMetadataCreateRequest(
				com.example.serverprovision.management.os.enums.OSName.ROCKY_LINUX, chars(65), null);

		Set<ConstraintViolation<OSMetadataCreateRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		ConstraintViolation<OSMetadataCreateRequest> violation = violations.iterator().next();
		assertThat(violation.getPropertyPath().toString()).isEqualTo("osVersion");
		assertThat(violation.getMessage()).isEqualTo("OS 버전은 64자 이하로 입력해주세요.");
	}

	@Test
	@DisplayName("IsoUploadIntentRequest.isoPath 1,025자 → 핸드셰이크 단계에서 @Size 위반 (업로드 전 선차단)")
	void isoUploadIntent_isoPathOverLimit_violates() {
		var request = new IsoUploadIntentRequest("/" + chars(1024), "dvd.iso", 1024L, false);

		Set<ConstraintViolation<IsoUploadIntentRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("isoPath");
	}
}
