package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S11-1 — 미아 마커(SOFTDEL_MARKER_STRAY) 해결 전략의 단위 검증.
 * 잔여 마커 정리(TrashKindResolutionTest)와 같은 원칙의 활성 트리 판 — 마커만 멱등 삭제,
 * 실행 직전 재확인(본체 출현 · 자원 row 소멸)은 409 로 거절.
 */
class StrayMarkerCleanupResolutionTest {

	private final MarkableScanner scanner = org.mockito.Mockito.mock(MarkableScanner.class);
	private final ProvisionMarkerService markerService = new ProvisionMarkerService();

	private static class DeletedIso extends LifecycleEntity implements Markable {
		private final Long id;
		private final Path path;
		DeletedIso(Long id, Path path, String trashedPath) {
			this.id = id;
			this.path = path;
			softDelete();
			if (trashedPath != null) markTrashed(trashedPath);
		}
		@Override protected Long resourceId() { return id; }
		@Override protected LifecycleEntity parentLifecycle() { return null; }
		@Override public Long getResourceId() { return id; }
		@Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
		@Override public Path getResourcePath() { return path; }
		@Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
		@Override public String getManifestHash() { return "hash-abc"; }
		@Override public String getMarkerSignature() { return "sig"; }
		@Override public void reissueMarker(String h, String sg) { }
	}

	private static Drift strayDriftAt(String expectedPath, String foundPath) {
		return Drift.builder()
				.resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.SOFTDEL_MARKER_STRAY)
				.oldPath(expectedPath).newPath(foundPath)
				.firstDetectedAt(Instant.now()).lastObservedAt(Instant.now())
				.build();
	}

	@Test
	@DisplayName("미아 마커 정리 : 발견 위치의 마커 파일만 삭제 — 휴지통 실물 · 기록 불변, 재실행도 안전(멱등)")
	void strayMarker_deletesMarkerOnly(@TempDir Path tmp) throws Exception {
		Path trashed = tmp.resolve("trash/dvd_x.iso");
		Files.createDirectories(trashed.getParent());
		Files.writeString(trashed, "trash-copy");
		Path foundAt = tmp.resolve("backup/dvd.iso"); // 본체 없는 자리
		Files.createDirectories(foundAt.getParent());
		Path strayMarker = tmp.resolve("backup/dvd.iso.provision.json");
		Files.writeString(strayMarker, "{}");
		DeletedIso resource = new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed.toString());
		org.mockito.BDDMockito.given(scanner.findTrashedById(42L)).willReturn(Optional.of(resource));

		StrayMarkerCleanupResolution resolution = new StrayMarkerCleanupResolution(markerService);
		resolution.resolve(strayDriftAt(trashed.toString(), foundAt.toString()), scanner);

		assertThat(Files.exists(strayMarker)).isFalse();
		assertThat(Files.exists(trashed)).isTrue(); // 휴지통 실물 불변
		assertThat(resource.isDeleted()).isTrue();  // 기록 불변

		// 멱등 — 마커가 이미 없어도 성공 취급
		resolution.resolve(strayDriftAt(trashed.toString(), foundAt.toString()), scanner);
	}

	@Test
	@DisplayName("미아 마커 정리 거절 : 발견 위치에 본체가 나타남(운영자 복원 진행 중) → 409 — 실물의 신원 증명을 떼지 않는다")
	void strayMarker_rejectsWhenBodyAppeared(@TempDir Path tmp) throws Exception {
		Path foundAt = tmp.resolve("backup/dvd.iso");
		Files.createDirectories(foundAt.getParent());
		Files.writeString(foundAt, "body-returned"); // 그 사이 본체 출현
		Path strayMarker = tmp.resolve("backup/dvd.iso.provision.json");
		Files.writeString(strayMarker, "{}");
		org.mockito.BDDMockito.given(scanner.findTrashedById(42L)).willReturn(Optional.of(
				new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), tmp.resolve("trash/x.iso").toString())));

		assertThatThrownBy(() -> new StrayMarkerCleanupResolution(markerService)
				.resolve(strayDriftAt("/trash/x.iso", foundAt.toString()), scanner))
				.isInstanceOf(DriftResolutionNotAllowedException.class)
				.hasMessageContaining("상태가 바뀌어");
		assertThat(Files.exists(strayMarker)).isTrue(); // 마커 보존
	}

	@Test
	@DisplayName("미아 마커 정리 거절 : 자원이 더 이상 휴지통에 없음(복원/영구삭제됨) → 409")
	void strayMarker_rejectsWhenResourceGone() {
		org.mockito.BDDMockito.given(scanner.findTrashedById(42L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> new StrayMarkerCleanupResolution(markerService)
				.resolve(strayDriftAt("/trash/x.iso", "/backup/dvd.iso"), scanner))
				.isInstanceOf(DriftResolutionNotAllowedException.class)
				.hasMessageContaining("상태가 바뀌어");
	}
}
