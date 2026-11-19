package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.FieldBoundConflictException;
import com.example.serverprovision.global.lifecycle.IdentityConflictSubject;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.vo.BoardScope;

/**
 * 같은 (kind, boardScope, name, version) 활성 자원이 이미 존재할 때 던진다.
 * <p>S4 — version 필드 직결.</p>
 */
public class DuplicateSubprogramVersionException extends FieldBoundConflictException {

	public DuplicateSubprogramVersionException(SubprogramKind kind, BoardScope scope, String name, String version) {
		super(
				"같은 적용 범위에 이름과 버전이 같은 " + kind.getDisplayName() + "가 이미 있습니다.", "version"
		);
	}

	private DuplicateSubprogramVersionException(String message) {
		super(message, "version");
	}

	/**
	 * 등록 충돌(HF30-2) — 같은 (종류, 범위, 이름, 버전)의 기존 행이 활성 · Deprecated 중 어디에 있느냐에 따라 다음 행동을 안내한다.
	 * 휴지통 행은 막지 않고 중복 확인 창으로 묻는다. 종류 이름(드라이버 · 유틸리티)은 모두 모음으로 끝나 주격 조사는 '가' 다.
	 */
	public static DuplicateSubprogramVersionException againstRegistration(Subprogram existing) {
		String noun = existing.getKind().getDisplayName();
		IdentityConflictSubject subject = new IdentityConflictSubject(noun, noun + "가", "버전을",
				existing.getName() + " " + existing.getVersion());
		return new DuplicateSubprogramVersionException(
				LifecycleStage.of(existing.isDeprecated(), existing.isDeleted()).registrationConflict(subject));
	}
}
