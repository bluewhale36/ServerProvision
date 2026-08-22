package com.example.serverprovision.management.bmc.firmware;

import com.example.serverprovision.management.board.enums.Vendor;
import org.springframework.stereotype.Component;

/**
 * R12-2 — ASUS BMC 펌웨어 파일명 정책. 현재 알려진 제약 없음(전부 통과) —
 * 실측으로 요구 형식이 확인되면 해당 데이터만 override 한다.
 */
@Component
public class AsusBmcFirmwareFilePolicyStrategy implements BmcFirmwareFilePolicyStrategy {

	@Override
	public boolean supports(Vendor vendor) {
		return vendor == Vendor.ASUS;
	}
}
