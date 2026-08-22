package com.example.serverprovision.management.common.firmware;

import com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException;
import com.example.serverprovision.management.board.enums.Vendor;

import java.util.List;
import java.util.Set;

/**
 * R12-1 — 제조사별 펌웨어 파일명 정책. R12-2 에서 BIOS · BMC 공용으로 끌어올렸다.
 *
 * <p>금지 파일명 · 허용 확장자 같은 제약은 제조사 패키지 관례에서 기원한다(예: GIGABYTE 벤더 패키지의
 * {@code PFR1.RBU} · {@code PFR2.RBU}, GIGABYTE 의 {@code .RBU} 단일 형식). 이를 단일 클래스에
 * 하드코딩하면 다른 제조사 파일에도 불필요하게 같은 제약이 강제되므로,
 * vendor 매칭 SPI(Service Provider Interface —
 * 도메인이 구현해 끼우는 확장점)로 분리한다. 새 vendor 를 {@link Vendor} enum 에 추가하면 본 인터페이스
 * 구현체도 함께 등록해야 한다 — 누락 시
 * {@link com.example.serverprovision.management.common.firmware.exception.FirmwareFilePolicyMissingException}
 * 으로 즉시 드러난다.</p>
 *
 * <p>검사 알고리즘은 {@link #assertAllowed} 의 default 본문 한 곳에만 있고, 각 vendor 구현체는
 * <b>데이터</b>(금지 목록 · 허용 확장자 · 문구)만 공급한다. 제약이 없는 제조사는 default(빈 목록 = 통과)를
 * 그대로 쓴다 — 구현체는 {@link #supports(Vendor)} 만 선언하면 된다.</p>
 */
public interface FirmwareFilePolicyStrategy {

	/**
	 * 본 strategy 가 주어진 vendor 의 펌웨어 파일명 정책을 담당하는지.
	 */
	boolean supports(Vendor vendor);

	/**
	 * 소문자 비교 기준의 금지 파일명. 빈 집합 = 금지 없음.
	 */
	default Set<String> forbiddenFilenames() {
		return Set.of();
	}

	/**
	 * 허용 확장자(소문자 · 점 없음). 빈 목록 = 확장자 제한 없음.
	 */
	default List<String> allowedExtensions() {
		return List.of();
	}

	/**
	 * 뷰 data 속성용 금지 파일명 CSV (표시 표기). 제약이 없으면 빈 문자열.
	 */
	default String forbiddenNamesCsv() {
		return "";
	}

	/**
	 * 금지 파일명 거절 문구 정본 — 서버 예외와 화면(JavaScript 사전 검사)이 같은 문장을 쓴다.
	 */
	default String forbiddenMessage() {
		return "";
	}

	/**
	 * 확장자 위반 거절 문구 정본. 확장자 제한이 없으면 빈 문자열.
	 */
	default String invalidExtensionMessage() {
		return "";
	}

	/**
	 * 파일명이 정책에 어긋나면 {@link InvalidFirmwareFileException}(400, 필드 직결) 을 던진다.
	 * 검사 순서 = 금지 파일명 → 허용 확장자. 알고리즘은 이 default 본문이 유일하다 —
	 * vendor 구현체는 데이터 메서드만 override 한다.
	 *
	 * @param fileName  검사 대상 파일명 (경로 아님). null · blank 는 검사 대상 없음으로 통과 —
	 *                  필수 여부는 호출측 검증(경로 해석 · 파일 실재)이 관장한다.
	 * @param fieldName 위반 시 오류를 표시할 폼 필드명 (예: {@code firmwareFile} · {@code firmwarePath})
	 */
	default void assertAllowed(String fileName, String fieldName) {
		if (fileName == null || fileName.isBlank()) {
			return;
		}
		String lower = fileName.toLowerCase();
		if (forbiddenFilenames().contains(lower)) {
			throw new InvalidFirmwareFileException(forbiddenMessage(), fieldName);
		}
		List<String> allowed = allowedExtensions();
		if (!allowed.isEmpty()) {
			int dot = lower.lastIndexOf('.');
			String extension = dot < 0 ? "" : lower.substring(dot + 1);
			if (!allowed.contains(extension)) {
				throw new InvalidFirmwareFileException(invalidExtensionMessage(), fieldName);
			}
		}
	}
}
