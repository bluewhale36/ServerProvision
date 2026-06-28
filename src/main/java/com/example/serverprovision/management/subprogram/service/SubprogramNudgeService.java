package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.management.common.nudge.*;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramUploadIntentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * MK2 — Subprogram nudge 세션 confirm 핸들러. 두 phase 지원 (BIOS / BMC 와 동일 패턴).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubprogramNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final SubprogramRegistrationService subprogramRegistrationService;
	private final SubprogramLifecycleService subprogramLifecycleService;
	private final SubprogramUploadIntentService subprogramUploadIntentService;

	// ============================================================
	// 단계 B (해시 충돌, ContentNudgePayload)
	// ============================================================

	public Long proceed(UUID nudgeId) {
		NudgeSession session = requireSubprogramSession(nudgeId);
		ContentNudgePayload payload = requireContentPayload(session);
		Long id = subprogramRegistrationService.persistFromNudge(payload);
		nudgeRegistry.remove(nudgeId);
		log.info("[subprogram-nudge.proceed] nudgeId={}, id={}", nudgeId, id);
		return id;
	}

	public Long replace(UUID nudgeId, Long targetId) {
		NudgeSession session = requireSubprogramSession(nudgeId);
		ContentNudgePayload payload = requireContentPayload(session);
		if (!session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		subprogramLifecycleService.purge(targetId);
		Long id = subprogramRegistrationService.persistFromNudge(payload);
		nudgeRegistry.remove(nudgeId);
		log.info("[subprogram-nudge.replace] nudgeId={}, replacedTarget={}, id={}", nudgeId, targetId, id);
		return id;
	}

	public void cancel(UUID nudgeId) {
		NudgeSession session = requireSubprogramSession(nudgeId);
		ContentNudgePayload payload = requireContentPayload(session);
		subprogramRegistrationService.purgeNudgeTempTree(Path.of(payload.tempFilePath()));
		nudgeRegistry.remove(nudgeId);
		log.info("[subprogram-nudge.cancel] nudgeId={}, tempPath={}", nudgeId, payload.tempFilePath());
	}

	// ============================================================
	// 단계 A (intent 메타 충돌, IntentMetaNudgePayload, WAVE 2)
	// ============================================================

	public SubprogramUploadIntentResponse proceedIntent(UUID nudgeId) {
		NudgeSession session = requireSubprogramSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		var reissue = subprogramUploadIntentService.reconstructFromAttributes(payload.attributes());
		SubprogramUploadIntentResponse response = subprogramUploadIntentService.issueAfterNudge(
				reissue.kind(), reissue.scope(), reissue.request());
		nudgeRegistry.remove(nudgeId);
		// 마스킹 — upload token 평문 로깅 금지.
		log.info("[subprogram-nudge.intent.proceed] nudgeId={}", nudgeId);
		return response;
	}

	public SubprogramUploadIntentResponse replaceIntent(UUID nudgeId, Long targetId) {
		NudgeSession session = requireSubprogramSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		if (!session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		subprogramLifecycleService.purge(targetId);
		var reissue = subprogramUploadIntentService.reconstructFromAttributes(payload.attributes());
		SubprogramUploadIntentResponse response = subprogramUploadIntentService.issueAfterNudge(
				reissue.kind(), reissue.scope(), reissue.request());
		nudgeRegistry.remove(nudgeId);
		// 마스킹 — upload token 평문 로깅 금지.
		log.info("[subprogram-nudge.intent.replace] nudgeId={}, replacedTarget={}", nudgeId, targetId);
		return response;
	}

	public void cancelIntent(UUID nudgeId) {
		NudgeSession session = requireSubprogramSession(nudgeId);
		requireIntentMetaPayload(session);
		nudgeRegistry.remove(nudgeId);
		log.info("[subprogram-nudge.intent.cancel] nudgeId={}", nudgeId);
	}

	// ============================================================
	// 내부 헬퍼
	// ============================================================

	private NudgeSession requireSubprogramSession(UUID nudgeId) {
		NudgeSession session = nudgeRegistry.require(nudgeId);
		if (session.resourceType() != NudgeResourceType.SUBPROGRAM) {
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
