package com.example.serverprovision.provisioning.dto.response;

import java.util.Map;

/**
 * BMC 비밀번호 변경의 Redfish 적용 계획 echo — 실제 호출 없이 "무엇을 보내야 하는지" 를 화면에 보여준다.
 * <ul>
 *   <li>{@code method} + {@code target} : {@code PATCH /redfish/v1/AccountService/Accounts/{id}} (GIGABYTE 매뉴얼 §8-1-1).</li>
 *   <li>{@code headers} : Content-Type + If-Match 사전조건 (AMI MegaRAC 가 428 로 요구할 수 있음).</li>
 *   <li>{@code body} : {@code {"Password": "<새 비밀번호>"}} — 화면 표시용 echo. 서버 로그에는 마스킹되어 기록.</li>
 * </ul>
 */
public record BmcPasswordPlanResponse(
		String method,
		String target,
		Map<String, String> headers,
		Map<String, Object> body,
		String userName,
		String note
) {
}
