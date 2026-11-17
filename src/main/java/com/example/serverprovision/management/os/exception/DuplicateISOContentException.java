package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.lifecycle.IdentityConflictSubject;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.global.registration.FailureDisposition;
import com.example.serverprovision.global.registration.RegistrationFailure;

/**
 * 업로드한 ISO 파일의 바이트 내용(SHA-256) 이 같은 OS 의 살아 있는 ISO(활성 · Deprecated)와 동일할 때 던진다.
 * 서로 다른 경로·이름으로 올려도 내용이 같으면 디스크 낭비가 발생하므로 차단하는 것이 목적이다. HF30-2 부터 판정 범위는 같은 OS 다
 * (같은 내용은 OS 버전마다 허용, U4). 휴지통의 같은 내용은 막지 않고 업로드 전 중복 확인 창으로 묻는다.
 */
public class DuplicateISOContentException extends ConflictException implements RegistrationFailure {

	private DuplicateISOContentException(String message) {
		super(message);
	}

	/** 등록 충돌(HF30-2) — 같은 내용의 기존 ISO 가 활성 · Deprecated 중 어디에 있느냐에 따라 다음 행동을 안내한다. */
	public static DuplicateISOContentException againstRegistration(ISO existing) {
		IdentityConflictSubject subject = new IdentityConflictSubject("ISO 내용", "ISO 내용이", "ISO 를",
				existing.getIsoPath());
		return new DuplicateISOContentException(
				LifecycleStage.of(existing.isDeprecated(), existing.isDeleted()).registrationConflict(subject));
	}

	/** 콘텐츠/영구 실패 — finalize 가 이미 업로드 파일을 정리했으므로 격리하지 않는다. */
	@Override
	public FailureDisposition disposition() {
		return FailureDisposition.CLEANUP;
	}
}
