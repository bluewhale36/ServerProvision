package com.example.serverprovision.management.bios.firmware;

import com.example.serverprovision.management.board.enums.Vendor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * R12-1 — GIGABYTE BIOS 펌웨어 파일명 정책. 검사 알고리즘은 인터페이스 default 가 수행하고
 * 여기는 데이터만 공급한다.
 *
 * <ul>
 *   <li><b>허용 확장자 {@code .RBU} 단일</b> — Redfish SimpleUpdate 실집행 실측이 {@code image.RBU}
 *       로 확정(E0-4 계열)됐고, GIGABYTE BIOS 는 이 형식만 요구한다(사용자 결정 2026-08-20).</li>
 *   <li><b>금지 파일명 {@code PFR1.RBU} · {@code PFR2.RBU}</b> — 벤더 패키지에 정본과 같은 확장자로
 *       동봉되는 PFR active/recovery 사본 경로 전용 파일. Redfish 전송 금지가 실측 확정이라
 *       확장자 검사로는 못 거르므로 파일명으로 조기 차단한다.</li>
 * </ul>
 */
@Component
public class GigabyteFirmwareFilePolicyStrategy implements BiosFirmwareFilePolicyStrategy {

	/** 소문자 비교 기준의 금지 파일명. */
	private static final Set<String> FORBIDDEN_FILENAMES = Set.of("pfr1.rbu", "pfr2.rbu");

	/** 안내 · data 속성용 표시 표기 (실측 패키지의 실제 대문자 표기). */
	private static final List<String> FORBIDDEN_DISPLAY_NAMES = List.of("PFR1.RBU", "PFR2.RBU");

	/** 허용 확장자 (소문자 · 점 없음). */
	private static final List<String> ALLOWED_EXTENSIONS = List.of("rbu");

	/** 거절 문구 정본 — 서버 예외와 화면(JavaScript 사전 검사)이 같은 문장을 쓴다. */
	private static final String FORBIDDEN_MESSAGE =
			"PFR1.RBU · PFR2.RBU 는 PFR 사본 경로 전용 파일로, 등록 대상이 아닙니다. 벤더 패키지의 image.RBU 를 선택하십시오.";

	private static final String INVALID_EXTENSION_MESSAGE =
			"GIGABYTE BIOS 펌웨어는 .RBU 형식 파일만 등록할 수 있습니다.";

	@Override
	public boolean supports(Vendor vendor) {
		return vendor == Vendor.GIGABYTE;
	}

	@Override
	public Set<String> forbiddenFilenames() {
		return FORBIDDEN_FILENAMES;
	}

	@Override
	public List<String> allowedExtensions() {
		return ALLOWED_EXTENSIONS;
	}

	@Override
	public String forbiddenNamesCsv() {
		return String.join(",", FORBIDDEN_DISPLAY_NAMES);
	}

	@Override
	public String forbiddenMessage() {
		return FORBIDDEN_MESSAGE;
	}

	@Override
	public String invalidExtensionMessage() {
		return INVALID_EXTENSION_MESSAGE;
	}
}
