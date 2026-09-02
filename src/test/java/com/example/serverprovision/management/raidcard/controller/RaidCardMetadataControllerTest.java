package com.example.serverprovision.management.raidcard.controller;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.exception.RaidCardNudgeRequiredException;
import com.example.serverprovision.management.raidcard.service.RaidCardMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MA7 — {@link RaidCardMetadataController} 통합 테스트 (메타 CRUD : list / new / create / edit / update).
 *
 * <p>Mocking 은 {@link RaidCardMetadataService} 단까지만. controller 의 redirect / view 선택 +
 * {@code @ControllerAdvice} 의 예외 → status 매핑은 실제로 실행된다.</p>
 *
 * <p>시나리오 11 : 성공 6 (list / 미확인 배지 렌더 / newForm / editForm / create / update)
 * + 400 3 (지원 레벨 미선택 / PCI 형식 오류 / update 검증 재렌더) + 404 1 (editForm) + 409 2 (nudge / 중복).</p>
 */
@WebMvcTest(controllers = RaidCardMetadataController.class)
class RaidCardMetadataControllerTest {

	@Autowired MockMvc mvc;

	@MockitoBean RaidCardMetadataService raidCardService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private static RaidCardResponse activeCard(String pciDisplay) {
		return new RaidCardResponse(
				3L, RaidCardVendor.GIGABYTE, "CRA3338",
				List.of(RaidLevel.RAID0, RaidLevel.RAID1), "RAID0 · RAID1",
				RaidChipFamily.MPT_IR,
				0, "없음", false, pciDisplay, "desc",
				true, false, false, LifecycleStage.ACTIVE);
	}

	// ==== 성공 2xx ====================================================

	@Test
	@DisplayName("GET /management/raidcard — 목록 200 + list 뷰 + 지원 레벨 렌더")
	void list_returns200() throws Exception {
		given(raidCardService.findAllGrouped(false)).willReturn(List.of(
				RaidCardVendorGroupResponse.of(RaidCardVendor.GIGABYTE, List.of(activeCard("1458:0011")))));

		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(view().name("management/raidcard/list"))
				.andExpect(model().attributeExists("raidCardGroups"))
				.andExpect(content().string(containsString("RAID0 · RAID1")));
	}

	@Test
	@DisplayName("GET /management/raidcard — PCI 미입력 카드는 '미확인' 배지 렌더 (D3 수용 슬롯의 화면 계약)")
	void list_rendersUnknownPciBadge() throws Exception {
		given(raidCardService.findAllGrouped(false)).willReturn(List.of(
				RaidCardVendorGroupResponse.of(RaidCardVendor.GIGABYTE, List.of(activeCard(null)))));

		mvc.perform(get("/management/raidcard"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("미확인")));
	}

	@Test
	@DisplayName("GET /management/raidcard/new — 신규 폼 200 + new 뷰 + 옵션 모델")
	void newForm_returns200() throws Exception {
		mvc.perform(get("/management/raidcard/new"))
				.andExpect(status().isOk())
				.andExpect(view().name("management/raidcard/new"))
				.andExpect(model().attributeExists("raidCardForm", "vendorOptions", "levelOptions"));
	}

	/**
	 * CP6 개정 — 목록 C2 하단 등록 버튼이 현재 제조사를 {@code ?vendor=} 로 넘기면 폼에 프리셀렉트된다
	 * (ISO 의 osId · BIOS/BMC 의 boardId 처럼 등록 진입점이 선택 컨텍스트를 담아 가는 방식).
	 * record 는 equals 가 성분 기반이라 폼 초기값 전체를 통째로 단언한다.
	 */
	@Test
	@DisplayName("GET /management/raidcard/new?vendor=GIGABYTE — 제조사 프리셀렉트된 폼 초기값")
	void newForm_withVendor_preselects() throws Exception {
		mvc.perform(get("/management/raidcard/new").param("vendor", "GIGABYTE"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("raidCardForm",
						new com.example.serverprovision.management.raidcard.dto.request.RaidCardCreateRequest(
								RaidCardVendor.GIGABYTE, "", null, List.of(), 0, "", "")));
	}

	@Test
	@DisplayName("GET /management/raidcard/{id}/edit — 수정 폼 200 + edit 뷰")
	void editForm_returns200() throws Exception {
		given(raidCardService.findById(3L)).willReturn(activeCard("1458:0011"));

		mvc.perform(get("/management/raidcard/3/edit"))
				.andExpect(status().isOk())
				.andExpect(view().name("management/raidcard/edit"))
				.andExpect(model().attributeExists("raidCardForm", "raidCardId", "vendorLabel", "levelOptions"));
	}

	@Test
	@DisplayName("POST /management/raidcard (JSON) — 생성 성공 200 + redirect body")
	void create_success_returns200WithRedirect() throws Exception {
		given(raidCardService.create(any())).willReturn(42L);

		mvc.perform(post("/management/raidcard")
						.param("vendor", "GIGABYTE")
						.param("modelName", "CRA3338")
						.param("chipFamily", "MPT_IR")
						.param("supportedRaidLevels", "RAID0", "RAID1")
						.param("cacheCapacityGb", "0")
						.param("pciSubsystemId", "1458:0011")
						.param("description", "desc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.redirect").value("/management/raidcard?selectId=42"));
	}

	@Test
	@DisplayName("POST /management/raidcard/{id}/edit — 수정 성공 302 redirect (selectId 보존)")
	void update_success_returns302() throws Exception {
		mvc.perform(post("/management/raidcard/3/edit")
						.param("modelName", "CRA3338")
						.param("chipFamily", "MPT_IR")
						.param("supportedRaidLevels", "RAID0", "RAID1", "RAID5")
						.param("cacheCapacityGb", "2")
						.param("pciSubsystemId", "")
						.param("description", "new desc"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", "/management/raidcard?selectId=3"));
	}

	// ==== 400 검증 실패 ===============================================

	@Test
	@DisplayName("POST /management/raidcard (JSON) — 지원 레벨 미선택 → 400 + fieldErrors[supportedRaidLevels]")
	void create_noLevels_returns400() throws Exception {
		mvc.perform(post("/management/raidcard")
						.param("vendor", "GIGABYTE")
						.param("modelName", "CRA3338")
						.param("chipFamily", "MPT_IR")
						.param("cacheCapacityGb", "0")
						.param("description", "desc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("supportedRaidLevels"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("지원 RAID 레벨을 1개 이상 선택하세요."));
	}

	/**
	 * RV3 미수행 항목의 보완 — 캐시 용량 미입력은 클라이언트 검증이 선차단해 화면 경로로는 서버
	 * {@code @NotNull} 에 도달하지 못한다. direct POST(검증 우회)가 서버 안전망에 걸리는 것을 여기서 고정.
	 */
	@Test
	@DisplayName("POST /management/raidcard (JSON) — 캐시 용량 미입력(direct POST) → 400 + fieldErrors[cacheCapacityGb]")
	void create_missingCacheCapacity_returns400() throws Exception {
		mvc.perform(post("/management/raidcard")
						.param("vendor", "GIGABYTE")
						.param("modelName", "CRA3338")
						.param("chipFamily", "MPT_IR")
						.param("supportedRaidLevels", "RAID0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("cacheCapacityGb"))
				.andExpect(jsonPath("$.fieldErrors[0].message")
						.value("캐시 용량을 입력하세요. 캐시가 없는 모델은 0 을 입력합니다."));
	}

	@Test
	@DisplayName("POST /management/raidcard (JSON) — PCI 형식 오류 → 400 + fieldErrors[pciSubsystemId]")
	void create_malformedPci_returns400() throws Exception {
		mvc.perform(post("/management/raidcard")
						.param("vendor", "GIGABYTE")
						.param("modelName", "CRA3338")
						.param("chipFamily", "MPT_IR")
						.param("supportedRaidLevels", "RAID0")
						.param("cacheCapacityGb", "0")
						.param("pciSubsystemId", "zzzz"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("pciSubsystemId"));
	}

	@Test
	@DisplayName("POST /management/raidcard/{id}/edit — 모델명 누락 → 폼 뷰 재렌더(200, edit)")
	void update_validationFailure_rerendersForm() throws Exception {
		given(raidCardService.findById(3L)).willReturn(activeCard("1458:0011"));

		mvc.perform(post("/management/raidcard/3/edit")
						.param("modelName", "")
						.param("chipFamily", "MPT_IR")
						.param("supportedRaidLevels", "RAID0")
						.param("cacheCapacityGb", "0"))
				.andExpect(status().isOk())
				.andExpect(view().name("management/raidcard/edit"))
				.andExpect(model().attributeExists("raidCardId", "vendorLabel", "levelOptions"));
	}

	// ==== 404 ========================================================

	@Test
	@DisplayName("GET /management/raidcard/{id}/edit — 없는 id → RaidCardNotFound 404 (advice)")
	void editForm_notFound_returns404() throws Exception {
		willThrow(new RaidCardNotFoundException(999L))
				.given(raidCardService).findById(999L);

		mvc.perform(get("/management/raidcard/999/edit"))
				.andExpect(status().isNotFound());
	}

	// ==== 409 ========================================================

	@Test
	@DisplayName("POST /management/raidcard (JSON) — 메타 충돌(nudge required) → 409 (advice)")
	void create_metaConflict_returns409() throws Exception {
		NudgeRequiredResponse payload = NudgeRequiredResponse.of(
				UUID.randomUUID(), List.of(), Instant.now().plusSeconds(300));
		willThrow(new RaidCardNudgeRequiredException(payload))
				.given(raidCardService).create(any());

		mvc.perform(post("/management/raidcard")
						.param("vendor", "GIGABYTE")
						.param("modelName", "CRA3338")
						.param("chipFamily", "MPT_IR")
						.param("supportedRaidLevels", "RAID0")
						.param("cacheCapacityGb", "0"))
				.andExpect(status().isConflict());
	}

	@Test
	@DisplayName("POST /management/raidcard (JSON) — 살아 있는 동일키 중복 → Duplicate 409 + fieldErrors[modelName]")
	void create_liveDuplicate_returns409() throws Exception {
		willThrow(new DuplicateRaidCardException(RaidCardVendor.GIGABYTE, "CRA3338"))
				.given(raidCardService).create(any());

		mvc.perform(post("/management/raidcard")
						.param("vendor", "GIGABYTE")
						.param("modelName", "CRA3338")
						.param("chipFamily", "MPT_IR")
						.param("supportedRaidLevels", "RAID0")
						.param("cacheCapacityGb", "0")
						.accept("application/json"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("modelName"));
	}
}
