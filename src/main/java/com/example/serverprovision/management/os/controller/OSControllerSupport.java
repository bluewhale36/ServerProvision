package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.os.dto.response.OSMetadataResponse;
import org.springframework.ui.Model;

/**
 * OS / ISO 컨트롤러 분할 후 공통으로 쓰이는 view 헬퍼.
 *
 * <p>이전엔 {@code OSMetadataController} 안 private static 메서드로 같은 로직을 보관했는데,
 * 컨트롤러를 7 개로 분리하면서 redirect URL 조립 / 폼 컨텍스트 채움 / null 보정이
 * 여러 컨트롤러에 동시에 필요해졌다. CLAUDE.md 의 "중복 로직은 즉시 공통 유틸로 추출" 원칙
 * 에 따라 정적 헬퍼 클래스로 승격.</p>
 */
public final class OSControllerSupport {

	private OSControllerSupport() {
	}

	/**
	 * OS 메타데이터 목록 페이지로 redirect 하되 Miller 의 selectId 를 보존한다.
	 * 단일 자원의 상태 전이 (toggle / restore / deprecate / softDelete 등) 후 호출자가 사용.
	 */
	public static String redirectToListWithSelect(Long selectId) {
		return "redirect:/management/os?selectId=" + selectId;
	}

	/**
	 * 상태 전이 후 목록 페이지 복귀 — Miller 의 3 가지 query state 를 모두 보존한다.
	 *
	 * <ul>
	 *   <li>{@code selectId} : C2 (버전) 선택. row hard-delete (purge) 경로는 null 로 전달</li>
	 *   <li>{@code selectKey} : C1 (OSName) 선택 — 휴지통 모드 on/off 토글 후에도 같은 OSName 그룹 유지</li>
	 *   <li>{@code includeDeleted} : 휴지통 모드. soft-deleted row 를 사용자가 보고 있었다면 그 보기 유지</li>
	 * </ul>
	 *
	 * <p>R1-3 버그 fix — restore / softDelete / toggle / deprecate / undeprecate / purge 후 selectKey / includeDeleted 보존이
	 * 사라지던 회귀 차단.</p>
	 */
	public static String redirectToList(Long selectId, String selectKey, boolean includeDeleted) {
		StringBuilder sb = new StringBuilder("redirect:/management/os");
		boolean first = true;
		if (selectId != null) {
			sb.append("?selectId=").append(selectId);
			first = false;
		}
		if (selectKey != null && !selectKey.isBlank()) {
			sb.append(first ? '?' : '&').append("selectKey=")
					.append(java.net.URLEncoder.encode(selectKey, java.nio.charset.StandardCharsets.UTF_8));
			first = false;
		}
		if (includeDeleted) {
			sb.append(first ? '?' : '&').append("includeDeleted=true");
		}
		return sb.toString();
	}

	/**
	 * ISO 폼 (신규 / 수정) 의 보조 모델 채움. osId 와 contextLabel (예: "Rocky Linux 9.4") 은
	 * 두 폼 모두 필요하고, isoId 는 수정 폼에만 필요해 nullable 로 처리.
	 */
	public static void populateIsoFormContext(Model model, Long osId, Long isoId, OSMetadataResponse os) {
		model.addAttribute("osId", osId);
		if (isoId != null) {
			model.addAttribute("isoId", isoId);
		}
		model.addAttribute("contextLabel", os.osName().getDisplayName() + " " + os.osVersion());
	}

	/**
	 * Thymeleaf 폼 binding 안전화 — null 문자열을 빈 문자열로 변환.
	 */
	public static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
