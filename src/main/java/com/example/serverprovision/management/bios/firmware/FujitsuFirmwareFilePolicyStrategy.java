package com.example.serverprovision.management.bios.firmware;

import com.example.serverprovision.management.board.enums.Vendor;
import org.springframework.stereotype.Component;

/**
 * R12-1 — Fujitsu BIOS 펌웨어 파일명 정책. 현재 알려진 제약 없음(전부 통과) —
 * 실측으로 제약이 확인되면 해당 검사만 override 한다.
 */
@Component
public class FujitsuFirmwareFilePolicyStrategy implements BiosFirmwareFilePolicyStrategy {

	@Override
	public boolean supports(Vendor vendor) {
		return vendor == Vendor.FUJITSU;
	}
}
