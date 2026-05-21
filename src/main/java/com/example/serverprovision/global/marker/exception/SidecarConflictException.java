package com.example.serverprovision.global.marker.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 단일 파일 자원(SIDECAR layout) 등록 시 {@code <basename>.provision.json} 파일이 이미 존재.
 * 다른 자원의 마커가 이미 있거나, 사용자가 수동으로 동일 이름 파일을 만든 경우. → 409
 */
public class SidecarConflictException extends ConflictException {

	public SidecarConflictException(String sidecarPath) {
		super("sidecar 마커 파일이 이미 존재합니다 : " + sidecarPath);
	}
}
