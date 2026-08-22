package com.example.serverprovision.management.board.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * 버전 순위 재정렬 요청의 구성 오류(E2-1-a) — 살아있는 행 전체를 정확히 한 번씩 담지 않은 목록
 * (누락 · 중복). 정상 흐름은 목록 화면의 드래그가 항상 전체 목록을 전송하므로 여기 도달하면
 * direct PATCH · stale 화면이다. 타 보드 · 미존재 id 는 404(forging 관례)로 따로 거절된다.
 */
public class InvalidVersionRankRequestException extends FieldBoundBadRequestException {

	private InvalidVersionRankRequestException(String message) {
		super(message, "orderedIds");
	}

	public static InvalidVersionRankRequestException duplicated() {
		return new InvalidVersionRankRequestException("순서 목록에 같은 항목이 두 번 들어 있습니다.");
	}

	public static InvalidVersionRankRequestException incomplete() {
		return new InvalidVersionRankRequestException("순서 목록이 살아있는 버전 전체를 담고 있지 않습니다 — 화면을 새로 고친 뒤 다시 시도하십시오.");
	}
}
