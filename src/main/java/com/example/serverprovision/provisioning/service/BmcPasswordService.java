package com.example.serverprovision.provisioning.service;

import com.example.serverprovision.provisioning.dto.request.BmcPasswordChangeRequest;
import com.example.serverprovision.provisioning.dto.response.BmcPasswordPlanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BMC 계정 비밀번호 변경의 Redfish 적용 계획 산출 (실제 호출 없음 — BIOS 속성 흐름과 동일하게 로그만).
 * <p>GIGABYTE 매뉴얼(server_manual_redfish §8-1-1 Accounts) 기준:
 * {@code PATCH /redfish/v1/AccountService/Accounts/{instance}} 에 {@code {"Password": "<새 값>"}}.
 * BIOS 설정과 달리 BMC 계정 변경은 재부팅 없이 즉시 적용된다.</p>
 * <p>보안: 비밀번호 실값은 HTTP 응답(사용자 본인 입력의 echo)에만 담고, 서버 로그에는 마스킹해 기록한다
 * (CLAUDE.md §아키텍처 — 자격 증명 로그 노출 금지).</p>
 */
@Service
public class BmcPasswordService {

	/** BMC 계정 리소스 — {instance} 는 숫자 계정 ID (admin 은 통상 2). */
	public static final String ACCOUNT_TARGET_BASE = "/redfish/v1/AccountService/Accounts/";
	public static final String METHOD = "PATCH";

	private static final Logger log = LoggerFactory.getLogger(BmcPasswordService.class);

	public BmcPasswordPlanResponse plan(BmcPasswordChangeRequest request) {
		String target = ACCOUNT_TARGET_BASE + request.accountId();

		Map<String, String> headers = new LinkedHashMap<>();
		// Basic Auth — base64("계정:비밀번호"). curl 은 -u 옵션, Java 는 HttpHeaders.setBasicAuth 가 자동 생성.
		headers.put("Authorization", "Basic {base64(BMC계정:비밀번호)}");
		headers.put("Content-Type", "application/json");
		// AMI MegaRAC 는 PATCH 에 사전조건 헤더를 요구할 수 있음 (Bios/SD 에서 428 실측). 거절 시 계정 리소스 ETag 사용.
		headers.put("If-Match", "*");

		Map<String, Object> body = Map.of("Password", request.newPassword());

		// 마스킹 — 비밀번호(********)에 더해 userName(자격증명 식별자)도 로그 미출력. 화면 echo 로만 확인.
		log.info("[bmc-password] {} {} account={} body={\"Password\":\"********\"} (password·userName 미출력)",
				METHOD, target, request.accountId());

		return new BmcPasswordPlanResponse(METHOD, target, headers, body, request.userName(),
				"BMC 계정 변경은 재부팅 없이 즉시 적용됩니다. 428 응답 시 If-Match 헤더(* 또는 계정 리소스 ETag)가 필수입니다. "
						+ "서버 로그에는 비밀번호가 마스킹되어 기록됩니다.");
	}
}
