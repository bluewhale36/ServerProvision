package com.example.serverprovision.provisioning.service;

import com.example.serverprovision.provisioning.config.BiosResourceProperties;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.dto.request.BiosSettingsSaveRequest;
import com.example.serverprovision.provisioning.dto.response.BiosSettingsSaveResponse;
import com.example.serverprovision.provisioning.dto.response.BiosSetupPageResponse;
import com.example.serverprovision.provisioning.dto.response.BoardCardResponse;
import com.example.serverprovision.provisioning.exception.InvalidBiosValueException;
import com.example.serverprovision.provisioning.exception.UnknownBiosAttributeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BIOS 셋업 슬라이스 오케스트레이션. 영속화가 없어 {@code @Transactional} 이 없다 (무상태 render + validate PoC).
 * <ul>
 *   <li>GET : 로더 → 화면 뷰모델({@link BiosSetupPageResponse}).</li>
 *   <li>POST : 변경쌍을 레지스트리/{@link BiosAttributeType} 로 검증·coerce → Redfish 페이로드 로깅 → echo 응답.</li>
 * </ul>
 * try/catch 없음 — 도메인 예외는 전역 advice 가 매핑한다.
 */
@Service
@RequiredArgsConstructor
public class BiosSetupService {

	private final BiosSetupLoader loader;
	private final BiosRedfishPayloadAssembler assembler;
	private final BiosResourceProperties properties;

	/** 선택 가능한 보드 목록 (설정 순서 = 표시 순서). 뷰 DTO 매핑은 서비스에서 수행한다. */
	public List<BoardCardResponse> listBoards() {
		if (properties.boards() == null) {
			return List.of();
		}
		return properties.boards().stream()
				.map(board -> new BoardCardResponse(board.key()))
				.toList();
	}

	public BiosSetupPageResponse renderMenu(String boardKey) {
		return BiosSetupPageResponse.of(loader.load(boardKey));
	}

	public BiosSettingsSaveResponse save(String boardKey, BiosSettingsSaveRequest request) {
		BiosSetupMenu menu = loader.load(boardKey);
		Map<BiosAttributeName, BiosAttributeValue> coerced = new LinkedHashMap<>();      // SD/Attributes 행
		List<BiosSettingsSaveResponse.PasswordChangePlan> passwordChanges = new ArrayList<>();
		List<String> passwordNames = new ArrayList<>();
		boolean resetRequired = false;

		for (Map.Entry<String, String> entry : request.attributes().entrySet()) {
			String key = entry.getKey();
			if (key == null || key.isBlank()) {
				throw InvalidBiosValueException.blankKey(); // 빈 키 → 400 (IllegalArgumentException → 500 누수 방지)
			}
			BiosAttributeName name = BiosAttributeName.of(key);
			String raw = entry.getValue();
			BiosAttribute attr = menu.attribute(name)
					.orElseThrow(() -> new UnknownBiosAttributeException(name));

			if (attr.readOnly()) {
				continue; // 안전망 — UI 가 차단하지만 직접 POST 대비.
			}

			// 비밀번호는 Attributes(SD) 가 아니라 Bios.ChangePassword 액션으로 분리 (GIGABYTE 매뉴얼).
			if (attr.type() == BiosAttributeType.PASSWORD) {
				if (raw == null || raw.isEmpty()) {
					continue; // 빈 비밀번호 = 미변경.
				}
				attr.type().validate(attr, raw); // 길이 검증
				String passwordName = redfishPasswordName(name);
				passwordChanges.add(new BiosSettingsSaveResponse.PasswordChangePlan(
						BiosRedfishPayloadAssembler.CHANGE_PASSWORD_TARGET, passwordName));
				passwordNames.add(passwordName);
				if (attr.resetRequired()) {
					resetRequired = true;
				}
				continue;
			}

			attr.type().validate(attr, raw);                 // 실패 시 InvalidBiosValueException(400)
			coerced.put(name, attr.type().coerce(attr, raw)); // 타입화 (String/Long)
			if (attr.resetRequired()) {
				resetRequired = true;
			}
		}

		Map<String, Object> attributes = assembler.assembleAttributes(coerced);
		assembler.logApplyPlan(boardKey, attributes, passwordNames, resetRequired);
		return new BiosSettingsSaveResponse(
				BiosRedfishPayloadAssembler.BIOS_SETTINGS_TARGET,
				BiosRedfishPayloadAssembler.BIOS_SETTINGS_METHOD,
				attributes,
				passwordChanges,
				resetRequired,
				BiosRedfishPayloadAssembler.RESET_TARGET,
				attributes.size() + passwordChanges.size());
	}

	// GIGABYTE Bios.ChangePassword 의 PasswordName: 레지스트리명 "SETUP001_AdministratorPassword" → "SETUP001".
	private static String redfishPasswordName(BiosAttributeName name) {
		String value = name.value();
		int underscore = value.indexOf('_');
		return underscore > 0 ? value.substring(0, underscore) : value;
	}
}
