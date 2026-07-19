package com.example.serverprovision.management.bmc.dto.request;

import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
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
 * HF4-2 (F-6 파생) — BMC {@code targetDirectory} 상한 255 하향의 행동 변경 검증.
 *
 * <p>배경 : {@code targetDirectory} 는 {@code BoardBMC.treeRootPath}(firmware_path, 1024) 외에
 * legacy 컬럼 {@code file_path VARCHAR(255) NOT NULL} 에도 동일 값이 미러링된다
 * (BoardBMC.legacyFilePath). 기존 {@code @Size(max=1024)} 는 검증 상한이 실제 DB 제약(255)보다 커서
 * 256~1024자 입력이 검증 통과 후 INSERT 에서 실패하는 F-6 동형 잠복 결함이었다 —
 * effective 제약 255 로의 정렬을 경계값으로 고정한다. legacy 컬럼 제거 시 1024 복원 예정.</p>
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
	@DisplayName("BmcCreateRequest.targetDirectory 256자 → @Size(255) 위반 (legacy file_path 정렬 — 행동 변경)")
	void bmcCreate_targetDirectoryOverLegacyLimit_violates() {
		var request = new BmcCreateRequest("iDRAC", "1.0", path(256), null, false, null);

		Set<ConstraintViolation<BmcCreateRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		ConstraintViolation<BmcCreateRequest> violation = violations.iterator().next();
		assertThat(violation.getPropertyPath().toString()).isEqualTo("targetDirectory");
		assertThat(violation.getMessage()).isEqualTo("대상 디렉토리 경로는 255자 이하로 입력해주세요.");
	}

	@Test
	@DisplayName("BmcCreateRequest.targetDirectory 정확히 255자 → 위반 없음 (경계값 = legacy 컬럼 length)")
	void bmcCreate_targetDirectoryAtLegacyLimit_passes() {
		var request = new BmcCreateRequest("iDRAC", "1.0", path(255), null, false, null);

		assertThat(validator.validate(request)).isEmpty();
	}

	@Test
	@DisplayName("BmcUploadIntentRequest.targetDirectory 256자 → 핸드셰이크 단계에서 @Size(255) 위반")
	void bmcUploadIntent_targetDirectoryOverLegacyLimit_violates() {
		var request = new BmcUploadIntentRequest(path(256), BmcUploadMode.FOLDER, 1, 0L, "1.0", false, null);

		Set<ConstraintViolation<BmcUploadIntentRequest>> violations = validator.validate(request);

		assertThat(violations).hasSize(1);
		assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("targetDirectory");
	}
}
