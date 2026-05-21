package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * MK2 WAVE 3 — Intent 핸드셰이크 응답.
 *
 * <p>sealed sum type 으로 두 phase 분기 :
 * <ul>
 *   <li>{@link IntentTokenIssued} : 정상 — 클라이언트가 즉시 업로드 시작 가능</li>
 *   <li>{@link HashCheckRequired} : Phase 1 결과 (osImageId, size) 매칭 후보가 있어 client SHA-256
 *       precompute 필요. 클라이언트가 fingerprint 계산 후 같은 endpoint 를 hash 동봉해서 재호출.</li>
 * </ul>
 *
 * <p>Jackson 3 직렬화 — {@code type} 디스크리미네이터 필드로 클라이언트가 분기 가능.
 * NUDGE_REQUIRED 케이스는 별도 예외 ({@code IsoNudgeRequiredException}) 로 409 응답이 되므로 본
 * sealed 의 permits 에 포함되지 않는다.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
		{
				@JsonSubTypes.Type(value = IsoUploadIntentResponse.IntentTokenIssued.class, name = "INTENT_TOKEN_ISSUED"),
				@JsonSubTypes.Type(value = IsoUploadIntentResponse.HashCheckRequired.class, name = "HASH_CHECK_REQUIRED")
		}
)
public sealed interface IsoUploadIntentResponse
		permits IsoUploadIntentResponse.IntentTokenIssued,
		IsoUploadIntentResponse.HashCheckRequired {

	/**
	 * Phase 1 정상 (후보 0건) 또는 Phase 2 hash 비매칭 — 정상 token 발급. 클라이언트는 즉시 업로드 시작.
	 */
	record IntentTokenIssued(
			String uploadToken,
			List<String> warnings
	) implements IsoUploadIntentResponse {

	}


	/**
	 * Phase 1 결과 (osImageId, size) 매칭 후보 발견 — 클라이언트가 정식 SHA-256 계산 후 같은 endpoint 를
	 * hash 동봉해서 재호출해야 한다.
	 *
	 * @param candidates           후보 자원 목록 (modal 표시용 — id/state/name/version 노출)
	 * @param fingerprintAlgorithm 해시 알고리즘 식별자 (현재 "SHA-256" 고정, 향후 확장 여지)
	 */
	record HashCheckRequired(
			List<NudgeConflictEntry> candidates,
			String fingerprintAlgorithm
	) implements IsoUploadIntentResponse {

	}
}
