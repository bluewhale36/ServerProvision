package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.common.nudge.*;
import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * MK2 — BMC nudge 세션 confirm 핸들러. 두 phase 지원 (BIOS 와 동일 패턴).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BmcNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final NudgeUploadDiscarder nudgeUploadDiscarder;
	private final BmcRegistrationService bmcRegistrationService;
	private final NudgeReplacer nudgeReplacer;
	private final BmcUploadIntentService bmcUploadIntentService;

	// ============================================================
	// 단계 B (해시 충돌, ContentNudgePayload)
	// ============================================================

	public Long proceed(UUID nudgeId) {
		return nudgeRegistry.whileInUse(nudgeId, () -> {
			NudgeSession session = requireBmcSession(nudgeId);
			ContentNudgePayload payload = requireContentPayload(session);
			Long bmcId = bmcRegistrationService.persistFromNudge(session.boardId(), payload);
			nudgeRegistry.remove(nudgeId);
			log.info("[bmc-nudge.proceed] nudgeId={}, bmcId={}", nudgeId, bmcId);
			return bmcId;
		});
	}

	public Long replace(UUID nudgeId, Long targetId, String typedName) {
		return nudgeRegistry.whileInUse(nudgeId, () -> {
			NudgeSession session = requireBmcSession(nudgeId);
			ContentNudgePayload payload = requireContentPayload(session);
			Long bmcId = nudgeReplacer.replace(session, ResourceType.BMC_FIRMWARE, targetId, typedName,
					() -> bmcRegistrationService.persistFromNudge(session.boardId(), payload));
			nudgeRegistry.remove(nudgeId);
			log.info("[bmc-nudge.replace] nudgeId={}, replacedTarget={}, bmcId={}", nudgeId, targetId, bmcId);
			return bmcId;
		});
	}

	public void cancel(UUID nudgeId) {
		nudgeRegistry.whileInUse(nudgeId, () -> {
			NudgeSession session = requireBmcSession(nudgeId);
			ContentNudgePayload payload = requireContentPayload(session);
			// 업로드 트리만 정리한다. 기존 폴더 등록(claim)이면 payload 가 지울 트리를 내지 않는다.
			nudgeUploadDiscarder.discard(session, "nudge.cancel");
			nudgeRegistry.remove(nudgeId);
			log.info("[bmc-nudge.cancel] nudgeId={}, tempPath={}", nudgeId, payload.tempFilePath());
		});
	}

	// ============================================================
	// 단계 A (intent 메타 충돌, IntentMetaNudgePayload, WAVE 2)
	// ============================================================

	public BmcUploadIntentResponse proceedIntent(UUID nudgeId) {
		NudgeSession session = requireBmcSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		BmcUploadIntentResponse response = bmcUploadIntentService.issueAfterNudge(
				session.boardId(),
				bmcUploadIntentService.reconstructRequestFromAttributes(payload.attributes())
		);
		nudgeRegistry.remove(nudgeId);
		// 마스킹 — upload token 평문 로깅 금지.
		log.info("[bmc-nudge.intent.proceed] nudgeId={}, boardId={}", nudgeId, session.boardId());
		return response;
	}

	public BmcUploadIntentResponse replaceIntent(UUID nudgeId, Long targetId, String typedName) {
		NudgeSession session = requireBmcSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		BmcUploadIntentResponse response = nudgeReplacer.replace(session, ResourceType.BMC_FIRMWARE, targetId, typedName,
				() -> bmcUploadIntentService.issueAfterNudge(
						session.boardId(),
						bmcUploadIntentService.reconstructRequestFromAttributes(payload.attributes())
				));
		nudgeRegistry.remove(nudgeId);
		// 마스킹 — upload token 평문 로깅 금지.
		log.info("[bmc-nudge.intent.replace] nudgeId={}, replacedTarget={}", nudgeId, targetId);
		return response;
	}

	public void cancelIntent(UUID nudgeId) {
		NudgeSession session = requireBmcSession(nudgeId);
		requireIntentMetaPayload(session);
		nudgeRegistry.remove(nudgeId);
		log.info("[bmc-nudge.intent.cancel] nudgeId={}", nudgeId);
	}

	// ============================================================
	// 내부 헬퍼
	// ============================================================

	private NudgeSession requireBmcSession(UUID nudgeId) {
		NudgeSession session = nudgeRegistry.require(nudgeId);
		if (session.resourceType() != NudgeResourceType.BMC) {
			throw new NudgeNotFoundException(nudgeId);
		}
		return session;
	}

	private ContentNudgePayload requireContentPayload(NudgeSession session) {
		return castPayload(session, ContentNudgePayload.class);
	}

	private IntentMetaNudgePayload requireIntentMetaPayload(NudgeSession session) {
		return castPayload(session, IntentMetaNudgePayload.class);
	}

	private <T extends NudgePayload> T castPayload(NudgeSession session, Class<T> expected) {
		NudgePayload payload = session.payload();
		if (!expected.isInstance(payload)) {
			throw new NudgeNotFoundException(session.nudgeId());
		}
		return expected.cast(payload);
	}
}
