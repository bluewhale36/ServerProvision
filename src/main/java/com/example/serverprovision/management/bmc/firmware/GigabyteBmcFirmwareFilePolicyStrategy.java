package com.example.serverprovision.management.bmc.firmware;

import com.example.serverprovision.management.board.enums.Vendor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R12-2 — GIGABYTE BMC 펌웨어 파일명 정책. 검사 알고리즘은 공용 인터페이스의 default 가 수행하고
 * 여기는 데이터만 공급한다.
 *
 * <p>허용 확장자는 {@code .ima_enc} 단일이다 — Redfish {@code UpdateService SimpleUpdate} 실집행
 * 실측(E0-4 계열)에서 완주한 형식이 이것뿐이다. 금지 파일명은 실측된 사례가 없어 두지 않는다
 * (BIOS 의 PFR 사본 같은 경우가 관측되면 그때 이 전략에만 추가한다).</p>
 */
@Component
public class GigabyteBmcFirmwareFilePolicyStrategy implements BmcFirmwareFilePolicyStrategy {

	/** 허용 확장자 (소문자 · 점 없음). */
	private static final List<String> ALLOWED_EXTENSIONS = List.of("ima_enc");

	private static final String INVALID_EXTENSION_MESSAGE =
			"GIGABYTE BMC 펌웨어는 .ima_enc 형식 파일만 등록할 수 있습니다.";

	@Override
	public boolean supports(Vendor vendor) {
		return vendor == Vendor.GIGABYTE;
	}

	@Override
	public List<String> allowedExtensions() {
		return ALLOWED_EXTENSIONS;
	}

	@Override
	public String invalidExtensionMessage() {
		return INVALID_EXTENSION_MESSAGE;
	}
}
