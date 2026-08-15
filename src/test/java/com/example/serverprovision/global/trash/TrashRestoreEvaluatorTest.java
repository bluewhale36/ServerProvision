package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MK4-5-1 — 복원 가능성 진리표의 전수 검증. plan(26-08-15_00-41-15_MK4-5-1_plan.html §4)의 여섯 행이
 * 이 클래스의 케이스와 1:1 대응한다 — 표가 바뀌면 여기가 함께 바뀌어야 한다(표 = SSOT).
 *
 * <p>축 정의 — {@code trashed_path} 기록 유무 × 원위치 파일 유무 × 휴지통 파일 유무. 부모 삭제는
 * 직교 축이라 별도 케이스로 둔다.</p>
 *
 * <p>{@link TrashRestoreEvaluator#evaluate} 와 {@link TrashRestoreEvaluator#evaluateFileState} 를
 * 모두 검증하는 이유는 <b>둘이 서로 다른 질문에 답하기 때문</b>이다. 화면은 부모 축을 포함해 묻고,
 * 서버 가드는 실물 축만 묻는다. 부모가 삭제된 채 실물까지 사라진 자원에서 두 답이 갈리는 것이
 * 정상이며, 이를 뭉개면 가드가 소실을 못 알아보고 없는 파일을 옮기려 든다.</p>
 */
class TrashRestoreEvaluatorTest {

	// ── 진리표 여섯 행 ────────────────────────────────────────────────

	@Test
	@DisplayName("1행 : 기록 없음 · 원위치에 실물 있음(외부 복귀) → 막지 않는다")
	void row1_externalReturn_notBlocked(@TempDir Path tmp) throws Exception {
		Path body = tmp.resolve("back.iso");
		Files.writeString(body, "x");
		TestEntity e = deleted(body);

		assertThat(TrashRestoreEvaluator.evaluateFileState(e)).isNull();
	}

	@Test
	@DisplayName("2행 : 기록 없음 · 실물도 없음(유령 기록) → 막는다")
	void row2_ghost_blocked(@TempDir Path tmp) {
		TestEntity e = deleted(tmp.resolve("gone.iso"));

		assertThat(TrashRestoreEvaluator.evaluateFileState(e)).isEqualTo(RestoreBlockReason.GHOST);
	}

	@Test
	@DisplayName("3행 : 기록 있음 · 휴지통에 실물 있음(정상 보관) → 막지 않는다")
	void row3_normalKeep_notBlocked(@TempDir Path tmp) throws Exception {
		Path trashed = tmp.resolve("kept.iso");
		Files.writeString(trashed, "x");
		TestEntity e = trashed(tmp.resolve("original.iso"), trashed);

		assertThat(TrashRestoreEvaluator.evaluateFileState(e)).isNull();
	}

	@Test
	@DisplayName("4행 : 기록 있음 · 휴지통 비었으나 원위치에 실물(반쪽 복원 잔여) → 막지 않는다")
	void row4_partialRestoreRemnant_notBlocked(@TempDir Path tmp) throws Exception {
		Path original = tmp.resolve("original.iso");
		Files.writeString(original, "x");
		TestEntity e = trashed(original, tmp.resolve("gone-from-trash.iso"));

		assertThat(TrashRestoreEvaluator.evaluateFileState(e)).isNull();
	}

	@Test
	@DisplayName("5행 : 기록 있음 · 양쪽 모두 실물 없음(휴지통 자원 소실) → 막는다")
	void row5_trashLost_blocked(@TempDir Path tmp) {
		TestEntity e = trashed(tmp.resolve("original.iso"), tmp.resolve("gone.iso"));

		assertThat(TrashRestoreEvaluator.evaluateFileState(e))
				.isEqualTo(RestoreBlockReason.TRASH_LOST);
	}

	@Test
	@DisplayName("6행 : 기록 있음 · 양쪽 모두 실물 있음(원위치 점유) → 이 슬라이스는 막지 않는다")
	void row6_pathOccupied_notBlockedHere(@TempDir Path tmp) throws Exception {
		Path original = tmp.resolve("original.iso");
		Path trashedPath = tmp.resolve("kept.iso");
		Files.writeString(original, "occupier");
		Files.writeString(trashedPath, "mine");
		TestEntity e = trashed(original, trashedPath);

		// 막을 수는 있으나 막은 뒤 안내할 곳이 없어 MK4-5-2 로 넘겼다(plan §8 D8).
		// 이 단정이 깨지면 그 결정이 뒤집힌 것이므로 plan 과 함께 고쳐야 한다.
		assertThat(TrashRestoreEvaluator.evaluateFileState(e)).isNull();
	}

	// ── 직교 축 : 부모 삭제 ──────────────────────────────────────────

	@Test
	@DisplayName("부모가 삭제 상태면 실물이 온전해도 화면은 막는다")
	void parentDeleted_blocksView(@TempDir Path tmp) throws Exception {
		Path trashedPath = tmp.resolve("kept.iso");
		Files.writeString(trashedPath, "x");
		TestEntity child = trashed(tmp.resolve("original.iso"), trashedPath);
		child.parent = deletedParent();

		assertThat(TrashRestoreEvaluator.evaluate(child))
				.isEqualTo(RestoreBlockReason.PARENT_DELETED);
	}

	@Test
	@DisplayName("부모 삭제가 실물 판정을 가리지 않는다 — 가드는 여전히 소실을 본다")
	void parentDeleted_doesNotMaskFileState(@TempDir Path tmp) {
		TestEntity child = trashed(tmp.resolve("original.iso"), tmp.resolve("gone.iso"));
		child.parent = deletedParent();

		// 화면은 부모부터 말하고,
		assertThat(TrashRestoreEvaluator.evaluate(child))
				.isEqualTo(RestoreBlockReason.PARENT_DELETED);
		// 가드는 실물이 없다는 사실을 그대로 본다. 이 둘이 같아지면 가드가 소실을 놓쳐
		// 없는 파일을 옮기려 든다.
		assertThat(TrashRestoreEvaluator.evaluateFileState(child))
				.isEqualTo(RestoreBlockReason.TRASH_LOST);
	}

	@Test
	@DisplayName("부모가 살아 있으면 부모 축에 걸리지 않는다")
	void parentAlive_notBlocked(@TempDir Path tmp) throws Exception {
		Path trashedPath = tmp.resolve("kept.iso");
		Files.writeString(trashedPath, "x");
		TestEntity child = trashed(tmp.resolve("original.iso"), trashedPath);
		child.parent = new TestEntity(1L, tmp.resolve("parent"));

		assertThat(TrashRestoreEvaluator.evaluate(child)).isNull();
	}

	// ── 경계 ────────────────────────────────────────────────────────

	@Test
	@DisplayName("메타 자원처럼 휴지통 기록이 없는 정상 상태는 실물 축에 걸리지 않는다")
	void metadataWithoutTrashedPath_notBlocked(@TempDir Path tmp) throws Exception {
		Path body = tmp.resolve("meta");
		Files.createDirectories(body);
		TestEntity e = new TestEntity(7L, body);
		e.softDelete();
		e.markTrashed(null);

		assertThat(TrashRestoreEvaluator.evaluateFileState(e)).isNull();
	}

	@Test
	@DisplayName("null 은 판정 대상이 아니다")
	void nullEntity() {
		assertThat(TrashRestoreEvaluator.evaluate(null)).isNull();
		assertThat(TrashRestoreEvaluator.evaluateFileState(null)).isNull();
	}

	// ── 픽스처 ──────────────────────────────────────────────────────

	/** 소프트 삭제됐고 휴지통 기록이 없는 상태 (진리표 1 · 2 행). */
	private static TestEntity deleted(Path resourcePath) {
		TestEntity e = new TestEntity(99L, resourcePath);
		e.softDelete();
		return e;
	}

	/** 소프트 삭제됐고 휴지통 기록이 있는 상태 (진리표 3 ~ 6 행). */
	private static TestEntity trashed(Path resourcePath, Path trashedPath) {
		TestEntity e = new TestEntity(99L, resourcePath);
		e.softDelete();
		e.markTrashed(trashedPath.toString());
		return e;
	}

	private static TestEntity deletedParent() {
		TestEntity parent = new TestEntity(1L, Path.of("/nowhere/parent"));
		parent.softDelete();
		return parent;
	}

	/** 테스트 전용 entity — lifecycle 메서드와 Markable 시그니처만 노출. */
	private static class TestEntity extends LifecycleEntity implements Markable {
		private final Long id;
		private final Path path;
		private Markable parent;

		TestEntity(Long id, Path path) {
			this.id = id;
			this.path = path;
		}

		@Override protected Long resourceId() { return id; }
		@Override protected LifecycleEntity parentLifecycle() { return null; }
		@Override public Long getResourceId() { return id; }
		@Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
		@Override public Path getResourcePath() { return path; }
		@Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
		@Override public String getManifestHash() { return "h"; }
		@Override public String getMarkerSignature() { return null; }
		@Override public void reissueMarker(String hash, String signature) {}
		@Override public Optional<Markable> getParentMarkable() { return Optional.ofNullable(parent); }
	}
}
