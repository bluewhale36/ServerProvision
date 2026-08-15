package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MK4-5-1 — 복원을 막아야 하는가의 단일 판정. 목록 렌더와 서버 가드가 이 하나를 함께 부른다.
 *
 * <p><b>근거는 실시간 파일시스템이다. 드리프트 조회가 아니다.</b> 자원 무결성 점검이 '휴지통 자원
 * 소실' 을 이미 보고하고 있으므로 그 기록을 읽는 안도 있었으나, 그러면 판정이 점검 주기에 매인다 —
 * 마지막 점검 뒤에 파일이 사라졌으면 차단이 걸리지 않고, 반대로 파일이 돌아왔으면 거짓 차단이
 * 남는다. 서버 가드가 보는 사실을 화면도 직접 보면 결과는 같으면서 낡지 않는다.
 * 비용 증분도 사실상 없다 — 유령 판정이 이미 삭제 자원마다 {@code Files.exists} 를 부르므로
 * 같은 순회에 호출 하나가 늘 뿐이다.</p>
 *
 * <p>{@link GhostEvaluator} 를 대체하지 않고 부른다. {@code isGhost} 호출부가 스무 곳이 넘어
 * 이름을 바꾸거나 흡수하면 churn 이 이득을 넘는다.</p>
 */
public final class TrashRestoreEvaluator {

	private TrashRestoreEvaluator() {
	}

	/**
	 * 화면이 묻는 것 — 이 행의 [복원] 을 지금 눌러도 반드시 실패하는가. 막을 사유가 있으면 그
	 * 사유를, 없으면 {@code null} 을 돌려준다.
	 *
	 * <p><b>축이 둘이다.</b> 부모가 삭제됐는지는 기록의 축이고, 실물이 어디 있는지는 파일의 축이다.
	 * 둘 중 부모를 먼저 보는 이유는 실물이 온전해도 자식만 단독으로는 되살릴 수 없기 때문이다 —
	 * 파일 상태를 먼저 말하면 사용자가 파일을 살펴보고 와서 다시 막힌다.</p>
	 *
	 * <p>서버 가드는 이 메서드가 아니라 {@link #evaluateFileState}를 부른다. 가드가 묻는 것은
	 * "이 물리 상태가 소실인가" 라서 부모 축이 답을 가리면 안 되기 때문이다. 실물 조건 자체는
	 * 한 곳(그 메서드)에만 있으므로 둘이 갈라지지 않는다.</p>
	 */
	public static RestoreBlockReason evaluate(Markable entity) {
		if (!(entity instanceof LifecycleEntity)) return null;
		if (isParentDeleted(entity)) return RestoreBlockReason.PARENT_DELETED;
		return evaluateFileState(entity);
	}

	/**
	 * 서버 가드가 묻는 것 — 실물이 어디에 있는가만 본다. 부모 축은 도메인 lifecycle 이 이미
	 * {@code blocksChildRestore()} 로 거절하므로 여기 오지 않는다.
	 *
	 * <p>복원 가능성 진리표 여섯 칸 중 <b>2 행(유령 기록)과 5 행(휴지통 자원 소실)</b>만 막는다.
	 * 4 행(원위치에 실물이 있고 휴지통이 비었음)은 반쪽 복원의 잔여라 서버가 기록만 맞춰 스스로
	 * 낫고, 6 행(원위치 점유)은 복원할 대상이 있으므로 성질이 다르다 — 막을 수는 있으나 막은 뒤
	 * 안내할 곳이 없어 MK4-5-2 로 넘겼다.</p>
	 */
	public static RestoreBlockReason evaluateFileState(Markable entity) {
		if (!(entity instanceof LifecycleEntity lifecycle)) return null;
		if (isGhost(entity)) return RestoreBlockReason.GHOST;

		String trashedPath = lifecycle.getTrashedPath();
		// 메타 자원은 휴지통 실물이 없는 것이 정상이라 이 축의 판정 대상이 아니다.
		if (trashedPath == null) return null;
		// 휴지통에 실물이 있으면 정상 보관이거나 원위치 점유다 — 둘 다 이 축에서는 막지 않는다.
		if (Files.exists(Path.of(trashedPath))) return null;
		// 휴지통은 비었어도 원위치에 실물이 있으면 반쪽 복원 잔여다. 서버가 기록만 맞추면 낫는다.
		if (Files.exists(entity.getResourcePath())) return null;
		return RestoreBlockReason.TRASH_LOST;
	}

	/**
	 * 부모가 삭제 상태인가. 부모가 없는 자원은 이 축에 걸리지 않는다.
	 *
	 * <p>부모 관계를 {@link Markable} 다형성으로 읽으므로 도메인 분기가 없다. 목록 렌더는 이미
	 * 행마다 부모를 조회하고 있어 추가 비용이 생기지 않는다.</p>
	 */
	private static boolean isParentDeleted(Markable entity) {
		return entity.getParentMarkable()
				.filter(LifecycleEntity.class::isInstance)
				.map(parent -> ((LifecycleEntity) parent).isDeleted())
				.orElse(false);
	}

	/**
	 * 유령 판정을 {@link GhostEvaluator} 에 위임한다. 그 메서드가 교차 타입
	 * ({@code LifecycleEntity & Markable})을 요구하는데 호출부는 {@link Markable} 만 들고 있어,
	 * 좁히는 일을 여기 한 곳에 가둔다. 두 조건은 바로 위에서 확인됐다.
	 */
	@SuppressWarnings("unchecked")
	private static <T extends LifecycleEntity & Markable> boolean isGhost(Markable entity) {
		return GhostEvaluator.isGhost((T) entity);
	}
}
