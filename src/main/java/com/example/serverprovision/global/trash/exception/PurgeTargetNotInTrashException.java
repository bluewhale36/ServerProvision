package com.example.serverprovision.global.trash.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 영구 삭제 대상이 휴지통에 없다(409, HF31) — 활성 자원에 영구 삭제를 direct POST 했거나, 그사이 다른 경로가 먼저 영구 삭제했다.
 *
 * <p>정상 화면 흐름에서는 [영구 삭제] 가 휴지통 행에만 있어 닿지 않는다. 종전에는 자원 화면이 도메인 purge 를 직접 불러 409
 * ("먼저 휴지통으로 이동하세요")가 났는데, 모든 사용자 영구 삭제가 PurgeExecutor 를 거치게 되면서 같은 상황이 503(입출력 실패)으로
 * 바뀌지 않도록 상태 충돌로 둔다. 메시지에 내부 id 를 싣지 않는다.</p>
 */
public class PurgeTargetNotInTrashException extends ConflictException {

	public PurgeTargetNotInTrashException() {
		super("휴지통에 있는 자원만 영구 삭제할 수 있습니다. 목록을 새로 고친 뒤 다시 확인하십시오.");
	}
}
