package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.management.os.enums.OSName;

/**
 * 미지원 OS 상수 아래에 ISO 를 새로 등록하려 할 때 던진다(S23-1). 목록 화면은 같은 판정({@link OSName#registrable()})으로
 * [ISO 추가] 를 막으므로 직접 보낸 요청에서만 발생한다.
 */
public class UnsupportedOsIsoRegistrationException extends ConflictException {

	public UnsupportedOsIsoRegistrationException(OSName osName) {
		super("지원하지 않는 OS 에는 ISO 를 등록할 수 없습니다: " + osName.label());
	}
}
