package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.dto.response.BulkApplyResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftOriginResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftTimelineEntry;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftTimelineKind;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftTimelineResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftReportNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.service.DriftBulkApplyService;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.maintenance.reconciliation.service.ReconciliationScheduler;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanCoverage;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanPopulation;
import com.example.serverprovision.provisioning.usage.ResourceUsageLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MK4-4-2 — 개편된 화면 라우트의 HTTP 계층 검증.
 *
 * <p>배치 후보 둘을 실제 화면으로 만들어 견준 뒤 채택한 구조다. 다음 셋을 본다.</p>
 *
 * <ul>
 *   <li>첫 화면이 지금 남은 드리프트를 급한 순으로 놓는가 — MK4-4-2 개편의 핵심</li>
 *   <li>새로 생긴 두 상세 화면이 없는 자원에 404 를 주는가 — {@link DriftReportNotFoundException}
 *       이 이 슬라이스에서 새로 생겼으므로 그 예외를 실제로 발생시키는 시나리오가 필요하다</li>
 *   <li>[전체 해결] 이 부분 성공을 부분 성공으로 답하는가</li>
 * </ul>
 */
@WebMvcTest(controllers = {ReconciliationController.class, ReconciliationRestController.class})
class ReconciliationScreenRouteTest {

	@Autowired MockMvc mvc;

	@MockitoBean PathReconciliationService reconciliationService;
	@MockitoBean DriftBulkApplyService bulkApplyService;
	@MockitoBean ReconciliationScheduler scheduler;
	@MockitoBean com.example.serverprovision.global.orphan.service.OrphanQuarantineService orphanQuarantineService;
	@MockitoBean com.example.serverprovision.maintenance.reconciliation.service.recheck.DriftRecheckService driftRecheckService;
	@MockitoBean com.example.serverprovision.maintenance.reconciliation.service.HashAcceptService hashAcceptService;
	@MockitoBean com.example.serverprovision.maintenance.reconciliation.service.DuplicateResolveService duplicateResolveService;
	@MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

	private static final Instant NOW = Instant.parse("2026-08-12T04:00:00Z");

	@BeforeEach
	void setUp() {
		given(reconciliationService.history(any())).willReturn(new PageImpl<>(List.of(report(10L))));
		given(reconciliationService.latestReport()).willReturn(Optional.of(report(10L)));
		given(reconciliationService.openDrifts()).willReturn(List.of(drift(1L)));
		given(reconciliationService.openDriftCount()).willReturn(1L);
		given(reconciliationService.isResolutionEnabled()).willReturn(true);
		given(reconciliationService.scanCoverage()).willReturn(new ScanCoverage(true, NOW));
		given(scheduler.nextDueAt(any(ScanDepth.class))).willReturn(Optional.empty());
		given(orphanQuarantineService.countPending()).willReturn(0L);
		given(reconciliationService.timelineOf(anyLong()))
				.willReturn(new DriftTimelineResponse(List.of(), 0));
	}

	private DriftReportResponse report(Long id) {
		return new DriftReportResponse(id, NOW, "0.45초", false,
				ScanPopulation.of(9, 6, 1), 3,
				List.of("/srv/provision/iso"), List.of(), List.of(drift(1L)));
	}

	private DriftResponse drift(Long id) {
		return new DriftResponse(id, ResourceType.OS_ISO, 42L, "Rocky Linux 9.6",
				DriftKind.PATH_DRIFT, "/iso/old.iso", "/iso/new.iso",
				NOW, NOW, 1, DriftStatus.OPEN, null, null, null, null,
				"경로가 바뀌었습니다", ResourceUsageLevel.NONE, null);
	}

	@Nested
	@DisplayName("첫 화면")
	class FirstScreen {

		@Test
		@DisplayName("A1 지금 남은 드리프트가 첫 화면이다")
		void 미해결이_첫_화면() throws Exception {
			mvc.perform(get("/maintenance/reconciliation"))
					.andExpect(status().isOk())
					.andExpect(view().name("maintenance/reconciliation/list"))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("지금 남은 드리프트")));
		}

		/** 갈 수 있는 곳 둘은 배너 문장 속 링크가 아니라 헤더 버튼이다. */
		@Test
		@DisplayName("A2 운영 설정 · 점검 이력이 헤더 버튼으로 상시 보인다")
		void 헤더_버튼() throws Exception {
			mvc.perform(get("/maintenance/reconciliation"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString(">점검 이력</a>")))
					.andExpect(content().string(org.hamcrest.Matchers.containsString(">운영 설정</a>")));
		}

		/** 마지막 점검은 배경이라 제목 아래 보조 텍스트로 내려갔다 — 배너 자리를 차지하지 않는다. */
		@Test
		@DisplayName("A3 마지막 점검이 제목 아래 보조 텍스트로 나온다")
		void 마지막_점검_보조_텍스트() throws Exception {
			mvc.perform(get("/maintenance/reconciliation"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("n-page-subtext")))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("마지막 점검")));
		}
	}

	@Nested
	@DisplayName("상세 화면 — 두 후보가 공유")
	class DetailScreens {

		@Test
		@DisplayName("A4 회차 상세가 점검 범위와 세 모집단을 보여 준다")
		void 회차_상세() throws Exception {
			given(reconciliationService.report(10L)).willReturn(report(10L));
			given(bulkApplyService.targetsInReport(10L)).willReturn(List.of(1L));

			mvc.perform(get("/maintenance/reconciliation/reports/10"))
					.andExpect(status().isOk())
					.andExpect(view().name("maintenance/reconciliation/report-detail"))
					// 세 모집단 — 활성만 세던 값이 아니라 총계와 내역이 함께 나온다
					.andExpect(content().string(org.hamcrest.Matchers.containsString("모두 16 건")))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("짝이 없는 마커 1")))
					// 점검 범위 — 이 회차가 어디를 뒤졌는가
					.andExpect(content().string(org.hamcrest.Matchers.containsString("/srv/provision/iso")));
		}

		@Test
		@DisplayName("A5 문제 상세가 종류 · 수명 · 처리 방법을 보여 준다")
		void 문제_상세() throws Exception {
			given(reconciliationService.drift(1L)).willReturn(drift(1L));

			mvc.perform(get("/maintenance/reconciliation/drifts/1"))
					.andExpect(status().isOk())
					.andExpect(view().name("maintenance/reconciliation/drift-detail"))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("Rocky Linux 9.6")))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("해결 방법")));
		}

		@Test
		@DisplayName("A6 이력 목록이 별도 화면으로 열린다 — 후보 B 의 부차 화면")
		void 이력_목록() throws Exception {
			mvc.perform(get("/maintenance/reconciliation/reports"))
					.andExpect(status().isOk())
					.andExpect(view().name("maintenance/reconciliation/report-list"));
		}

		/**
		 * 목록의 행을 눌러 들어오는 정상 흐름에서는 도달하지 않는다. 주소창 직접 입력과, 목록을
		 * 열어 둔 채 그 회차가 보관 한도를 넘겨 정리된 경우가 이 경로다.
		 */
		@Test
		@DisplayName("B2 없는 회차 → 404")
		void 없는_회차_404() throws Exception {
			given(reconciliationService.report(999L)).willThrow(new DriftReportNotFoundException(999L));

			mvc.perform(get("/maintenance/reconciliation/reports/999"))
					.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("B3 없는 문제 → 404")
		void 없는_문제_404() throws Exception {
			given(reconciliationService.drift(999L)).willThrow(new DriftNotFoundException(999L));

			mvc.perform(get("/maintenance/reconciliation/drifts/999"))
					.andExpect(status().isNotFound());
		}
	}

	/**
	 * MK4-4-2 — 앞선 드리프트가 형태를 바꿔 나타난 것을 화면이 그렇게 말하는가.
	 *
	 * <p>S11-2 가 계보를 기록 계층으로 남기면서 표시를 MK4-4 로 넘겼는데, 그 사이 화면은 승계된
	 * 드리프트를 「최초 발견」 으로 표시하고 있었다 — 관측 횟수가 1 이라는 이유만으로.</p>
	 */
	@Nested
	@DisplayName("계보 — 앞선 드리프트에서 이어진 것")
	class Lineage {

		private DriftResponse succeeded() {
			return new DriftResponse(2L, ResourceType.OS_ISO, 42L, "Rocky Linux 9.6",
					DriftKind.RESOURCE_REPLICA, "/iso/a.iso", "/iso/b.iso",
					NOW, NOW, 1, DriftStatus.OPEN, null, null, null, null, "복제본 발견",
					ResourceUsageLevel.NONE,
					new DriftOriginResponse(1L, DriftKind.SIGNATURE_INVALID, NOW, NOW));
		}

		@Test
		@DisplayName("A9 승계된 드리프트는 「최초 발견」 이 아니라 「이어짐」 으로 표시된다")
		void 이어짐_배지() throws Exception {
			given(reconciliationService.openDrifts()).willReturn(List.of(succeeded()));

			mvc.perform(get("/maintenance/reconciliation"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("이어짐")))
					// 어디서 이어졌는지를 tooltip 이 말한다 — 배지만으로는 출처를 알 수 없다
					.andExpect(content().string(org.hamcrest.Matchers.containsString(
							DriftKind.SIGNATURE_INVALID.getLabel())))
					.andExpect(content().string(org.hamcrest.Matchers.not(
							org.hamcrest.Matchers.containsString("최초 발견"))));
		}

		/**
		 * 계보를 별도 구획으로 두었더니 이력과 무슨 관계인지 알 수 없다는 지적을 받았다. 한
		 * 시간축에 합쳐, 이어짐이 이력의 마지막 줄들로 들어온다.
		 */
		@Test
		@DisplayName("A10 이어짐이 이력의 한 줄로 들어오고 앞선 드리프트로 갈 수 있다")
		void 계보가_이력에_들어온다() throws Exception {
			given(reconciliationService.drift(2L)).willReturn(succeeded());
			given(reconciliationService.timelineOf(2L)).willReturn(new DriftTimelineResponse(List.of(
					new DriftTimelineEntry(NOW, DriftTimelineKind.SUCCESSION,
							"「마커 서명 불일치」 가 닫히며 이어짐", null, 1L, false, true,
							"/iso/a.iso", null)), 0));

			mvc.perform(get("/maintenance/reconciliation/drifts/2"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("가 닫히며 이어짐")))
					// 앞선 드리프트로 갈 수 있다 — "그 앞은 무엇이었나" 가 바로 옆 질문이다
					.andExpect(content().string(org.hamcrest.Matchers.containsString(
							"/maintenance/reconciliation/drifts/1")))
					// 별도 구획으로 갈라져 있지 않다
					.andExpect(content().string(org.hamcrest.Matchers.not(
							org.hamcrest.Matchers.containsString("어디서 이어졌나"))));
		}
	}

	@Nested
	@DisplayName("이력 — 관측과 처리를 한 시간축에")
	class Timeline {

		@Test
		@DisplayName("A12 관측과 처리가 시간순으로 함께 나온다 — 관측은 그 회차로 갈 수 있다")
		void 이력_표시() throws Exception {
			given(reconciliationService.drift(1L)).willReturn(drift(1L));
			given(reconciliationService.timelineOf(1L)).willReturn(new DriftTimelineResponse(List.of(
					new DriftTimelineEntry(NOW.plusSeconds(60), DriftTimelineKind.HANDLING, "해결",
							null, null, true, true, "/iso/old.iso", "/iso/new.iso"),
					new DriftTimelineEntry(NOW, DriftTimelineKind.OBSERVATION, "정밀 점검에서 관측",
							425L, null, true, true, "/iso/old.iso", null)), 0));

			mvc.perform(get("/maintenance/reconciliation/drifts/1"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("이력")))
					.andExpect(content().string(org.hamcrest.Matchers.containsString("정밀 점검에서 관측")))
					// 관측은 회차로 가는 링크를 갖는다 — "그때 무엇을 함께 봤나" 가 바로 옆 질문이다
					.andExpect(content().string(org.hamcrest.Matchers.containsString(
							"/maintenance/reconciliation/reports/425")));
		}

		/** 잘라 놓고 말하지 않으면 보이는 것이 전부인 줄 알게 된다. */
		@Test
		@DisplayName("A13 감춘 건수를 밝힌다")
		void 감춘_건수_고지() throws Exception {
			given(reconciliationService.drift(1L)).willReturn(drift(1L));
			given(reconciliationService.timelineOf(1L)).willReturn(new DriftTimelineResponse(List.of(
					new DriftTimelineEntry(NOW, DriftTimelineKind.OBSERVATION, "일반 점검에서 관측",
							425L, null, true, false, null, null)), 34));

			mvc.perform(get("/maintenance/reconciliation/drifts/1"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.containsString("이전 34 건은 표시하지 않았습니다")));
		}

		@Test
		@DisplayName("A14 이력이 없으면 구획이 나타나지 않는다")
		void 이력_없으면_구획_없음() throws Exception {
			given(reconciliationService.drift(1L)).willReturn(drift(1L));

			mvc.perform(get("/maintenance/reconciliation/drifts/1"))
					.andExpect(status().isOk())
					.andExpect(content().string(org.hamcrest.Matchers.not(
							org.hamcrest.Matchers.containsString("최근 것이 위"))));
		}
	}

	@Nested
	@DisplayName("전체 해결")
	class BulkApply {

		@Test
		@DisplayName("A7 회차 단위 — 집은 수와 해결한 수를 함께 답한다")
		void 회차_전체_해결() throws Exception {
			given(bulkApplyService.targetsInReport(10L)).willReturn(List.of(1L, 2L));
			given(bulkApplyService.applyAll(List.of(1L, 2L)))
					.willReturn(new BulkApplyResponse(2, 2, List.of()));

			mvc.perform(post("/maintenance/reconciliation/reports/10/apply-all"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.requested").value(2))
					.andExpect(jsonPath("$.applied").value(2));
		}

		@Test
		@DisplayName("A8 열린 것 전체 — 부분 성공을 부분 성공으로 답한다")
		void 열린_것_전체_해결() throws Exception {
			given(bulkApplyService.openTargets()).willReturn(List.of(1L, 2L, 3L));
			given(bulkApplyService.applyAll(List.of(1L, 2L, 3L)))
					.willReturn(new BulkApplyResponse(3, 2, List.of("#2 — 이미 해결된 드리프트입니다.")));

			mvc.perform(post("/maintenance/reconciliation/drifts/apply-all"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.applied").value(2))
					.andExpect(jsonPath("$.failures.length()").value(1));
		}

		@Test
		@DisplayName("B4 없는 회차에 전체 해결 → 404")
		void 없는_회차_전체_해결_404() throws Exception {
			given(bulkApplyService.targetsInReport(anyLong()))
					.willThrow(new DriftReportNotFoundException(999L));

			mvc.perform(post("/maintenance/reconciliation/reports/999/apply-all"))
					.andExpect(status().isNotFound());
		}
	}
}
