package com.example.serverprovision.management.bmc.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 요청 경로의 BMC 레코드가 없거나 부모 BoardModel 에 속하지 않을 때 던진다.
 */
public class BmcNotFoundException extends NotFoundException {

	public BmcNotFoundException(Long boardId, Long bmcId) {
		super("BMC 펌웨어를 찾을 수 없습니다. boardId=" + boardId + ", bmcId=" + bmcId);
	}
}
