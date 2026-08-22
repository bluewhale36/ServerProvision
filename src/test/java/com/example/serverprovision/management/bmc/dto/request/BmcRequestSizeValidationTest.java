package com.example.serverprovision.management.bmc.dto.request;

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
 * HF4-2 (F-6 파생) — BMC 경로 필드 상한 255 하향의 행동 변경 검증.
 *
 * <p>배경 : 경로는 {@code BoardBMC.treeRootPath}(firmware_path, 1024) 외에
 * legacy 컬럼 {@code file_path VARCHAR(255) NOT NULL} 에도 동일 값이 미러링된다
 * (BoardBMC.legacyFilePath). 기존 {@code @Size(max=1024)} 는 검증 상한이 실제 DB 제약(255)보다 커서
 * 256~1024자 입력이 검증 통과 후 INSERT 에서 실패하는 F-6 동형 잠복 결함이었다 —
 * effective 제약 255 로의 정렬을 경계값으로 고정한다. legacy 컬럼 제거 시 1024 복원 예정.</p>
 *
 * <p>R12-2 — 단일 폼 개편으로 필드명이 {@code targetDirectory} 에서 {@code firmwarePath} 로 바뀌었다.
 * 255 제약 자체는 legacy 컬럼이 남아 있는 한 그대로이며, 화면에도 이 상한을 안내한다.</p>
 */
class BmcRequestSizeValidationTest {

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

	private static String path(int totalLength) {
		return "/" + "a".repeat(totalLength - 1);
	}

	@Test
	@DisplayName("BmcCreateRequest.firmwarePath 256자 → @Size(255) 위반 (legacy file_path 정렬 — 행동 변경)")
	void bmcCreate_firmwarePathOverLegacyLimit_violates() {
		var request = new BmcCreateRequest("iDRAC", "1.0", path(256), null, false);

		Set<ConstraintViolation<BmcCreateRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		ConstraintViolation<BmcCreateRequest> violation = violations.iterator().next();
		assertThat(violation.getPropertyPath().toString()).isEqualTo("firmwarePath");
		assertThat(violation.getMessage()).isEqualTo("펌웨어 파일 경로는 255자 이하로 입력해주세요.");
	}

	@Test
	@DisplayName("BmcCreateRequest.firmwarePath 정확히 255자 → 위반 없음 (경계값 = legacy 컬럼 length)")
	void bmcCreate_firmwarePathAtLegacyLimit_passes() {
		var request = new BmcCreateRequest("iDRAC", "1.0", path(255), null, false);

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	@DisplayName("BmcUploadIntentRequest.firmwarePath 256자 → 핸드셰이크 단계에서 @Size(255) 위반")
	void bmcUploadIntent_firmwarePathOverLegacyLimit_violates() {
		var request = new BmcUploadIntentRequest(path(256), "bmc.ima_enc", 0L, "1.0", false);

		Set<ConstraintViolation<BmcUploadIntentRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("firmwarePath");
	}
}
