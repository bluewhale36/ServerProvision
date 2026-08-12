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
import com.example.serverprovision.maintenance.reconciliation.entity.DriftObservation;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftHandlingRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.GhostDbRowClearResolution;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.PathDriftResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * S11-1 — 삭제 자원 판정 진리표의 전수 검증. plan(26-08-08_20-57-13_S11_plan.html §4)의 24칸이
 * 이 클래스의 케이스와 1:1 대응한다 — 표가 바뀌면 여기가 함께 바뀌어야 한다(표 = SSOT).
 *
 * <p>축 정의 — 자원 상태 6종(A 기록없음·원위치본체 / B 유령 / C 정상보관 / D 정상+점유 /
 * E 복귀 / F 소실) × 마커 신호 4종(ⓜ0 없음 / ⓜ1 원위치 / ⓜ2 타위치+본체 / ⓜ3 마커만).
 * 원위치에 본체가 없는 상태(B · C · F)의 원위치 마커는 물리적으로 ⓜ3 과 같은 상태이므로
 * 그 칸의 기대값은 ⓜ3 규칙(미아 마커 병행)을 따른다.</p>
 *
 * <p>합성 규칙 — ① 본체 없는 마커는 자원 상태 판정을 건드리지 않고 미아 마커로 병행 보고.
 * ② 본체 동반 발견은 자원 상태의 부재(소실 F · 유령 B)를 설명할 때만 그 판정을 대체.
 * ③ 원위치 마커는 복귀 판정에 증거로 합류(새 판정을 만들지 않음).</p>
 */
class PathReconciliationTruthTableTest {

	private ProvisionMarkerService markerService;
	private DriftReportRepository driftReportRepository;
	private MarkableScanner isoScanner;
	private com.example.serverprovision.maintenance.reconciliation.service.ReconciliationSettingsService settingsService;
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

		DriftRepository driftRepository = mock(DriftRepository.class);
		given(driftRepository.save(any(Drift.class))).willAnswer(inv -> inv.getArgument(0));
		DriftHandlingRepository driftHandlingRepository = mock(DriftHandlingRepository.class);

		isoScanner = mock(MarkableScanner.class);
		given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);

		// MK4-3-1 — 점검 설정은 이제 데이터베이스에서 온다. 저장소를 비워 두면 서비스가 이관 원본
		// (설정 파일 값)으로 답하므로, 종전 @Value 필드를 세팅하던 자리를 그대로 옮겨 쓸 수 있다.
		var settingRepository = mock(com.example.serverprovision.maintenance.reconciliation.repository.ReconciliationSettingRepository.class);
		given(settingRepository.findById(any())).willReturn(java.util.Optional.empty());
		given(settingRepository.findAll()).willReturn(List.of());
		settingsService = new com.example.serverprovision.maintenance.reconciliation.service.ReconciliationSettingsService(settingRepository);
		service = new PathReconciliationService(
				List.of(isoScanner), markerService, backgroundJobService,
				driftReportRepository, driftRepository, driftHandlingRepository,
				settingsService,
				org.mockito.Mockito.mock(com.example.serverprovision.provisioning.usage.ResourceUsageQuery.class),
				List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
		ReflectionTestUtils.setField(settingsService, "legacyStartupEnabled", true);
		ReflectionTestUtils.setField(settingsService, "legacyRetentionCount", 100);
		ReflectionTestUtils.setField(settingsService, "legacyAutoApplyKindsCsv", "");
		ReflectionTestUtils.setField(settingsService, "legacyExtraRootsCsv", "");
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

	private DriftReport captureSavedReport() {
		var captor = org.mockito.ArgumentCaptor.forClass(DriftReport.class);
		verify(driftReportRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		return captor.getValue();
	}

	private static List<Drift> driftsOf(DriftReport report) {
		return report.getObservations().stream().map(DriftObservation::getDrift).toList();
	}

	/** 마커 배치 축 — 진리표의 마커 신호를 물리 상태로 만든다. */
	enum MarkerPlacement { NONE, AT_ORIGINAL, AT_OTHER_WITH_BODY, AT_OTHER_ONLY }

	/**
	 * 진리표 한 칸. {@code trashRecord}=휴지통 기록, {@code trashAlive}=휴지통 실물,
	 * {@code bodyAtOriginal}=원위치 본체. 기대값은 plan §4 전개표의 해당 행.
	 */
	record Cell(String code, boolean trashRecord, boolean trashAlive, boolean bodyAtOriginal,
			MarkerPlacement marker, DriftKind[] expected) {
		static Cell of(String code, boolean rec, boolean alive, boolean orig,
				MarkerPlacement marker, DriftKind... expected) {
			return new Cell(code, rec, alive, orig, marker, expected);
		}
	}

	static Stream<Arguments> truthTableCells() {
		final DriftKind ORIGINAL = DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL;
		final DriftKind OTHER = DriftKind.SOFTDEL_ESCAPE_TO_OTHER;
		final DriftKind STRAY = DriftKind.SOFTDEL_MARKER_STRAY;
		final DriftKind LOST = DriftKind.TRASH_LOST;
		final DriftKind GHOST = DriftKind.GHOST_DB_ROW;
		return Stream.of(
				// A — 휴지통 기록 없음 · 원위치 본체 있음 → 복귀
				Cell.of("[A · ⓜ0]", false, false, true, MarkerPlacement.NONE, ORIGINAL),
				Cell.of("[A · ⓜ1]", false, false, true, MarkerPlacement.AT_ORIGINAL, ORIGINAL),
				Cell.of("[A · ⓜ2]", false, false, true, MarkerPlacement.AT_OTHER_WITH_BODY, ORIGINAL, OTHER),
				Cell.of("[A · ⓜ3]", false, false, true, MarkerPlacement.AT_OTHER_ONLY, ORIGINAL, STRAY),
				// B — 기록도 본체도 없음 → 유령. 본체 동반 이탈만 유령을 대체(규칙 2)
				Cell.of("[B · ⓜ0]", false, false, false, MarkerPlacement.NONE, GHOST),
				Cell.of("[B · ⓜ1→ⓜ3]", false, false, false, MarkerPlacement.AT_ORIGINAL, GHOST, STRAY),
				Cell.of("[B · ⓜ2]", false, false, false, MarkerPlacement.AT_OTHER_WITH_BODY, OTHER),
				Cell.of("[B · ⓜ3]", false, false, false, MarkerPlacement.AT_OTHER_ONLY, GHOST, STRAY),
				// C — 정상 보관 → 자원 상태 무보고. 마커 신호만 병행
				Cell.of("[C · ⓜ0]", true, true, false, MarkerPlacement.NONE),
				Cell.of("[C · ⓜ1→ⓜ3]", true, true, false, MarkerPlacement.AT_ORIGINAL, STRAY),
				Cell.of("[C · ⓜ2]", true, true, false, MarkerPlacement.AT_OTHER_WITH_BODY, OTHER),
				Cell.of("[C · ⓜ3]", true, true, false, MarkerPlacement.AT_OTHER_ONLY, STRAY),
				// D — 정상 보관 + 원위치 점유. 점유([D · ⓜ1])는 복원 시점 게이트 소관이라 무보고
				Cell.of("[D · ⓜ0]", true, true, true, MarkerPlacement.NONE),
				Cell.of("[D · ⓜ1]", true, true, true, MarkerPlacement.AT_ORIGINAL),
				Cell.of("[D · ⓜ2]", true, true, true, MarkerPlacement.AT_OTHER_WITH_BODY, OTHER),
				Cell.of("[D · ⓜ3]", true, true, true, MarkerPlacement.AT_OTHER_ONLY, STRAY),
				// E — 휴지통 실물 없음 · 원위치 본체 있음 → 복귀. 이탈은 병행(원위치 본체가 부재를 반증)
				Cell.of("[E · ⓜ0]", true, false, true, MarkerPlacement.NONE, ORIGINAL),
				Cell.of("[E · ⓜ1]", true, false, true, MarkerPlacement.AT_ORIGINAL, ORIGINAL),
				Cell.of("[E · ⓜ2]", true, false, true, MarkerPlacement.AT_OTHER_WITH_BODY, ORIGINAL, OTHER),
				Cell.of("[E · ⓜ3]", true, false, true, MarkerPlacement.AT_OTHER_ONLY, ORIGINAL, STRAY),
				// F — 소실. 본체 동반 이탈만 소실을 대체(규칙 2 — 빈 휴지통을 탈출이 설명)
				Cell.of("[F · ⓜ0]", true, false, false, MarkerPlacement.NONE, LOST),
				Cell.of("[F · ⓜ1→ⓜ3]", true, false, false, MarkerPlacement.AT_ORIGINAL, LOST, STRAY),
				Cell.of("[F · ⓜ2]", true, false, false, MarkerPlacement.AT_OTHER_WITH_BODY, OTHER),
				Cell.of("[F · ⓜ3]", true, false, false, MarkerPlacement.AT_OTHER_ONLY, LOST, STRAY)
		).map(c -> Arguments.of(c.code(), c));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("truthTableCells")
	@DisplayName("S11-1 진리표 — 자원 상태 × 마커 신호 24칸 전수")
	void truthTable(String code, Cell cell, @TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Files.createDirectories(orig.getParent());
		Path other = tmp.resolve("backup/dvd.iso");
		Files.createDirectories(other.getParent());
		Path trashDir = tmp.resolve("trash");
		Files.createDirectories(trashDir);

		String trashedPath = cell.trashRecord() ? trashDir.resolve("dvd_x.iso").toString() : null;
		if (cell.trashAlive()) Files.writeString(Path.of(trashedPath), "trash-copy");
		if (cell.bodyAtOriginal()) Files.writeString(orig, "body-at-original");
		switch (cell.marker()) {
			case NONE -> { }
			case AT_ORIGINAL -> writeMarker(orig, 42L);
			case AT_OTHER_WITH_BODY -> {
				Files.writeString(other, "body-at-other");
				writeMarker(other, 42L);
			}
			case AT_OTHER_ONLY -> writeMarker(other, 42L);
		}
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashedPath)));
		ReflectionTestUtils.setField(settingsService, "legacyExtraRootsCsv", tmp.toString());

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport()))
				.as("칸 %s 의 판정", cell.code())
				.extracting(Drift::getKind)
				.containsExactlyInAnyOrder(cell.expected());
	}

	@Test
	@DisplayName("[C · ⓜ2] detail — 정본이 휴지통에 있음을 알리고 회수 대신 택일을 안내한다 (409 안내 정확화)")
	void cellC_m2_detailGuidesChoice(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Path other = tmp.resolve("backup/dvd.iso");
		Path trashed = tmp.resolve("trash/dvd_x.iso");
		Files.createDirectories(orig.getParent());
		Files.createDirectories(other.getParent());
		Files.createDirectories(trashed.getParent());
		Files.writeString(trashed, "trash-copy");
		Files.writeString(other, "body-at-other");
		writeMarker(other, 42L);
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed.toString())));
		ReflectionTestUtils.setField(settingsService, "legacyExtraRootsCsv", tmp.resolve("backup").toString());

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d -> {
			assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_OTHER);
			assertThat(d.getDetail()).contains("정본이 휴지통에 보관되어 있음");
		});
	}

	@Test
	@DisplayName("미아 마커의 경로 — oldPath 는 기대 위치(휴지통 기록 기준), newPath 는 마커 발견 경로")
	void strayMarker_pathAnchors(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Path other = tmp.resolve("backup/dvd.iso");
		Path trashed = tmp.resolve("trash/dvd_x.iso");
		Files.createDirectories(orig.getParent());
		Files.createDirectories(other.getParent());
		Files.createDirectories(trashed.getParent());
		Files.writeString(trashed, "trash-copy");
		writeMarker(other, 42L); // 본체 없는 마커
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed.toString())));
		ReflectionTestUtils.setField(settingsService, "legacyExtraRootsCsv", tmp.resolve("backup").toString());

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d -> {
			assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_MARKER_STRAY);
			assertThat(d.getOldPath()).isEqualTo(trashed.toString());
			assertThat(d.getNewPath()).isEqualTo(other.toString());
		});
	}

	@Test
	@DisplayName("불변식 위반 칸 — trashedAt 만 있고 trashedPath 없음 → TRASH_LOST 보수 판정 (종전 침묵 소멸)")
	void invariantViolation_conservativeTrashLost(@TempDir Path tmp) throws Exception {
		Path orig = tmp.resolve("iso/dvd.iso");
		Files.createDirectories(orig.getParent());
		DeletedIso broken = new DeletedIso(42L, orig, null);
		ReflectionTestUtils.setField(broken, "trashedAt", Instant.now()); // 정상 경로로는 못 만드는 상태
		given(isoScanner.findActiveMarkables()).willReturn(List.of());
		given(isoScanner.findTrashed()).willReturn(List.of(broken));

		ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

		assertThat(driftsOf(captureSavedReport())).singleElement().satisfies(d -> {
			assertThat(d.getKind()).isEqualTo(DriftKind.TRASH_LOST);
			assertThat(d.getDetail()).contains("불변식 위반");
		});
	}
}
