package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 지정 (osMetadataId, isoId) 조합의 ISO 레코드가 없을 때 던진다.
 */
public class ISONotFoundException extends NotFoundException {

	public ISONotFoundException(Long osMetadataId, Long isoId) {
		super("ISO 를 찾을 수 없습니다. osMetadataId=" + osMetadataId + ", isoId=" + isoId);
	}
}
