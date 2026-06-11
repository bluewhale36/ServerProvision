package com.example.serverprovision.provisioning.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * BMC 계정 비밀번호 변경 요청.
 *
 * @param accountId   Redfish 계정 instance ID — {@code GET /redfish/v1/AccountService/Accounts} 의 Members 로 확인 (admin 은 보통 2)
 * @param userName    참고 표시용 계정명 (선택 — 경로/바디에는 쓰이지 않음, 화면 echo 용)
 * @param newPassword 새 비밀번호. 길이 정책은 BMC 가 판정하므로 여기선 공백만 차단
 */
public record BmcPasswordChangeRequest(
		@NotBlank(message = "계정 ID 는 필수입니다.")
		@Pattern(regexp = "\\d+", message = "계정 ID 는 숫자여야 합니다.")
		String accountId,
		String userName,
		@NotBlank(message = "새 비밀번호는 필수입니다.")
		String newPassword
) {
}
