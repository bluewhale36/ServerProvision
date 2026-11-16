package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.exception.PurgeTargetNotInTrashException;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableInventory;
import com.example.serverprovision.global.trash.service.UserPurgeService;
import com.example.serverprovision.management.board.service.metadata.BoardModelLifecycleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HF31 — 자원 화면의 [영구 삭제] 가 휴지통 화면과 같은 단일 진입점(UserPurgeService → PurgeExecutor)을 실제로 탄다.
 * UserPurgeService 는 실제 빈, 그 아래 휴지통 조회(MarkableInventory) · PurgeExecutor 만 mock — 컨트롤러 → 단일 진입점 → advice 매핑이 실제로 돈다.
 * 도메인 lifecycle 서비스의 purge 는 컨트롤러가 직접 부르지 않는다(감사 기록 · 휴지통 실물 정리를 건너뛰던 종전 경로, docs C-1).
 */
@Import(UserPurgeService.class)
@WebMvcTest(controllers = BoardModelLifecycleController.class)
class BoardModelPurgeUnifiedFlowTest {

	@Autowired MockMvc mvc;

	@MockitoBean PurgeExecutor purgeExecutor;
	@MockitoBean MarkableInventory boardInventory;
	@MockitoBean BoardModelLifecycleService boardModelLifecycleService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private void givenTrashedBoard() {
		Markable trashed = org.mockito.Mockito.mock(Markable.class);
		given(trashed.displayName()).willReturn("Asus P13R-E");
		given(boardInventory.supportedType()).willReturn(ResourceType.BOARD_MODEL);
		given(boardInventory.findTrashedById(3L)).willReturn(java.util.Optional.of(trashed));
	}

	@Test
	@DisplayName("성공 : 자원 화면 영구 삭제가 PurgeExecutor 에 USER_DIRECT 요청을 넘기고 302, 도메인 purge 를 직접 부르지 않는다")
	void purge_goesThroughPurgeExecutor() throws Exception {
		givenTrashedBoard();
		given(purgeExecutor.execute(any())).willAnswer(inv -> new PurgeResult.Success(inv.getArgument(0), 10L));

		mvc.perform(post("/management/board/3/purge").param("typedName", "Asus P13R-E"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/board?includeDeleted=true"));

		then(purgeExecutor).should().execute(PurgeRequest.forUserDirect(ResourceType.BOARD_MODEL, 3L, null, "Asus P13R-E"));
		then(boardModelLifecycleService).should(never()).purge(any(Long.class));
	}

	@Test
	@DisplayName("409 : 휴지통에 없는 보드(활성 · 이미 영구 삭제)에 direct POST → 409 + 사유, 실행기 미호출(HF31 CP5 F-1)")
	void purge_targetNotInTrash_returns409() throws Exception {
		given(boardInventory.supportedType()).willReturn(ResourceType.BOARD_MODEL);
		given(boardInventory.findTrashedById(3L)).willReturn(java.util.Optional.empty());

		mvc.perform(post("/management/board/3/purge").param("typedName", "Asus P13R-E")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("휴지통에 있는 자원만 영구 삭제할 수 있습니다. 목록을 새로 고친 뒤 다시 확인하십시오."));

		then(purgeExecutor).should(never()).execute(any());
	}

	@Test
	@DisplayName("409 : 실행 중 다른 경로가 먼저 지움(실행기가 휴지통에서 못 찾음) → 원인 예외 그대로 409")
	void purge_executorReportsNotInTrash_returns409() throws Exception {
		givenTrashedBoard();
		given(purgeExecutor.execute(any())).willAnswer(inv ->
				new PurgeResult.Failed(inv.getArgument(0), 11L, new PurgeTargetNotInTrashException()));

		mvc.perform(post("/management/board/3/purge").param("typedName", "Asus P13R-E")
						.accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("400 : 자원명 불일치 → PurgeExecutor 미호출(감사 기록 · 삭제 없음)")
	void purge_typedNameMismatch_returns400WithoutExecuting() throws Exception {
		givenTrashedBoard();

		mvc.perform(post("/management/board/3/purge").param("typedName", "wrong"))
				.andExpect(status().isBadRequest());

		then(purgeExecutor).should(never()).execute(any());
	}
}
