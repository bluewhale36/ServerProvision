package com.example.serverprovision.management.os.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.lifecycle.IdentityConflictSubject;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;

/**
 * 동일 (OSName, osVersion) 조합이 이미 있을 때 던진다. HF30 부터 휴지통 · Deprecated 행도 정체를 점유한다 — 같은 OS 버전은
 * 새로 등록하지 않고 복구 · Deprecated 해제로 다룬다(메인보드 HF26 과 같은 규칙, DB {@code uk_os_metadata_name_version} 이 안전망).
 * {@link #againstRegistration} 이 기존 행의 상태에 맞는 다음 행동을 문구에 싣는다.
 */
public class DuplicateOSMetadataException extends ConflictException {

	public DuplicateOSMetadataException(OSName osName, String osVersion) {
		super("이미 등록된 OS 버전입니다. %s %s".formatted(osName.getDisplayName(), osVersion));
	}

	private DuplicateOSMetadataException(String message) {
		super(message);
	}

	/** 등록 충돌(HF30) — 기존 행이 휴지통 · Deprecated · 활성 중 어디에 있느냐에 따라 다음 행동을 안내한다. */
	public static DuplicateOSMetadataException againstRegistration(OSMetadata existing) {
		IdentityConflictSubject subject = new IdentityConflictSubject("OS 버전", "OS 버전이", "버전을",
				existing.getOsName().getDisplayName() + " " + existing.getOsVersion());
		return new DuplicateOSMetadataException(
				LifecycleStage.of(existing.isDeprecated(), existing.isDeleted()).registrationConflict(subject));
	}
}
