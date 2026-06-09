package com.example.serverprovision.provisioning.exception;

import com.example.serverprovision.global.exception.NotFoundException;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

/**
 * 저장 요청에 담긴 AttributeName 이 레지스트리에 존재하지 않을 때 (404).
 * 레지스트리에 없는 키는 절대 Redfish 페이로드로 전달하지 않는다 (안전 규칙 #1).
 */
public class UnknownBiosAttributeException extends NotFoundException {

	public UnknownBiosAttributeException(BiosAttributeName name) {
		super("레지스트리에 존재하지 않는 BIOS 속성입니다: " + name.value());
	}
}
