package com.example.serverprovision.management.common.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3 — restore 검증 실패 : DB 의 trashed_path 위치에 자원 파일이 부재.
 * <p>외부에서 trash 강제 비우기 등으로 자원이 사라진 상태.
 * 자동 복구 불가 — 휴지통의 '정리' 또는 reconciliation 의 GHOST_DB_ROW drift apply 로 정리해야 한다.</p>
 *
 * <p>HF-1 (A-1) — superclass 를 {@link NotFoundException}(404) 에서 {@link ConflictException}(409) 로 변경.
 * 형제 3 예외 ({@link RestorePathOccupiedException} / {@link RestoreTargetUnreachableException} /
 * {@code GhostRowRestoreNotAllowedException}) 와 계층 일관성을 맞춘다 — "복구 가능 자원이 없는 상태 충돌" 은
 * 리소스 식별 실패(NotFound)가 아니라 현재 상태와의 충돌(Conflict)이 정확하다.
 * advice 의 {@code ConflictException} 핸들러가 polymorphic 흡수하므로 신규 핸들러 0.</p>
 */
public class RestoreTrashLostException extends ConflictException {

	public RestoreTrashLostException(String trashedPath) {
		super("휴지통 자원이 부재합니다 (외부에서 정리된 것으로 보입니다) : " + trashedPath
				+ " · 휴지통의 '정리' 또는 reconciliation 의 GHOST_DB_ROW drift apply 로 정리하세요.");
	}
}
