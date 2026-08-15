package com.example.serverprovision.management.common.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3 — restore 검증 실패 : DB 의 trashed_path 위치에 자원 파일이 부재.
 * <p>외부에서 trash 강제 비우기 등으로 자원이 사라진 상태. 자동 복구 불가.</p>
 *
 * <p>MK4-5-1 — 문구를 두 곳에서 고쳤다. 첫째, 종전 안내는 <b>이 행에 없는 [정리] 버튼</b>을
 * 가리켰다. 그 버튼은 유령 기록 행에만 붙는다. 둘째, 드리프트 종류를 {@code GHOST_DB_ROW} 로
 * 지목했으나 실제로 이 상태를 보고하는 종류는 '휴지통 자원 소실'({@code TRASH_LOST}) 이다.
 * 지금은 자원 무결성 점검으로 보내며, 그쪽에 처리 이력 원장이 있어 처리가 기록으로 남는다.</p>
 *
 * <p>HF-1 (A-1) — superclass 를 {@link NotFoundException}(404) 에서 {@link ConflictException}(409) 로 변경.
 * 형제 3 예외 ({@link RestorePathOccupiedException} / {@link RestoreTargetUnreachableException} /
 * {@code GhostRowRestoreNotAllowedException}) 와 계층 일관성을 맞춘다 — "복구 가능 자원이 없는 상태 충돌" 은
 * 리소스 식별 실패(NotFound)가 아니라 현재 상태와의 충돌(Conflict)이 정확하다.
 * advice 의 {@code ConflictException} 핸들러가 polymorphic 흡수하므로 신규 핸들러 0.</p>
 */
public class RestoreTrashLostException extends ConflictException {

	public RestoreTrashLostException(String trashedPath) {
		super("휴지통에 보관돼 있어야 할 파일이 그 자리에 없습니다 : " + trashedPath
				+ " · 자원 무결성 점검에서 「휴지통 자원 소실」 로 처리해 주세요.");
	}
}
