package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.management.board.enums.Vendor;

/**
 * S5-11 v2 — 특정 vendor 의 자동 진입점 탐지 정책이 정의되지 않음.
 *
 * <p>현재 Fujitsu 가 해당. iRMC 기반 별 흐름으로 처리될 예정이라 본 슬라이스 범위 외. silent fail 대신
 * explicit 예외로 사용자에게 명확 안내 — "이 vendor 는 자동 탐지 미지원, override 또는 운영자 상의 필요".</p>
 */
public class EntrypointDetectionNotSupportedException extends ConflictException {

	public EntrypointDetectionNotSupportedException(Vendor vendor) {
		super("진입점 자동 탐지가 지원되지 않는 제조사입니다 : " + vendor.getDisplayName()
				+ ". 진입점을 명시 지정하거나 운영자와 상의하세요.");
	}
}
