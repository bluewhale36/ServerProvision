package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.maintenance.reconciliation.enums.ReconciliationSettingItem;
import com.example.serverprovision.maintenance.reconciliation.service.ReconciliationSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MK4-3-1 — 운영 설정 화면. 서버가 값을 잘 만들어도 화면이 그것을 쓰지 않으면 운영자에게 도달하지
 * 않으므로, 항목 설명과 후보 목록이 실제로 렌더되는지까지 본다.
 */
@WebMvcTest(controllers = ReconciliationSettingsController.class)
class ReconciliationSettingsControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ReconciliationSettingsService settingsService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private void givenDefaults() {
		Map<ReconciliationSettingItem, String> values = new EnumMap<>(ReconciliationSettingItem.class);
		values.put(ReconciliationSettingItem.AUTO_APPLY_KINDS,
				ReconciliationSettingItem.AUTO_APPLY_KINDS.defaultValue());
		values.put(ReconciliationSettingItem.RESOLUTION_ENABLED, "true");
		values.put(ReconciliationSettingItem.REPORT_RETENTION_COUNT, "100");
		values.put(ReconciliationSettingItem.EXTRA_SCAN_ROOTS, "");
		values.put(ReconciliationSettingItem.STARTUP_SCAN_ENABLED, "true");
		given(settingsService.currentValues()).willReturn(values);
		given(settingsService.unknownAutoApplyKinds()).willReturn(Set.of());
	}

	@Test
	@DisplayName("GET — 자동 처리 후보가 전부 렌더되고 되돌릴 수 있는지가 함께 보인다")
	void get_rendersCandidatesWithReversibility() throws Exception {
		givenDefaults();

		var result = mvc.perform(get("/maintenance/reconciliation/settings"))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("settings"));

		// 후보 목록은 종류의 해결 등급에서 파생한다 — 화면에 이름을 하드코딩하지 않았으므로 전부 나와야 한다.
		for (DriftKind kind : DriftKind.values()) {
			if (!kind.isAutoApplicable()) continue;
			result.andExpect(content().string(containsString(kind.getLabel())));
		}
		result.andExpect(content().string(containsString("되돌릴 수 없음")))
				.andExpect(content().string(containsString("되돌릴 수 있음")));
	}

	@Test
	@DisplayName("GET — 항목마다 뜻과 효과 시점이 화면에 나온다")
	void get_rendersItemDescriptions() throws Exception {
		givenDefaults();

		var result = mvc.perform(get("/maintenance/reconciliation/settings"))
				.andExpect(status().isOk());

		for (ReconciliationSettingItem item : ReconciliationSettingItem.values()) {
			result.andExpect(content().string(containsString(item.getLabel())));
			// 설명에 작은따옴표가 들어가면 화면에서는 HTML 로 이스케이프된다. 같은 이스케이프를 거쳐
			// 비교해야 문구 전체를 그대로 고정할 수 있다(일부만 잘라 비교하면 뒤가 바뀌어도 통과한다).
			result.andExpect(content().string(containsString(
					org.springframework.web.util.HtmlUtils.htmlEscape(item.getDescription()))));
		}
	}

	@Test
	@DisplayName("GET — 점검 주기는 읽기 전용으로 보이고 입력 요소가 아니다")
	void get_showsScheduleAsReadOnly() throws Exception {
		givenDefaults();

		mvc.perform(get("/maintenance/reconciliation/settings"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("시간마다")))
				.andExpect(content().string(containsString(
						ReconciliationSettingItem.EffectTiming.RESTART_REQUIRED.getLabel())))
				// 주기 항목에는 입력 요소를 만들지 않는다.
				.andExpect(content().string(not(containsString("name=\"scanIntervalMs\""))));
	}

	@Test
	@DisplayName("GET — 알 수 없는 항목이 있으면 드러낸다")
	void get_surfacesUnknownKinds() throws Exception {
		givenDefaults();
		given(settingsService.unknownAutoApplyKinds()).willReturn(Set.of("NOT_A_KIND"));

		mvc.perform(get("/maintenance/reconciliation/settings"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("알 수 없는 항목")))
				.andExpect(content().string(containsString("NOT_A_KIND")));
	}

	@Test
	@DisplayName("POST — 유효한 값이면 저장하고 같은 화면으로 되돌린다")
	void post_savesAndRedirects() throws Exception {
		givenDefaults();

		mvc.perform(post("/maintenance/reconciliation/settings")
						.param("autoApplyKinds", DriftKind.PATH_DRIFT.name())
						.param("resolutionEnabled", "true")
						.param("reportRetentionCount", "50")
						.param("extraScanRoots", "/mnt/backup")
						.param("startupScanEnabled", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/maintenance/reconciliation/settings?saved"));

		verify(settingsService).update(org.mockito.ArgumentMatchers.any());
	}

	@Test
	@DisplayName("POST 400 — 보관 개수가 1 미만이면 필드 메시지와 함께 같은 화면을 다시 그린다")
	void post_rejectsRetentionBelowOne() throws Exception {
		givenDefaults();

		mvc.perform(post("/maintenance/reconciliation/settings")
						.param("resolutionEnabled", "true")
						.param("reportRetentionCount", "0")
						.param("extraScanRoots", "")
						.param("startupScanEnabled", "true"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("보고서는 최소 1 회분은 남겨야 해요.")));
	}

	@Test
	@DisplayName("POST 400 — 상대 경로는 거절한다")
	void post_rejectsRelativePath() throws Exception {
		givenDefaults();

		mvc.perform(post("/maintenance/reconciliation/settings")
						.param("resolutionEnabled", "true")
						.param("reportRetentionCount", "100")
						.param("extraScanRoots", "backup/iso")
						.param("startupScanEnabled", "true"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("절대 경로로 입력해주세요")));
	}

	@Test
	@DisplayName("POST 400 — 자동 처리 대상에 알 수 없는 종류가 오면 거절한다")
	void post_rejectsUnknownKind() throws Exception {
		givenDefaults();

		mvc.perform(post("/maintenance/reconciliation/settings")
						.param("autoApplyKinds", "NOT_A_KIND")
						.param("resolutionEnabled", "true")
						.param("reportRetentionCount", "100")
						.param("extraScanRoots", "")
						.param("startupScanEnabled", "true"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("알 수 없는 종류가 섞여 있어요")));
	}
}
