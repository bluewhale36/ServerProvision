package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftHandling;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftObservation;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftHandlingAction;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.enums.SnoozeWindow;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftHandlingRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.GhostDbRowClearResolution;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.PathDriftResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * S11-2 — 재분류 승계 감지의 단위 검증. plan §6 의 U1~U8 과 1:1 대응한다.
 *
 * <p>감지 규칙: 관측되지 않아 닫히는 열린 문제(전임)와 같은 자원 신원의 <b>이번 회차 신규</b> 문제
 * (후임)가 함께 있으면, 후임이 전임을 {@code Drift.predecessor} 로 가리키고 전임은
 * {@code SUPERSEDED} 로 닫힌다. 전임 후보가 여럿이면 lastObservedAt 최신 · 동률 시 식별자
 * 오름차순(D-2 · Q1 확정), 후임이 여럿이면 전원이 같은 전임을 가리킨다(D-1 fan-out).</p>
 */
class PathReconciliationSuccessionTest {

	private ProvisionMarkerService markerService;
	private DriftReportRepository driftReportRepository;
	private DriftRepository driftRepository;
	private DriftHandlingRepository driftHandlingRepository;
	private MarkableScanner isoScanner;
	private PathReconciliationService service;

	@BeforeEach
	void setUp() {
		markerService = new ProvisionMarkerService();
		ReflectionTestUtils.setField(markerService, "secret", "test-secret");

		BackgroundJobService backgroundJobService = mock(BackgroundJobService.class);
		given(backgroundJobService.register(any(), anyString(), anyString(),
				org.mockito.ArgumentMatchers.<List<String>>any())).willReturn("job-1");

		driftReportRepository = mock(DriftReportRepository.class);
		given(driftReportRepository.save(any(DriftReport.class))).willAnswer(inv -> inv.getArgument(0));
		given(driftReportRepository.count()).willReturn(0L);

		driftRepository = mock(DriftRepository.class);
		given(driftRepository.save(any(Drift.class))).willAnswer(inv -> inv.getArgument(0));
		driftHandlingRepository = mock(DriftHandlingRepository.class);

		isoScanner = mock(MarkableScanner.class);
		given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);

		service = new PathReconciliationService(
				List.of(isoScanner), markerService, backgroundJobService,
				driftReportRepository, driftRepository, driftHandlingRepository,
				List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
		ReflectionTestUtils.setField(service, "startupEnabled", true);
		ReflectionTestUtils.setField(service, "retentionCount", 100);
		ReflectionTestUtils.setField(service, "autoApplyKindsCsv", "");
		ReflectionTestUtils.setField(service, "extraRootsCsv", "");
	}

	/** soft-deleted 분류 패스용 fixture — PathReconciliationServiceTest.DeletedIso 와 동형. */
	private static class DeletedIso extends com.example.serverprovision.global.entity.LifecycleEntity implements Markable {
		private final Long id;
		private final Path path;
		DeletedIso(Long id, Path path, String trashedPath) {
			this.id = id;
			this.path = path;
			softDelete();
			if (trashedPath != null) markTrashed(trashedPath);
		}
		@Override protected Long resourceId() { return id; }
		@Override protected com.example.serverprovision.global.entity.LifecycleEntity parentLifecycle() { return null; }
		@Override public Long getResourceId() { return id; }
		@Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
		@Override public Path getResourcePath() { return path; }
		@Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
		@Override public String getManifestHash() { return "hash-abc"; }
		@Override public String getMarkerSignature() { return "sig"; }
		@Override public void reissueMarker(String h, String sg) { }
	}

	private void writeMarker(Path resourcePath, Long id) {
		MarkerContent unsigned = new MarkerContent(
				ResourceType.OS_ISO.name(), id, Map.of(), Instant.now(), "hash-abc", null);
		String sig = markerService.computeSignature(unsigned);
		markerService.write(resourcePath, MarkerLayout.SIDECAR, unsigned.withSignature(sig));
	}

	/** 이전 회차에서 열린 문제 stub — 식별자와 마지막 관측 시각을 지정해 D-2 정렬을 검증한다. */
	private static Drift openDrift(Long driftId, Long resourceId, DriftKind kind, Instant lastObservedAt) {
		Drift d = Drift.builder()
				.resourceType(ResourceType.OS_ISO).resourceId(resourceId).kind(kind)
				.oldPath("/old/path").newPath(null)
				.firstDetectedAt(lastObservedAt.minusSeconds(3600)).lastObservedAt(lastObservedAt)
				.build();
		ReflectionTestUtils.setField(d, "id", driftId);
		return d;
	}

	private DriftReport captureSavedReport() {
		var captor = ArgumentCaptor.forClass(DriftReport.class);
		verify(driftReportRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		return captor.getValue();
	}

	private static List<Drift> driftsOf(DriftReport report) {
		return report.getObservations().stream().map(DriftObservation::getDrift).toList();
	}

	private List<DriftHandlingAction> savedHandlingActions() {
		var captor = ArgumentCaptor.forClass(DriftHandling.class);
		verify(driftHandlingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		return captor.getAllValues().stream().map(DriftHandling::getAction).toList();
	}

	@Test
	@DisplayName("U1 감지 happy : 소실 전임 닫힘 + 복귀 후임 신설 → 계보 링크 + SUPERSEDED 닫힘")
	void succession_linksPredecessorAndClosesSuperseded(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Files.createDirectories(orig.getParent());
		Files.writeString(orig, "body-restored"); // 운영자가 본체를 원위치에 복원 → 복귀 신설
		String trashedGone = tmp.resolve("trash/dvd_x.iso").toString(); // 휴지통 실물은 여전히 없음
		Drift trashLost = openDrift(5L, 42L, DriftKind.TRASH_LOST, Instant.parse("2026-08-08T00:00:00Z"));
		given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(trashLost));
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashedGone)));

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d -> {
			assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL);
			assertThat(d.getPredecessor()).isSameAs(trashLost);
		});
		assertThat(trashLost.getStatus()).isEqualTo(DriftStatus.RESOLVED);
		assertThat(trashLost.getResolvedBy()).isEqualTo(DriftHandlingAction.SUPERSEDED);
		assertThat(savedHandlingActions()).containsExactly(DriftHandlingAction.SUPERSEDED);
	}

	@Test
	@DisplayName("U2 비감지 — 다른 자원 : 무관 자원의 닫힘에는 링크가 생기지 않고 SCAN_UNOBSERVED 유지")
	void succession_ignoresUnrelatedResource(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Files.createDirectories(orig.getParent());
		Files.writeString(orig, "body-restored"); // 자원 42 에 복귀 신설
		Drift otherResource = openDrift(7L, 43L, DriftKind.TRASH_LOST, Instant.parse("2026-08-08T00:00:00Z"));
		given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(otherResource));
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(
				new DeletedIso(42L, orig, tmp.resolve("trash/dvd_x.iso").toString())));

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d ->
				assertThat(d.getPredecessor()).isNull());
		assertThat(otherResource.getResolvedBy()).isEqualTo(DriftHandlingAction.SCAN_UNOBSERVED);
	}

	@Test
	@DisplayName("U3 비감지 — 신규 아님 : 기존 열린 문제의 재관측은 후임이 아니다 → 닫힘은 SCAN_UNOBSERVED")
	void succession_reobservedProblemIsNotSuccessor(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso"); // 원위치 비어 있음
		Path trashed = tmp.resolve("trash/dvd_x.iso");
		Files.createDirectories(trashed.getParent());
		Files.writeString(trashed, "restored-into-trash"); // 실물이 휴지통으로 복원됨 → 소실 해소(정상 보관)
		Path strayAt = tmp.resolve("backup/dvd.iso");
		Files.createDirectories(strayAt.getParent());
		writeMarker(strayAt, 42L); // 미아 마커는 여전히 잔존 → 재관측
		Drift stray = openDrift(3L, 42L, DriftKind.SOFTDEL_MARKER_STRAY, Instant.parse("2026-08-08T00:00:00Z"));
		Drift trashLost = openDrift(4L, 42L, DriftKind.TRASH_LOST, Instant.parse("2026-08-08T00:00:00Z"));
		given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(stray, trashLost));
		given(driftRepository.findFirstByResourceTypeAndResourceIdAndKindAndStatusNot(
				eq(ResourceType.OS_ISO), eq(42L), eq(DriftKind.SOFTDEL_MARKER_STRAY), eq(DriftStatus.RESOLVED)))
				.willReturn(Optional.of(stray));
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed.toString())));
		ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.resolve("backup").toString());

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		// 재관측된 미아 마커는 신규가 아니므로 후임 자격이 없다 — 소실은 승계 없이 닫힌다.
		assertThat(stray.getPredecessor()).isNull();
		assertThat(stray.getStatus()).isEqualTo(DriftStatus.OPEN);
		assertThat(trashLost.getResolvedBy()).isEqualTo(DriftHandlingAction.SCAN_UNOBSERVED);
	}

	@Test
	@DisplayName("U4 fan-out : 같은 회차 신규 2건(복귀 + 이탈)이 전부 같은 전임을 가리킨다")
	void succession_fanOutLinksAllSuccessors(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Path other = tmp.resolve("backup/dvd.iso");
		Files.createDirectories(orig.getParent());
		Files.createDirectories(other.getParent());
		Files.writeString(orig, "body-at-original");   // 복귀 신설 (상태 E)
		Files.writeString(other, "body-at-other");     // 타위치 본체 + 마커 → 이탈 병행 신설
		writeMarker(other, 42L);
		Drift stray = openDrift(6L, 42L, DriftKind.SOFTDEL_MARKER_STRAY, Instant.parse("2026-08-08T00:00:00Z"));
		given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(stray));
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(
				new DeletedIso(42L, orig, tmp.resolve("trash/gone.iso").toString())));
		ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		List<Drift> observed = driftsOf(captureSavedReport());
		assertThat(observed).extracting(Drift::getKind).containsExactlyInAnyOrder(
				DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL, DriftKind.SOFTDEL_ESCAPE_TO_OTHER);
		assertThat(observed).allSatisfy(d -> assertThat(d.getPredecessor()).isSameAs(stray));
		assertThat(stray.getResolvedBy()).isEqualTo(DriftHandlingAction.SUPERSEDED);
	}

	@Test
	@DisplayName("U5 최초 고정 : 계보는 한 번 이어지면 바뀌지 않는다")
	void linkPredecessor_isImmutableOnceSet() {
		Drift successor = openDrift(10L, 42L, DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL, Instant.parse("2026-08-09T00:00:00Z"));
		Drift first = openDrift(1L, 42L, DriftKind.TRASH_LOST, Instant.parse("2026-08-08T00:00:00Z"));
		Drift second = openDrift(2L, 42L, DriftKind.GHOST_DB_ROW, Instant.parse("2026-08-08T12:00:00Z"));

		successor.linkPredecessor(first);
		successor.linkPredecessor(second);

		assertThat(successor.getPredecessor()).isSameAs(first);
	}

	@Test
	@DisplayName("U6 전임 다수 : lastObservedAt 최신 전임이 계보를 받고, 나머지는 SCAN_UNOBSERVED")
	void succession_picksLatestObservedPredecessor(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Files.createDirectories(orig.getParent());
		Files.writeString(orig, "body-restored"); // 복귀 신설, 전임 둘 다 닫힘
		Drift older = openDrift(5L, 42L, DriftKind.TRASH_LOST, Instant.parse("2026-08-07T00:00:00Z"));
		Drift newer = openDrift(9L, 42L, DriftKind.SOFTDEL_MARKER_STRAY, Instant.parse("2026-08-08T00:00:00Z"));
		given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(older, newer));
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(
				new DeletedIso(42L, orig, tmp.resolve("trash/gone.iso").toString())));

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d ->
				assertThat(d.getPredecessor()).isSameAs(newer));
		assertThat(newer.getResolvedBy()).isEqualTo(DriftHandlingAction.SUPERSEDED);
		assertThat(older.getResolvedBy()).isEqualTo(DriftHandlingAction.SCAN_UNOBSERVED);
	}

	@Test
	@DisplayName("U7 동률 처리(Q1) : lastObservedAt 이 같으면 식별자 오름차순 — 먼저 열린 전임이 계보를 받는다")
	void succession_tieBreaksByAscendingId(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Files.createDirectories(orig.getParent());
		Files.writeString(orig, "body-restored");
		Instant sameObserved = Instant.parse("2026-08-08T00:00:00Z"); // 직전 회차에 함께 관측 — 흔한 동률
		Drift earlier = openDrift(5L, 42L, DriftKind.TRASH_LOST, sameObserved);
		Drift later = openDrift(9L, 42L, DriftKind.SOFTDEL_MARKER_STRAY, sameObserved);
		given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(later, earlier));
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(
				new DeletedIso(42L, orig, tmp.resolve("trash/gone.iso").toString())));

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d ->
				assertThat(d.getPredecessor()).isSameAs(earlier));
		assertThat(earlier.getResolvedBy()).isEqualTo(DriftHandlingAction.SUPERSEDED);
		assertThat(later.getResolvedBy()).isEqualTo(DriftHandlingAction.SCAN_UNOBSERVED);
	}

	@Test
	@DisplayName("U9 보관 중 전임 : SNOOZED 드리프트도 전임으로 채택되어 SUPERSEDED 로 닫힌다 — 보관하던 사건일수록 계보를 잃지 않는다 (CP5 관찰 3 고정)")
	void succession_snoozedPredecessorIsSupersededWithLineage(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Files.createDirectories(orig.getParent());
		Files.writeString(orig, "body-restored"); // 보관 중이던 소실 자원의 본체가 복원됨 → 복귀 신설
		Drift snoozed = openDrift(5L, 42L, DriftKind.TRASH_LOST, Instant.parse("2026-08-08T00:00:00Z"));
		snoozed.snooze(SnoozeWindow.DAYS_7, "부품 입고 대기", Instant.parse("2026-08-08T00:00:00Z"));
		given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(snoozed));
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(
				new DeletedIso(42L, orig, tmp.resolve("trash/gone.iso").toString())));

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		// MK4-1 이 "보관 중이어도 관측되지 않으면 자동 해소가 먼저" 로 확정한 닫힘에, 승계는 그 사유를
		// 더 정확하게 만들 뿐이다 — 보관하던 사건이 새 이름으로 이어졌음이 계보로 남는다.
		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d ->
				assertThat(d.getPredecessor()).isSameAs(snoozed));
		assertThat(snoozed.getStatus()).isEqualTo(DriftStatus.RESOLVED);
		assertThat(snoozed.getResolvedBy()).isEqualTo(DriftHandlingAction.SUPERSEDED);
	}

	@Test
	@DisplayName("U8 SUPERSEDED 계약 : 라벨 '재분류로 이어짐', 어떤 종류에도 되돌리기 불가")
	void supersededActionContract() {
		assertThat(DriftHandlingAction.SUPERSEDED.getLabel()).isEqualTo("재분류로 이어짐");
		for (DriftKind kind : DriftKind.values()) {
			assertThat(DriftHandlingAction.SUPERSEDED.reversibleFor(kind))
					.as("SUPERSEDED.reversibleFor(%s)", kind).isFalse();
		}
	}
}
