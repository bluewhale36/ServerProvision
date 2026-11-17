package com.example.serverprovision.management.bmc.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.global.lifecycle.IdentityConflictSubject;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bmc.entity.BoardBMC;

/**
 * 같은 BoardModel 범위에서 활성 BMC 의 version 이 중복될 때 던진다.
 * <p>S4 — version 필드 직결.</p>
 */
public class DuplicateBmcVersionException extends FieldBoundConflictException {

	public DuplicateBmcVersionException(Long boardId, String version) {
		super("같은 메인보드에 같은 버전의 BMC 펌웨어가 이미 있습니다: " + version, "version");
	}

	private DuplicateBmcVersionException(String message) {
		super(message, "version");
	}

	/**
	 * 등록 충돌(HF30-2) — 같은 (메인보드, 버전)의 기존 행이 활성 · Deprecated 중 어디에 있느냐에 따라 다음 행동을 안내한다.
	 * 휴지통 행은 막지 않고 중복 확인 창으로 묻는다(파일 자원은 휴지통의 옛것과 새것이 공존할 수 있다).
	 */
	public static DuplicateBmcVersionException againstRegistration(BoardBMC existing) {
		IdentityConflictSubject subject = new IdentityConflictSubject("BMC 버전", "BMC 버전이", "버전을",
				existing.getName() + " " + existing.getVersion());
		return new DuplicateBmcVersionException(
				LifecycleStage.of(existing.isDeprecated(), existing.isDeleted()).registrationConflict(subject));
	}
}
