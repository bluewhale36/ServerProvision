package com.example.serverprovision.management.raidcard.controller;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardObservationSummaryResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.enums.RaidCardObservationStatus;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.exception.RaidCardObservationConfirmRejectedException;
import com.example.serverprovision.management.raidcard.service.RaidCardMetadataService;
import com.example.serverprovision.management.raidcard.service.RaidCardObservationService;
import com.example.serverprovision.management.raidcard.vo.RaidCardObservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E3.5-5-b D5 — [관측값으로 확정] 엔드포인트(302 · 409 · 404)와 목록 C3 관측 블록의 5 상태 렌더.
 * Mocking 은 Service 단까지 — advice 의 409 매핑과 Thymeleaf 렌더는 실제로 실행된다.
 */
@WebMvcTest(controllers = RaidCardMetadataController.class)
class RaidCardObservationControllerTest {

	private static final UUID G1 = UUID.randomUUID();
	private static final UUID G2 = UUID.randomUUID();

	@Autowired MockMvc mvc;

	@MockitoBean RaidCardMetadataService raidCardService;
	@MockitoBean RaidCardObservationService observationService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private static RaidCardResponse card(boolean deleted, String pci) {
		return new RaidCardResponse(3L, RaidCardVendor.GIGABYTE, "CRA3338",
				List.of(RaidLevel.RAID0), "RAID0", RaidChipFamily.MPT_IR, 0, "없음", false, pci, null,
				true, false, deleted, LifecycleStage.of(false, deleted));
	}

	private static RaidCardObservationSummaryResponse summary(String confirmed, RaidCardObservation... observations) {
		return RaidCardObservationSummaryResponse.of(confirmed, List.of(observations));
	}

	private void stubList(boolean includeDeleted, RaidCardResponse card, RaidCardObservationSummaryResponse summary) {
		given(raidCardService.findAllGrouped(includeDeleted))
				.willReturn(List.of(RaidCardVendorGroupResponse.of(RaidCardVendor.GIGABYTE, List.of(card))));
		given(observationService.summariesByCard(any())).willReturn(summary == null ? Map.of() : Map.of(3L, summary));
	}

	// ==== POST confirm-observed ==========================================

	@Test
	@DisplayName("POST /{id}/confirm-observed — 302 selectId 보존 + 서비스 위임")
	void confirm_redirects() throws Exception {
		mvc.perform(post("/management/raidcard/3/confirm-observed"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard?selectId=3"));
		verify(observationService).confirmObserved(3L);
	}

	@Test
	@DisplayName("POST /{id}/confirm-observed — 판정 불허는 409 + 사유 문장(tooltip 과 같은 문장)")
	void confirm_conflict() throws Exception {
		willThrow(new RaidCardObservationConfirmRejectedException(RaidCardObservationStatus.CONFLICTING.blockReason()))
				.given(observationService).confirmObserved(3L);
		mvc.perform(post("/management/raidcard/3/confirm-observed").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(RaidCardObservationStatus.CONFLICTING.blockReason()));
	}

	@Test
	@DisplayName("POST /{id}/confirm-observed — 없는 · 삭제된 카드는 404")
	void confirm_notFound() throws Exception {
		willThrow(new RaidCardNotFoundException(3L)).given(observationService).confirmObserved(3L);
		mvc.perform(post("/management/raidcard/3/confirm-observed").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	// ==== 목록 렌더 — 5 상태 ==============================================

	@Test
	@DisplayName("NONE — 안내 한 줄 · 버튼 없음")
	void list_none() throws Exception {
		stubList(false, card(false, null), summary(null));
		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("data-observation-status=\"NONE\"")))
				.andExpect(content().string(containsString("이 카드를 지정한 서버의 진단 결과가 아직 없습니다.")))
				.andExpect(content().string(not(containsString("confirm-observed"))));
	}

	@Test
	@DisplayName("AGREED_UNCONFIRMED — 파란 배지 · 게스트 링크 · 활성 버튼(disabled 없음)")
	void list_agreed() throws Exception {
		stubList(false, card(false, null), summary(null, new RaidCardObservation(G1, "srv-01", "1000:9361")));
		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("n-badge-blue")))
				.andExpect(content().string(containsString("관측 1000:9361")))
				.andExpect(content().string(containsString("서버 1대가 관측했습니다")))
				.andExpect(content().string(containsString("href=\"/provisioning/server/" + G1 + "\"")))
				.andExpect(content().string(containsString("srv-01")))
				.andExpect(content().string(containsString("action=\"/management/raidcard/3/confirm-observed\"")))
				.andExpect(content().string(containsString("관측값으로 확정")))
				.andExpect(content().string(not(containsString("disabled=\"disabled\""))));
	}

	@Test
	@DisplayName("MATCHES_CONFIRMED — 초록 배지 · 버튼 없음")
	void list_matches() throws Exception {
		stubList(false, card(false, "1000:9361"), summary("1000:9361", new RaidCardObservation(G1, "srv-01", "1000:9361")));
		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("n-badge-green")))
				.andExpect(content().string(containsString("관측이 확정값과 일치합니다 (1000:9361 · 서버 1대)")))
				.andExpect(content().string(not(containsString("confirm-observed"))));
	}

	@Test
	@DisplayName("DIFFERS_FROM_CONFIRMED — 위험 배너 · 버튼 없음")
	void list_differs() throws Exception {
		stubList(false, card(false, "1000:9361"), summary("1000:9361", new RaidCardObservation(G1, "srv-01", "1000:00ce")));
		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("n-alert-danger")))
				.andExpect(content().string(containsString("관측 1000:00ce 이 확정값 1000:9361 과 다릅니다")))
				.andExpect(content().string(not(containsString("confirm-observed"))));
	}

	@Test
	@DisplayName("CONFLICTING — 위험 배너 · 값별 게스트 목록 · 잠긴 버튼 + tooltip 사유")
	void list_conflicting() throws Exception {
		stubList(false, card(false, null), summary(null,
				new RaidCardObservation(G1, "srv-01", "1000:9361"), new RaidCardObservation(G2, "srv-02", "1000:00ce")));
		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("관측이 서로 다릅니다 (1000:9361 · 1000:00ce)")))
				.andExpect(content().string(containsString("srv-01")))
				.andExpect(content().string(containsString("srv-02")))
				.andExpect(content().string(containsString("disabled=\"disabled\"")))
				.andExpect(content().string(containsString("title=\"" + RaidCardObservationStatus.CONFLICTING.blockReason() + "\"")));
	}

	@Test
	@DisplayName("CONFLICTING + 확정된 카드 — 배너 · 목록은 그리되 잠긴 버튼은 없다(CP5 F-2)")
	void list_conflictingOnConfirmedCardHasNoButton() throws Exception {
		stubList(false, card(false, "1000:9361"), summary("1000:9361",
				new RaidCardObservation(G1, "srv-01", "1000:9361"), new RaidCardObservation(G2, "srv-02", "1458:3008")));
		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("관측이 서로 다릅니다 (1000:9361 · 1458:3008)")))
				.andExpect(content().string(containsString("srv-02")))
				.andExpect(content().string(not(containsString("confirm-observed"))));
	}

	@Test
	@DisplayName("삭제된 카드 — 관측 블록을 그리지 않는다")
	void list_deletedCardHasNoBlock() throws Exception {
		stubList(true, card(true, null), null);
		mvc.perform(get("/management/raidcard").param("includeDeleted", "true"))
				.andExpect(status().isOk())
				.andExpect(content().string(not(containsString("data-observation-status"))));
	}
}
