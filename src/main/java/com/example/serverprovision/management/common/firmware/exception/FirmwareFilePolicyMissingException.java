package com.example.serverprovision.management.common.firmware.exception;

import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.management.board.enums.Vendor;

/**
 * R12-1 — Vendor enum 에 상수가 추가됐는데 매칭되는 FirmwareFilePolicyStrategy 구현체가 없는 코드 누락.
 *
 * <p>운영 환경에 절대 도달하면 안 되는 상황 — 새 vendor 가 enum 에 추가되면 반드시 strategy 도 같이
 * 등록해야 한다는 개발 규칙 강제용. 500 으로 매핑되어
 * 즉시 알려지도록 한다.</p>
 *
 * <p>R12-2 — BIOS · BMC 가 각자의 전략 목록을 갖게 되면서 어느 자원의 정책이 빠졌는지 구분해야 하므로
 * 자원 라벨을 인자로 받는다.</p>
 */
public class FirmwareFilePolicyMissingException extends DomainException {

	public FirmwareFilePolicyMissingException(String resourceLabel, Vendor vendor) {
		super(resourceLabel + " 펌웨어 파일 정책 구현체가 없습니다 (개발 규칙 위반). vendor=" + vendor.name()
				+ " — 새 vendor 를 추가했으면 해당 자원의 FirmwareFilePolicyStrategy 구현체도 함께 등록하세요.");
	}
}
