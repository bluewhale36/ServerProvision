package com.example.serverprovision.maintenance.trash.controller;

import com.example.serverprovision.global.trash.service.UserPurgeService;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.TrashPolicy;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.maintenance.trash.service.TrashTtlExtensionService;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HF26 — 휴지통의 [영구삭제] 사전 차단. 참조가 남은 메인보드는 버튼이 disabled 이고 tooltip 에 서버 가드와 같은 사유가 실린다.
 * 사유는 scanner SPI({@code purgeBlockReason})가 내며, 서버 가드(도메인 purge)와 같은 판정을 공유한다.
 */
@org.springframework.context.annotation.Import(UserPurgeService.class)   // HF31 — 실제 단일 진입점이 mock scanner · PurgeExecutor 를 부른다
@WebMvcTest(controllers = TrashController.class)
class TrashControllerPurgeBlockTest {

	private static final String REASON =
			"이 메인보드 모델을 참조하는 다음 항목이 남아 있어 영구 삭제할 수 없습니다: 등록된 서버 2 대.";

	@Autowired MockMvc mvc;

	@MockitoBean TrashTtlExtensionService trashTtlExtensionService;
	@MockitoBean MarkableScanner markableScanner;
	@MockitoBean TrashPolicy trashPolicy;
	@MockitoBean PurgeExecutor purgeExecutor;
	@MockitoBean TypedNameVerifier typedNameVerifier;
	@MockitoBean com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService pathReconciliationService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private BoardModel trashedBoard() {
		BoardModel board = BoardModel.builder().id(7L).vendor(Vendor.GIGABYTE).modelName("MS74-HB0")
				.isEnabled(true).isDeprecated(false).isDeleted(false).build();
		board.softDelete();
		board.markTrashed(null);
		return board;
	}

	private void givenTrash(String reason) {
		given(trashPolicy.getTtl()).willReturn(Duration.ofDays(30));
		given(trashPolicy.getTtlDays()).willReturn(30);
		given(pathReconciliationService.openDriftIdsOf(any())).willReturn(Map.of());
		given(markableScanner.supportedType()).willReturn(ResourceType.BOARD_MODEL);
		given(markableScanner.findTrashed()).willReturn(List.<Markable>of(trashedBoard()));
		given(markableScanner.findGhostMarkables()).willReturn(List.of());
		given(markableScanner.findDeletedChildLabels(7L)).willReturn(List.of());
		given(markableScanner.purgeBlockReason(7L)).willReturn(reason);
	}

	@Test
	@DisplayName("참조가 남은 메인보드 — [영구삭제] disabled + tooltip 에 서버 가드와 같은 사유")
	void blockedBoard_rendersDisabledPurgeWithReason() throws Exception {
		givenTrash(REASON);

		mvc.perform(get("/maintenance/trash"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-tooltip=\"" + REASON + "\"")))
				.andExpect(content().string(matchesPattern("(?s).*<button type=\"submit\" class=\"n-btn n-btn-sm n-btn-outline-danger\"\\s+disabled=\"disabled\">영구삭제</button>.*")));
	}

	@Test
	@DisplayName("막을 참조가 없으면 [영구삭제] 가 열려 있다")
	void freeBoard_rendersEnabledPurge() throws Exception {
		givenTrash(null);

		mvc.perform(get("/maintenance/trash"))
				.andExpect(status().isOk())
				.andExpect(content().string(matchesPattern("(?s).*<button type=\"submit\" class=\"n-btn n-btn-sm n-btn-outline-danger\"\\s*>영구삭제</button>.*")));
	}

	@Test
	@DisplayName("D-1 — 참조가 남은 보드를 휴지통 경로로 direct POST 하면 500 이 아니라 409 + 같은 사유")
	void trashPurge_blockedBoard_returns409WithReason() throws Exception {
		var request = com.example.serverprovision.global.trash.PurgeRequest.forUserDirect(
				ResourceType.BOARD_MODEL, 6L, null, "Gigabyte HF26-REF");
		// HF31 — 단일 진입점이 휴지통 자원을 먼저 찾아 자원명을 대조한다
		Markable trashed = org.mockito.Mockito.mock(Markable.class);
		given(trashed.displayName()).willReturn("Gigabyte HF26-REF");
		given(markableScanner.supportedType()).willReturn(ResourceType.BOARD_MODEL);
		given(markableScanner.findTrashedById(6L)).willReturn(java.util.Optional.of(trashed));
		given(purgeExecutor.execute(any())).willReturn(new com.example.serverprovision.global.trash.PurgeResult.Failed(
				request, 46L, new com.example.serverprovision.management.board.exception.IllegalBoardModelStateException(REASON)));

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.post("/maintenance/trash/BOARD_MODEL/6/purge").param("typedName", "Gigabyte HF26-REF")
						.accept(org.springframework.http.MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value(REASON));
	}
}
