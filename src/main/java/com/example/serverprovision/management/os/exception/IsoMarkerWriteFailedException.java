package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.global.orphan.enums.OrphanFailureClass;
import com.example.serverprovision.global.registration.FailureDisposition;
import com.example.serverprovision.global.registration.RegistrationFailure;

/**
 * ISO 등록 마지막 단계({@code finalizeMarker})에서 sidecar 마커(.provision.json) 기록이 IO 등으로 실패한 경우.
 * <p>이 시점엔 ISO 파일이 디스크에 있으므로(DB row 는 트랜잭션 롤백), 처분이 QUARANTINE 으로 분류돼
 * 파일을 삭제하지 않고 격리한다. 별도 wrap 이 없으면 raw RuntimeException 으로 500 만 나가 분류가 불가능했다.</p>
 */
public class IsoMarkerWriteFailedException extends DomainException implements RegistrationFailure {

	public IsoMarkerWriteFailedException(String message, Throwable cause) {
		super(message, cause);
	}

	/** 인프라/일시 실패 — 파일 보존 + 격리 (마커 기록). */
	@Override
	public FailureDisposition disposition() {
		return new FailureDisposition.Quarantine(OrphanFailureClass.MARKER_WRITE);
	}
}
