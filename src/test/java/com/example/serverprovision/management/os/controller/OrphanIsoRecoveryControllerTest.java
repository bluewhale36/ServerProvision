package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.os.dto.response.OrphanIsoQuarantineResponse;
import com.example.serverprovision.management.os.dto.response.OrphanRetryResponse;
import com.example.serverprovision.management.os.exception.OrphanRecoveryAlreadyResolvedException;
import com.example.serverprovision.management.os.exception.OrphanRecoveryNotFoundException;
import com.example.serverprovision.management.os.service.iso.OrphanIsoRecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 오펀 ISO 복구 endpoint 의 사용자 액션 기반 통합 테스트.
 * <p>Service 단 mock 만으로는 "도메인 예외 → HTTP status" 매핑(404/409/400)이 드러나지 않으므로,
 * 컨트롤러 + @ControllerAdvice 매핑을 함께 실행해 실제 status·body 를 검증한다 (CLAUDE.md 테스트 규율).</p>
 *
 * 시나리오 커버리지 : 성공(2xx) · 입력검증/도메인(400) · 도메인 충돌(409) · 리소스 없음(404).
 */
@WebMvcTest(controllers = OrphanIsoRecoveryController.class)
class OrphanIsoRecoveryControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	OrphanIsoRecoveryService orphanIsoRecoveryService;
	// @EnableJpaAuditing 이 main class 에 있어 WebMvcTest 부팅 시 jpaMappingContext 를 요구한다.
	@MockitoBean
	JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private static OrphanIsoQuarantineResponse sample(String recoveryId) {
		return new OrphanIsoQuarantineResponse(
				recoveryId, 1L, "/opt/iso/rocky.iso", "rocky.iso",
				"메타데이터 저장에 실패했습니다 (DB 제약 위반).", "PENDING",
				LocalDateTime.of(2026, 6, 12, 10, 0), 0);
	}

	@Nested
	@DisplayName("GET /maintenance/quarantine")
	class PageAndDetail {

		@Test
		@DisplayName("목록 페이지 — 200 + view + model")
		void page_returnsView() throws Exception {
			given(orphanIsoRecoveryService.listPending()).willReturn(List.of(sample("r1")));

			mvc.perform(get("/maintenance/quarantine"))
					.andExpect(status().isOk())
					.andExpect(view().name("maintenance/quarantine/list"))
					.andExpect(model().attributeExists("orphans"));
		}

		@Test
		@DisplayName("단일 상세 — 200 + 필드")
		void detail_success() throws Exception {
			given(orphanIsoRecoveryService.get("r1")).willReturn(sample("r1"));

			mvc.perform(get("/maintenance/quarantine/r1"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.recoveryId").value("r1"))
					.andExpect(jsonPath("$.originalFilename").value("rocky.iso"))
					.andExpect(jsonPath("$.state").value("PENDING"));
		}

		@Test
		@DisplayName("단일 상세 — 없는 recoveryId 면 404")
		void detail_notFound() throws Exception {
			given(orphanIsoRecoveryService.get("nope"))
					.willThrow(new OrphanRecoveryNotFoundException("nope"));

			mvc.perform(get("/maintenance/quarantine/nope"))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("POST /maintenance/quarantine/{id}/retry")
	class Retry {

		@Test
		@DisplayName("정상 — 200 + jobId/redirect")
		void retry_success() throws Exception {
			given(orphanIsoRecoveryService.retry("r1"))
					.willReturn(new OrphanRetryResponse("job-1", "/management/os?selectId=1"));

			mvc.perform(post("/maintenance/quarantine/r1/retry"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.jobId").value("job-1"))
					.andExpect(jsonPath("$.redirect").value("/management/os?selectId=1"));
		}

		@Test
		@DisplayName("없는 recoveryId — 404")
		void retry_notFound() throws Exception {
			given(orphanIsoRecoveryService.retry("nope"))
					.willThrow(new OrphanRecoveryNotFoundException("nope"));

			mvc.perform(post("/maintenance/quarantine/nope/retry"))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("이미 복구/폐기된 항목 — 409")
		void retry_alreadyResolved() throws Exception {
			given(orphanIsoRecoveryService.retry("r1"))
					.willThrow(new OrphanRecoveryAlreadyResolvedException("r1", "DISCARDED"));

			mvc.perform(post("/maintenance/quarantine/r1/retry"))
					.andExpect(status().isConflict());
		}
	}

	@Nested
	@DisplayName("POST /maintenance/quarantine/{id}/discard")
	class Discard {

		@Test
		@DisplayName("정상 — 204 No Content")
		void discard_success() throws Exception {
			willDoNothing().given(orphanIsoRecoveryService).discard(eq("r1"), eq("rocky.iso"));

			mvc.perform(post("/maintenance/quarantine/r1/discard").param("typedName", "rocky.iso"))
					.andExpect(status().isNoContent());
		}

		@Test
		@DisplayName("파일명 불일치 — 400")
		void discard_typedNameMismatch() throws Exception {
			willThrow(new TypedNameMismatchException("rocky.iso", "wrong.iso"))
					.given(orphanIsoRecoveryService).discard(eq("r1"), any());

			mvc.perform(post("/maintenance/quarantine/r1/discard").param("typedName", "wrong.iso"))
					.andExpect(status().isBadRequest());
		}

		@Test
		@DisplayName("없는 recoveryId — 404")
		void discard_notFound() throws Exception {
			willThrow(new OrphanRecoveryNotFoundException("nope"))
					.given(orphanIsoRecoveryService).discard(eq("nope"), any());

			mvc.perform(post("/maintenance/quarantine/nope/discard").param("typedName", "x"))
					.andExpect(status().isNotFound());
		}
	}
}
