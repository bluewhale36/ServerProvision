package com.example.serverprovision.management.raidcard.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import com.example.serverprovision.management.raidcard.service.RaidCardNudgeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MA7 — {@link RaidCardNudgeController} 통합 테스트 (메타 nudge confirm : proceed / replace / cancel).
 *
 * <p>Mocking 은 {@link RaidCardNudgeService} + {@link TypedNameVerifier} 단까지만.
 * {@code @ControllerAdvice} 의 예외 → status 매핑은 실제로 실행된다.</p>
 *
 * <p>시나리오 6 : 성공 3 (proceed / replace JSON, cancel 204) + 400 1 (replace typedName 불일치)
 * + 404 1 (없는 nudgeId proceed) + 409 1 (이미 처리된 nudge replace).</p>
 */
@WebMvcTest(controllers = RaidCardNudgeController.class)
class RaidCardNudgeControllerTest {

	@Autowired MockMvc mvc;

	@MockitoBean RaidCardNudgeService raidCardNudgeService;
	@MockitoBean TypedNameVerifier typedNameVerifier;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private static final UUID NUDGE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	// ==== 성공 2xx ====================================================

	@Test
	@DisplayName("POST /nudge/{nudgeId}/proceed — 신규 등록 → JSON(id + redirect)")
	void nudgeProceed_returnsJson() throws Exception {
		given(raidCardNudgeService.proceed(eq(NUDGE_ID))).willReturn(77L);

		mvc.perform(post("/management/raidcard/nudge/" + NUDGE_ID + "/proceed"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(77))
				.andExpect(jsonPath("$.redirect").value("/management/raidcard?selectId=77"));
	}

	@Test
	@DisplayName("POST /nudge/{nudgeId}/replace — 충돌 자원 교체 → JSON(id + redirect)")
	void nudgeReplace_returnsJson() throws Exception {
		given(raidCardNudgeService.replace(eq(NUDGE_ID), eq(9L))).willReturn(88L);

		mvc.perform(post("/management/raidcard/nudge/" + NUDGE_ID + "/replace")
						.param("targetId", "9")
						.param("typedName", "GIGABYTE CRA3338"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(88))
				.andExpect(jsonPath("$.redirect").value("/management/raidcard?selectId=88"));
	}

	@Test
	@DisplayName("POST /nudge/{nudgeId}/cancel — 세션 폐기 → 204 No Content")
	void nudgeCancel_returns204() throws Exception {
		willDoNothing().given(raidCardNudgeService).cancel(eq(NUDGE_ID));

		mvc.perform(post("/management/raidcard/nudge/" + NUDGE_ID + "/cancel"))
				.andExpect(status().isNoContent());
	}

	// ==== 400 — replace typed-name 불일치 =============================

	@Test
	@DisplayName("POST /nudge/{nudgeId}/replace — typedName 불일치 → TypedNameMismatch 400 (advice)")
	void nudgeReplace_typedNameMismatch_returns400() throws Exception {
		willThrow(new TypedNameMismatchException("GIGABYTE CRA3338", "wrong"))
				.given(typedNameVerifier)
				.verify(eq(ResourceType.RAID_CARD), eq(9L), eq("wrong"));

		mvc.perform(post("/management/raidcard/nudge/" + NUDGE_ID + "/replace")
						.param("targetId", "9")
						.param("typedName", "wrong"))
				.andExpect(status().isBadRequest());
	}

	// ==== 404 — 없는 nudgeId ==========================================

	@Test
	@DisplayName("POST /nudge/{nudgeId}/proceed — 없는 nudgeId → NudgeNotFound 404 (advice)")
	void nudgeProceed_notFound_returns404() throws Exception {
		willThrow(new NudgeNotFoundException(NUDGE_ID))
				.given(raidCardNudgeService).proceed(eq(NUDGE_ID));

		mvc.perform(post("/management/raidcard/nudge/" + NUDGE_ID + "/proceed"))
				.andExpect(status().isNotFound());
	}

	// ==== 409 — 이미 처리된 nudge =====================================

	@Test
	@DisplayName("POST /nudge/{nudgeId}/replace — 이미 처리된 세션 → NudgeAlreadyResolved 409 (advice)")
	void nudgeReplace_alreadyResolved_returns409() throws Exception {
		// typedName 없이 호출 → verifier 우회, service 가 던지는 경로.
		willThrow(new NudgeAlreadyResolvedException(NUDGE_ID))
				.given(raidCardNudgeService).replace(eq(NUDGE_ID), eq(9L));

		mvc.perform(post("/management/raidcard/nudge/" + NUDGE_ID + "/replace")
						.param("targetId", "9"))
				.andExpect(status().isConflict());
	}
}
