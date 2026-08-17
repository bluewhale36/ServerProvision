package com.example.serverprovision.management.raidcard.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.exception.IllegalRaidCardStateException;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.service.RaidCardLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MA7 — {@link RaidCardLifecycleController} 통합 테스트 (toggle / delete / restore / purge /
 * deprecate / undeprecate).
 *
 * <p>Mocking 은 {@link RaidCardLifecycleService} 단까지만 — advice 의 예외 → status 매핑이 실제로
 * 실행된다. D7 신설 가드(undeprecate 동일키 충돌 409)의 HTTP 채널이 여기서 고정된다.</p>
 *
 * <p>시나리오 12 : 성공 7 (toggle / deprecate / undeprecate / delete / purge / restore×2)
 * + 400 1 (purge typed-name 불일치) + 404 1 (없는 id toggle)
 * + 409 3 (restore invariant / restore 중복 / undeprecate 동일키 충돌).</p>
 */
@WebMvcTest(controllers = RaidCardLifecycleController.class)
class RaidCardLifecycleControllerTest {

	@Autowired MockMvc mvc;

	@MockitoBean RaidCardLifecycleService raidCardService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	// ==== 성공 2xx ====================================================

	@Test
	@DisplayName("POST /{id}/toggle — 302 redirect (selectId 보존)")
	void toggle_returns302() throws Exception {
		willDoNothing().given(raidCardService).toggleEnabled(eq(3L));

		mvc.perform(post("/management/raidcard/3/toggle"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard?selectId=3"));
	}

	@Test
	@DisplayName("POST /{id}/deprecate — 302 redirect (selectId 보존)")
	void deprecate_returns302() throws Exception {
		willDoNothing().given(raidCardService).deprecate(eq(3L));

		mvc.perform(post("/management/raidcard/3/deprecate"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard?selectId=3"));
	}

	@Test
	@DisplayName("POST /{id}/undeprecate — 302 redirect (selectId 보존)")
	void undeprecate_returns302() throws Exception {
		willDoNothing().given(raidCardService).undeprecate(eq(3L));

		mvc.perform(post("/management/raidcard/3/undeprecate"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard?selectId=3"));
	}

	@Test
	@DisplayName("POST /{id}/delete — 302 redirect (목록으로, 선택 복원 없음)")
	void delete_returns302() throws Exception {
		willDoNothing().given(raidCardService).softDelete(eq(3L));

		mvc.perform(post("/management/raidcard/3/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard"));
	}

	@Test
	@DisplayName("POST /{id}/purge — typedName 일치 → 302 redirect (includeDeleted 보존)")
	void purge_typedNameMatches_returns302() throws Exception {
		willDoNothing().given(raidCardService)
				.purgeWithTypedNameCheck(eq(3L), eq("GIGABYTE CRA3338"));

		mvc.perform(post("/management/raidcard/3/purge").param("typedName", "GIGABYTE CRA3338"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard?includeDeleted=true"));
	}

	@Test
	@DisplayName("POST /{id}/restore — 302 redirect (selectId 보존)")
	void restore_returns302() throws Exception {
		given(raidCardService.restore(eq(3L), eq(false))).willReturn(RestoreResponse.none());

		mvc.perform(post("/management/raidcard/3/restore"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard?selectId=3"));
	}

	@Test
	@DisplayName("POST /{id}/restore cascade=true — 자식 없는 leaf 라 0건으로 자연 처리 → 302")
	void restore_cascadeTrue_returns302() throws Exception {
		given(raidCardService.restore(eq(3L), eq(true))).willReturn(RestoreResponse.none());

		mvc.perform(post("/management/raidcard/3/restore").param("cascade", "true"))
				.andExpect(status().is3xxRedirection());
	}

	// ==== 400 — purge typed-name 불일치 ================================

	@Test
	@DisplayName("POST /{id}/purge — typedName 불일치 → TypedNameMismatch 400 (advice)")
	void purge_typedNameMismatch_returns400() throws Exception {
		willThrow(new TypedNameMismatchException("GIGABYTE CRA3338", "wrong"))
				.given(raidCardService)
				.purgeWithTypedNameCheck(eq(3L), eq("wrong"));

		mvc.perform(post("/management/raidcard/3/purge").param("typedName", "wrong"))
				.andExpect(status().isBadRequest());
	}

	// ==== 404 ========================================================

	@Test
	@DisplayName("POST /{id}/toggle — 없는 id → RaidCardNotFound 404 (advice)")
	void toggle_notFound_returns404() throws Exception {
		willThrow(new RaidCardNotFoundException(999L))
				.given(raidCardService).toggleEnabled(eq(999L));

		mvc.perform(post("/management/raidcard/999/toggle"))
				.andExpect(status().isNotFound());
	}

	// ==== 409 ========================================================

	@Test
	@DisplayName("POST /{id}/restore — 이미 활성/부재 → IllegalRaidCardState 409 (advice)")
	void restore_illegalState_returns409() throws Exception {
		willThrow(new IllegalRaidCardStateException("이미 활성 상태이거나 존재하지 않는 RAID 카드입니다. id=3"))
				.given(raidCardService).restore(eq(3L), eq(false));

		mvc.perform(post("/management/raidcard/3/restore"))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("POST /{id}/restore — 살아 있는 동일키 충돌 → Duplicate 409 + JSON message (async 채널)")
	void restore_duplicate_returns409Json() throws Exception {
		willThrow(new DuplicateRaidCardException(RaidCardVendor.GIGABYTE, "CRA3338"))
				.given(raidCardService).restore(eq(3L), eq(false));

		mvc.perform(post("/management/raidcard/3/restore").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.message").exists());
	}

	/**
	 * D7 신설 가드의 HTTP 채널 — Deprecated 해제가 동일키 살아 있는 카드와 충돌하면 DB 유니크 위반
	 * (500)이 아니라 서비스 가드의 409 로 수렴함을 고정한다.
	 */
	@Test
	@DisplayName("POST /{id}/undeprecate — 동일키 살아 있는 카드 존재 → IllegalRaidCardState 409 (advice)")
	void undeprecate_conflict_returns409() throws Exception {
		willThrow(new IllegalRaidCardStateException(
				"같은 이름의 카드가 이미 살아 있어 Deprecated 를 해제할 수 없습니다. id=3"))
				.given(raidCardService).undeprecate(eq(3L));

		mvc.perform(post("/management/raidcard/3/undeprecate"))
				.andExpect(status().isConflict());
	}
}
