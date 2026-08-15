package com.example.serverprovision.global.trash.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3-1 — Ghost row (DB-truth + FS-truth 양쪽이 음수인 dead row) 에 대한 restore 호출이 들어왔을 때 throw.
 * <p>Ghost 정의 : {@code is_deleted=true AND trashed_at=null AND trashed_path=null AND
 * Files.notExists(DB.resourcePath)}. 복구 가능한 자원이 0 이므로 lifecycle flag flip 만 수행해도 의미 없음.</p>
 *
 * <p>사용자 동선 : 휴지통 페이지({@code /maintenance/trash})의 해당 행에서 [정리] 를 누르면 끝난다.
 * 이 행에는 그 버튼이 실제로 붙어 있으므로 다른 화면으로 보내지 않는다.</p>
 *
 * <p>MK4-5-1 — 문구를 화면의 말로 고쳤다. 종전에는 {@code ghost} · {@code row} ·
 * {@code reconciliation} · {@code drift apply} 처럼 <b>화면 어디에도 없는 말</b>로 안내했다.
 * 메뉴 이름은 '자원 무결성 점검' 이고 버튼 이름은 '해결' 이며, 사용자가 보는 것은 자원 표시명이다.
 * 안내가 가리키는 대상이 실재해야 안내가 성립한다.</p>
 */
public class GhostRowRestoreNotAllowedException extends ConflictException {

	public GhostRowRestoreNotAllowedException(String displayName) {
		super("복구할 파일이 없습니다 — " + displayName
					  + " · 이 자원은 기록만 남은 상태입니다. 휴지통 목록의 [정리] 로 기록을 지울 수 있습니다.");
	}
}
