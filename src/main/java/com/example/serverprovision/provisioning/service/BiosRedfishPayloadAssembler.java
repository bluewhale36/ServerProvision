package com.example.serverprovision.provisioning.service;

import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 변경분을 GIGABYTE(AMI MegaRAC) Redfish 가 실제로 받는 형태로 조립·로깅한다.
 * <p>GIGABYTE 매뉴얼(server_manual_redfish §19-2-1-1 BIOS SD, §18-2 System) 기준:</p>
 * <ul>
 *   <li>일반 속성: {@code PATCH /redfish/v1/Systems/Self/Bios/SD} 에 순수 {@code {"Attributes":{...}}} 만 전송
 *       (DMTF 의 {@code @Redfish.SettingsApplyTime} 래퍼는 GIGABYTE 예시에 없어 제외).</li>
 *   <li>비밀번호: {@code Attributes} 가 아니라 {@code POST .../Bios/Actions/Bios.ChangePassword} 액션
 *       ({@code PasswordName} 은 "SETUP001" 형식의 GIGABYTE 이름).</li>
 *   <li>적용: {@code POST .../Actions/ComputerSystem.Reset {"ResetType":"ForceRestart"}} 재부팅 후 반영.</li>
 * </ul>
 * <p>현 PoC 는 실제 호출 없이 INFO 로그로만 출력한다 (Integer 는 따옴표 없는 숫자, Enum 은 문자열로 직렬화 — 타입 보존).</p>
 */
@Component
public class BiosRedfishPayloadAssembler {

	/** BIOS pending(SD) 설정 리소스 — 일반 속성 변경 대상. (system instance = "Self", GIGABYTE 관례) */
	public static final String BIOS_SETTINGS_TARGET = "/redfish/v1/Systems/Self/Bios/SD";
	public static final String BIOS_SETTINGS_METHOD = "PATCH";
	/** 비밀번호 변경 액션 — Attributes 로는 설정 불가. */
	public static final String CHANGE_PASSWORD_TARGET = "/redfish/v1/Systems/Self/Bios/Actions/Bios.ChangePassword";
	/** 적용(반영)용 재부팅 액션. */
	public static final String RESET_TARGET = "/redfish/v1/Systems/Self/Actions/ComputerSystem.Reset";
	public static final String RESET_TYPE = "ForceRestart";

	private static final Logger log = LoggerFactory.getLogger(BiosRedfishPayloadAssembler.class);
	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	public Map<String, Object> assembleAttributes(Map<BiosAttributeName, BiosAttributeValue> coerced) {
		Map<String, Object> attributes = new LinkedHashMap<>();
		coerced.forEach((name, value) -> attributes.put(name.value(), value.jsonValue()));
		return attributes;
	}

	/**
	 * GIGABYTE-ready 적용 계획을 로그로 출력한다 (대상 URI + method + 분리된 password).
	 *
	 * @param attributes    SD 에 PATCH 할 일반 속성 (비밀번호 제외, 타입 보존)
	 * @param passwordNames ChangePassword 로 보낼 PasswordName 목록 (값은 보안상 미출력)
	 */
	public void logApplyPlan(String boardKey, Map<String, Object> attributes,
	                         List<String> passwordNames, boolean resetRequired) {
		if (attributes.isEmpty() && passwordNames.isEmpty()) {
			log.info("[bios-save] board={} 적용 대상 변경 없음", boardKey);
			return;
		}
		if (!attributes.isEmpty()) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("Attributes", attributes);
			log.info("[bios-save] board={} {} {} body={}",
					boardKey, BIOS_SETTINGS_METHOD, BIOS_SETTINGS_TARGET, MAPPER.writeValueAsString(body));
		}
		for (String passwordName : passwordNames) {
			log.info("[bios-save] board={} POST {} PasswordName={} (NewPassword 값은 미출력 — Old/New 는 호출 시 입력)",
					boardKey, CHANGE_PASSWORD_TARGET, passwordName);
		}
		if (resetRequired) {
			// SLF4J 는 '{}' 쌍만 placeholder 로 해석 — '{"' / '"}' 는 리터럴.
			log.info("[bios-save] board={} 적용하려면 재부팅: POST {} body={\"ResetType\":\"{}\"}",
					boardKey, RESET_TARGET, RESET_TYPE);
		}
	}
}
