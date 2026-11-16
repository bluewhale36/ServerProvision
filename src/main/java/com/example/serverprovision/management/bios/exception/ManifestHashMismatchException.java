package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * marker 서명은 유효하나 현재 트리에서 재계산한 manifestHash 가 marker 에 기록된 값과 불일치.
 * 트리 내 파일이 외부에서 수정된 상태 (변조 또는 실수에 의한 손상).
 */
public class ManifestHashMismatchException extends ConflictException {

	public ManifestHashMismatchException(String path) {
		super("파일 해시가 마커 기록값과 다릅니다. 변조되거나 손상됐을 수 있습니다: " + path);
	}
}
