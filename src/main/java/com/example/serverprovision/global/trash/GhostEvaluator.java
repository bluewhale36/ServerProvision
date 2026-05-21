package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;

import java.nio.file.Files;

/**
 * MK3-1 — Ghost row 판정 단일 진입점. 4 도메인 (ISO / BIOS / BMC / Subprogram) 의 scanner 가 공통 사용.
 * <p>도메인별 복붙 차단 (CLAUDE.md §중복된 코드 불가침). 4 도메인 entity 가 모두
 * {@link LifecycleEntity} 상속 + {@link Markable} 구현이라는 invariant 활용.</p>
 *
 * <p><b>Ghost 정의 (4 조건 동시 만족)</b> :
 * <ul>
 *   <li>{@code is_deleted = true}</li>
 *   <li>{@code trashed_at = null}</li>
 *   <li>{@code trashed_path = null}</li>
 *   <li>{@code Files.notExists(DB.resourcePath)}</li>
 * </ul>
 *
 * <p>본 클래스는 stateless utility — 도메인 무관. 의존성 주입 불필요하지만 Spring bean 으로 등록해
 * 테스트에서 mock 가능하게 한다.</p>
 */
public final class GhostEvaluator {

	private GhostEvaluator() {
	}

	/**
	 * MK3-1 — 단일 entity 가 ghost 인지 판정. 4 조건 모두 충족하면 true.
	 * <p>Markable + LifecycleEntity 두 인터페이스에서 필요한 모든 정보 추출 가능.</p>
	 */
	public static <T extends LifecycleEntity & Markable> boolean isGhost(T entity) {
		if (entity == null) return false;
		if (!entity.isDeleted()) return false;
		if (entity.getTrashedAt() != null) return false;
		if (entity.getTrashedPath() != null) return false;
		return !Files.exists(entity.getResourcePath());
	}
}
