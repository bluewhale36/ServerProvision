package com.example.serverprovision.provisioning.dto.response;

import java.util.List;
import java.util.Map;

/**
 * 저장 echo — GIGABYTE(MegaRAC) Redfish 적용 계획 프리뷰.
 * <ul>
 *   <li>{@code attributes} : {@code PATCH settingsTarget} 의 {@code Attributes} body (Integer=숫자, Enum=문자열, 비밀번호 제외).</li>
 *   <li>{@code passwordChanges} : 비밀번호는 Attributes 가 아니라 {@code POST actionTarget} (Bios.ChangePassword) 로 별도 전송.</li>
 *   <li>{@code resetRequired} 면 {@code resetTarget} 재부팅 후 반영.</li>
 * </ul>
 */
public record BiosSettingsSaveResponse(
		String settingsTarget,
		String settingsMethod,
		Map<String, Object> attributes,
		List<PasswordChangePlan> passwordChanges,
		boolean resetRequired,
		String resetTarget,
		int changedCount
) {

	/** 비밀번호 변경 계획. NewPassword 값은 보안상 담지 않으며, PasswordName(예: "SETUP001") 과 액션 URI 만 표기. */
	public record PasswordChangePlan(String actionTarget, String passwordName) {
	}
}
