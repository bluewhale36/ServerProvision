package com.example.serverprovision.management.common.firmware;

import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.common.firmware.exception.FirmwareFilePolicyMissingException;
import com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * R12-2 — 펌웨어 파일 정책 dispatcher 의 공통 본체.
 *
 * <p>vendor 매칭과 뷰 파생 문자열 조립은 자원(BIOS · BMC)이 달라도 동일하므로 여기 한 곳에 둔다.
 * 각 자원의 dispatcher 는 <b>자기 타입의 전략 목록</b>과 <b>자원 라벨</b>만 공급한다 — 목록의 타입이
 * 자원별 마커 인터페이스({@code BiosFirmwareFilePolicyStrategy} 등)이므로 Spring 이 주입 단계에서
 * 갈래를 나눠 주고, BIOS 전략이 BMC 조회에 섞일 수 없다.</p>
 *
 * @param <S> 자원별 마커 인터페이스
 */
public abstract class FirmwareFilePolicySupport<S extends FirmwareFilePolicyStrategy> {

	/**
	 * 이 자원의 전략 목록. 구현체가 생성자 주입으로 받은 목록을 그대로 돌려준다.
	 */
	protected abstract List<S> strategies();

	/**
	 * 전략 누락 예외 메시지에 쓸 자원 이름 (예: {@code "BIOS"} · {@code "BMC"}).
	 */
	protected abstract String resourceLabel();

	/**
	 * 파일명이 해당 vendor 정책에 어긋나면 {@link InvalidFirmwareFileException}(400, 필드 직결) 을 던진다.
	 */
	public void assertAllowed(Vendor vendor, String fileName, String fieldName) {
		resolve(vendor).assertAllowed(fileName, fieldName);
	}

	/**
	 * R12-1 — <b>경로에서 뽑은 파일명</b>에 대한 검사. 위반 문구에 "경로가 파일명으로 해석됐다"는 맥락을
	 * 덧붙인다 — 첨부 파일은 정상인데 경로 때문에 거절되는 상황에서 정책 문구만 보여주면 사용자가
	 * 무엇을 고쳐야 할지 알 수 없기 때문이다(R12-1 CP5 직후 실제로 겪은 혼란).
	 */
	public void assertPathFileNameAllowed(Vendor vendor, String fileName) {
		try {
			resolve(vendor).assertAllowed(fileName, "firmwarePath");
		} catch (InvalidFirmwareFileException e) {
			throw new InvalidFirmwareFileException(
					"경로의 마지막 이름 '" + fileName + "' 이 저장할 파일명으로 해석됐습니다. " + e.getMessage()
							+ " 디렉토리 안에 저장하려면 경로 끝에 / 를 붙이십시오.",
					"firmwarePath"
			);
		}
	}

	/**
	 * 허용 확장자 목록 (소문자 · 점 없음). 경로 해석의 디렉토리 추론에도 쓰인다(R12-2 D8).
	 */
	public List<String> allowedExtensions(Vendor vendor) {
		return resolve(vendor).allowedExtensions();
	}

	/**
	 * 뷰 data 속성용 금지 파일명 CSV (표시 표기). 제약 없는 vendor 는 빈 문자열.
	 */
	public String forbiddenNamesCsv(Vendor vendor) {
		return resolve(vendor).forbiddenNamesCsv();
	}

	/**
	 * 뷰 data 속성용 거절 문구 정본. 제약 없는 vendor 는 빈 문자열.
	 */
	public String forbiddenMessage(Vendor vendor) {
		return resolve(vendor).forbiddenMessage();
	}

	/**
	 * 뷰 data 속성용 허용 확장자 CSV (소문자 · 점 없음 — JavaScript 비교용). 제한 없는 vendor 는 빈 문자열.
	 */
	public String allowedExtensionsCsv(Vendor vendor) {
		return String.join(",", resolve(vendor).allowedExtensions());
	}

	/**
	 * 파일 입력의 accept 속성값 — 허용 확장자를 소문자 · 대문자 두 표기로 나열한다
	 * (브라우저별 대소문자 매칭 차이 방어). 제한 없는 vendor 는 빈 문자열(속성 미표기).
	 */
	public String acceptAttribute(Vendor vendor) {
		return resolve(vendor).allowedExtensions().stream()
				.flatMap(ext -> Stream.of("." + ext, "." + ext.toUpperCase()))
				.collect(Collectors.joining(","));
	}

	/**
	 * 확장자 위반 거절 문구 정본. 제한 없는 vendor 는 빈 문자열.
	 */
	public String invalidExtensionMessage(Vendor vendor) {
		return resolve(vendor).invalidExtensionMessage();
	}

	private S resolve(Vendor vendor) {
		return strategies().stream()
				.filter(s -> s.supports(vendor))
				.findFirst()
				.orElseThrow(() -> new FirmwareFilePolicyMissingException(resourceLabel(), vendor));
	}
}
