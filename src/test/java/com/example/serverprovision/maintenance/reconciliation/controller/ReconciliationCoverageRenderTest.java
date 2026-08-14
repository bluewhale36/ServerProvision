package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanCoverage;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanPopulation;
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
	private com.example.serverprovision.maintenance.reconciliation.service.ReconciliationScheduler scheduler;

	// MK4-4-2 — 목록이 [전체 해결] 대상 수를 함께 보인다. 이 테스트의 관심사는 커버리지 표기라
	// 스텁하지 않고 둔다(0 이면 버튼이 비활성으로 그려질 뿐이다).
	@MockitoBean
	private com.example.serverprovision.maintenance.reconciliation.service.DriftBulkApplyService bulkApplyService;

	@MockitoBean
	private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private static final Instant NOW = Instant.parse("2026-08-09T04:00:00Z");
	private static final Instant LAST_DEEP = Instant.parse("2026-08-08T18:00:00Z");

	private DriftResponse hashMismatch() {
		return new DriftResponse(1L, ResourceType.OS_ISO, 42L, "Rocky Linux 9.4 dvd.iso",
				DriftKind.HASH_MISMATCH, "/db/dvd.iso", null,
				NOW, NOW, 2, DriftStatus.OPEN, null, null, null, null, null, null, "지문 불일치",
				ResourceUsageLevel.ASSIGNED, null);
	}

	private void givenPage(ScanCoverage coverage) {
		given(reconciliationService.history(any())).willReturn(new PageImpl<>(List.of(
				new DriftReportResponse(10L, NOW, "0.1초", coverage.contentChecked(), ScanPopulation.of(5, 0, 0), 1, List.of(), List.of(), List.of(hashMismatch())))));
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
						"이번 점검은 파일 내용을 확인하지 않습니다")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("마지막 정밀 점검")));
		// '확인 안 됨' 표시는 드리프트 상세의 필드다. MK4-4-2 개편으로 상세가 별도 화면이 되면서
		// 이 목록에서는 확인할 수 없게 됐다 — 그 검증은 상세 화면 테스트가 맡는다.
	}

	@Test
	@DisplayName("정밀 점검 — 배너가 사라지고 '확인 안 됨' 도 붙지 않는다")
	void deepScanHasNoWarning() throws Exception {
		givenPage(new ScanCoverage(true, NOW));

		mvc.perform(get("/maintenance/reconciliation"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("이번 점검은 파일 내용을 확인하지 않습니다"))));
	}

	@Test
	@DisplayName("정밀 점검 기록이 없어도 화면이 깨지지 않는다 — 보관 정리로 사라진 경우 포함")
	void neverDeepScannedRendersSafely() throws Exception {
		givenPage(new ScanCoverage(false, null));

		mvc.perform(get("/maintenance/reconciliation"))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						// MK4-4-2 — 라벨(마지막 정밀 점검)과 값이 갈리면서 값 쪽 문구가 짧아졌다.
						"남아 있는 기록 없음")));
	}

	@Test
	@DisplayName("위험도와 사용 중이 상세에 표시된다 — 순서의 근거를 화면이 말한다")
	void severityAndUsageAreRendered() throws Exception {
		givenPage(new ScanCoverage(true, NOW));

		mvc.perform(get("/maintenance/reconciliation"))
				.andExpect(status().isOk())
				// 목록은 위험도와 사용 중을 열로 보여 준다. 설명 문구는 상세의 몫이다.
				.andExpect(content().string(org.hamcrest.Matchers.containsString("위험도")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						DriftKind.HASH_MISMATCH.getSeverity().getLabel())))
				.andExpect(content().string(org.hamcrest.Matchers.containsString(
						ResourceUsageLevel.ASSIGNED.getLabel())));
	}
}
