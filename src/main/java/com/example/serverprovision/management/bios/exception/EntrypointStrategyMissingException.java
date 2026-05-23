package com.example.serverprovision.management.bios.exception;

import com.example.serverprovision.global.exception.DomainException;
import com.example.serverprovision.management.board.enums.Vendor;

/**
 * S5-11 v2 — Vendor enum 에 상수가 추가됐는데 매칭되는 EntrypointDetectionStrategy 구현체가 없는 코드 누락.
 *
 * <p>운영 환경에 절대 도달하면 안 되는 상황 — 새 vendor 가 enum 에 추가되면 반드시 strategy 도 같이 등록해야
 * 한다는 개발 규칙 강제용. 500 으로 매핑되어 즉시 알려지도록 한다.</p>
 */
public class EntrypointStrategyMissingException extends DomainException {

	public EntrypointStrategyMissingException(Vendor vendor) {
		super("EntrypointDetectionStrategy 구현체가 없습니다 (개발 규칙 위반). vendor=" + vendor.name()
				+ " — 새 vendor 를 추가했으면 EntrypointDetectionStrategy 구현체도 함께 등록하세요.");
	}
}
