package com.example.serverprovision.management.raidcard.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 지정 ID 의 RAID 카드가 존재하지 않을 때 던진다.
 * soft 삭제된 레코드에 대해 수정/토글/삭제를 시도하는 경우에도 NotFound 로 취급한다.
 */
public class RaidCardNotFoundException extends NotFoundException {

	public RaidCardNotFoundException(Long id) {
		super("RAID 카드를 찾을 수 없습니다. id=" + id);
	}
}
