package com.example.serverprovision.management.board.enums;

import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.VendorNotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * 메인보드 제조사. A2 MVP 는 실제 보유 장비 기준 3종.
 * 새 제조사를 지원하려면 이 열거형에 상수를 추가한다 — 런타임 등록은 지원하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum Vendor {

	/**
	 * Gigabyte 는 iPXE {@code ${product}} 에 카탈로그 모델명 뒤로 {@code -000} 접미사를 붙여 보고한다
	 * (ex. {@code MS03-CE0} 모델 → {@code MS03-CE0-000}). 정규화 시 이 접미사만 제거한다.
	 */
	GIGABYTE("Gigabyte", "Giga Computing") {
		@Override
		public String canonicalizeReportedModel(String reportedModel) {
			// 보고 접미는 "-숫자열" — 실측 -000(MS03·MS73·MS74) · -00(MD72-HB3, 2026-08-27). 3자리 고정이 MD72 를 놓쳤다.
			return reportedModel == null ? null
					: GIGABYTE_REPORT_SUFFIX.matcher(reportedModel.trim()).replaceFirst("");
		}
	},
	ASUS("Asus", "Asus"),
	FUJITSU("Fujitsu", "FUJITSU");

	private static final java.util.regex.Pattern GIGABYTE_REPORT_SUFFIX = java.util.regex.Pattern.compile("-\\d+$");

	private final String displayName;
	private final String ipxeName;

	public static Vendor findByIpxeName(String ipxeNameStr) {
		return Arrays.stream(Vendor.values())
				.filter(v -> v.ipxeName.equals(ipxeNameStr))
				.findFirst()
				.orElseThrow(
						() -> new VendorNotFoundException(ipxeNameStr)
				);
	}

	/**
	 * iPXE 가 보고한 {@code ${product}} 문자열을 카탈로그 모델명 형태로 정규화한다.
	 * <p>제조사마다 보고 규약이 달라 method-per-constant 다형으로 둔다. 기본은 항등(trim 만) — 규약이 관측된
	 * 제조사만 override 한다(예: {@link #GIGABYTE}). 정규화는 보수적으로 — 알려진 접미사만 제거하고 임의 절단은 하지 않는다.</p>
	 * <p>호출 측은 <b>원본 exact 매칭을 먼저</b> 시도하고 miss 일 때만 본 정규화값으로 재시도해야 한다
	 * (실제로 접미사로 끝나는 모델을 과잉 제거하는 사고를 막기 위함).</p>
	 */
	public String canonicalizeReportedModel(String reportedModel) {
		return reportedModel == null ? null : reportedModel.trim();
	}
}
