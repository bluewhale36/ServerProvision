package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanCoverage;
import com.example.serverprovision.provisioning.usage.ResourceUsageLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MK4-2 — 점검 커버리지가 화면에 실제로 렌더되는지. 서버가 값을 잘 만들어도 화면이 그 값을 쓰지
 * 않으면 "해결된 것이 아니라 보지 않은 것" 이라는 사실은 여전히 사용자에게 도달하지 않는다.
 */
@WebMvcTest(controllers = ReconciliationController.class)
class ReconciliationCoverageRenderTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private PathReconciliationService reconciliationService;

	@MockitoBean
	private com.example.serverprovision.global.orphan.service.OrphanQuarantineService orphanQuarantineService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private static final Instant NOW = Instant.parse("2026-08-09T04:00:00Z");
	private static final Instant LAST_DEEP = Instant.parse("2026-08-08T18:00:00Z");

	private DriftResponse hashMismatch() {
		return new DriftResponse(1L, ResourceType.OS_ISO, 42L, "Rocky Linux 9.4 dvd.iso",
				DriftKind.HASH_MISMATCH, "/db/dvd.iso", null,
				NOW, NOW, 2, DriftStatus.OPEN, null, null, null, null, "지문 불일치",
				ResourceUsageLevel.ASSIGNED);
	}

	private void givenPage(ScanCoverage coverage) {
		given(reconciliationService.history(any())).willReturn(new PageImpl<>(List.of(
				new DriftReportResponse(10L, NOW, "0.1초", coverage.contentChecked(), 5, 1,
						List.of(), List.of(hashMismatch())))));
		given(reconciliationService.latestReport()).willReturn(java.util.Optional.empty());
		given(reconciliationService.openDriftCount()).willReturn(1L);
		given(reconciliationService.openDrifts()).willReturn(List.of(hashMismatch()));
		given(reconciliationService.isResolutionEnabled()).willReturn(true);
		given(reconciliationService.scanCoverage()).willReturn(coverage);
		given(orphanQuarantineService.countPending()).willReturn(0L);
	}

	@Test
	@DisplayName("일반 점검 — 내용 미확인 배너와 마지막 정밀 점검 시각이 뜬다")
	void quickScanShowsCoverageWarning() throws Exception {
		givenPage(new ScanCoverage(false, LAST_DEEP));

		mvc.perform(get("/maintenance/reconciliation"))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("scanCoverage"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"이번 점검은 파일 내용을 확인하지 않았습니다.")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("마지막 정밀 점검")))
				// 내용을 봐야 판정되는 종류의 자리에 '확인 안 됨' 이 붙는다.
				.andExpect(content().string(org.hamcrest.Matchers.containsString("확인 안 됨")));
	}

	@Test
	@DisplayName("정밀 점검 — 배너가 사라지고 '확인 안 됨' 도 붙지 않는다")
	void deepScanHasNoWarning() throws Exception {
		givenPage(new ScanCoverage(true, NOW));

		mvc.perform(get("/maintenance/reconciliation"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("이번 점검은 파일 내용을 확인하지 않았습니다."))))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("확인 안 됨"))));
	}

	@Test
	@DisplayName("정밀 점검 이력이 없어도 화면이 깨지지 않는다")
	void neverDeepScannedRendersSafely() throws Exception {
		givenPage(new ScanCoverage(false, null));

		mvc.perform(get("/maintenance/reconciliation"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						"아직 정밀 점검을 한 번도 하지 않았습니다.")));
	}

	@Test
	@DisplayName("위험도와 사용 중이 상세에 표시된다 — 순서의 근거를 화면이 말한다")
	void severityAndUsageAreRendered() throws Exception {
		givenPage(new ScanCoverage(true, NOW));

		mvc.perform(get("/maintenance/reconciliation"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("위험도")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						DriftKind.HASH_MISMATCH.getSeverity().getLabel())))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						ResourceUsageLevel.ASSIGNED.getLabel())));
	}
}
