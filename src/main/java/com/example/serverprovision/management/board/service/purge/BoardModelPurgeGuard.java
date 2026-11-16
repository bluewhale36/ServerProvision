package com.example.serverprovision.management.board.service.purge;

import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.service.BoardScopedChildLifecycle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * "이 메인보드를 지워도 되나" 의 단일 판정(HF26) — 영구 삭제 서버 가드와 [영구 삭제] 버튼 사전 차단이 같은 문구를 쓴다.
 *
 * <p>판정 재료는 둘이다. 보드와 함께 휴지통에 가는 자식(BIOS · BMC · 드라이버 · 유틸리티)과, 보드를 참조하지만 따라가지
 * 않는 기록({@link BoardModelPurgeBlocker} 구현 — 서버 하드웨어 기록 · BIOS 세팅 템플릿). 이전에는 자식만 보았고, DB 가
 * 나머지를 ON DELETE CASCADE 로 함께 지웠다.</p>
 */
@Component
@RequiredArgsConstructor
public class BoardModelPurgeGuard {

	private static final String CHILD_RESOURCES = "하위 BIOS · BMC · 드라이버 · 유틸리티 자원";

	private final List<BoardScopedChildLifecycle> children;
	private final List<BoardModelPurgeBlocker> blockers;

	/** 막는 사유 문구 — 지워도 되면 {@code null}. 화면 tooltip 이자 서버 409 사유. */
	@Transactional(readOnly = true)
	public String reasonFor(Long boardModelId) {
		List<String> remaining = new ArrayList<>();
		if (children.stream().anyMatch(child -> child.hasAny(boardModelId))) {
			remaining.add(CHILD_RESOURCES);
		}
		blockers.stream()
				.map(blocker -> blocker.remainingReference(boardModelId))
				.filter(Objects::nonNull)
				.forEach(remaining::add);
		return remaining.isEmpty()
				? null
				: "이 메인보드 모델을 참조하는 다음 항목이 남아 있어 영구 삭제할 수 없습니다: " + String.join(", ", remaining) + ".";
	}

	/** 서버 가드 — 정상 흐름에서는 버튼이 먼저 막으므로 direct POST · 동시 조작에서만 발동한다. */
	public void assertPurgeable(Long boardModelId) {
		String reason = reasonFor(boardModelId);
		if (reason != null) {
			throw new IllegalBoardModelStateException(reason);
		}
	}
}
