package com.example.serverprovision.global.trash.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * MK3-1 — Ghost row (DB-truth + FS-truth 양쪽이 음수인 dead row) 에 대한 restore 호출이 들어왔을 때 throw.
 * <p>Ghost 정의 : {@code is_deleted=true AND trashed_at=null AND trashed_path=null AND
 * Files.notExists(DB.resourcePath)}. 복구 가능한 자원이 0 이므로 lifecycle flag flip 만 수행해도 의미 없음.</p>
 *
 * <p>사용자 동선 가이드 :
 * <ul>
 *   <li>휴지통 페이지 (`/maintenance/trash`) 의 ghost 행에서 "정리" 액션</li>
 *   <li>또는 reconciliation 의 GHOST_DB_ROW drift apply</li>
 * </ul>
 */
public class GhostRowRestoreNotAllowedException extends ConflictException {

	public GhostRowRestoreNotAllowedException(String resourceTypeAndId) {
		super("이 자원은 ghost (DB row 만 남은 상태) 입니다. 복구 가능한 자원이 없습니다 — "
					  + resourceTypeAndId
					  + " · 휴지통의 '정리' 또는 reconciliation 의 GHOST_DB_ROW drift apply 로 row 를 제거하세요.");
	}
}
