package com.example.serverprovision.management.os.service;

import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeReplacer;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import com.example.serverprovision.management.os.dto.response.IsoUploadIntentResponse;
import com.example.serverprovision.management.os.service.iso.IsoUploadIntentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * ISO 업로드 전 중복 확인 창(같은 OS 의 같은 경로 · 같은 내용 휴지통 후보)의 3택 핸들러.
 *
 * <p>HF30-2 — 배경 등록 단계의 휴지통 창(업로드 뒤 내용 창)과 그 proceed · replace · cancel 은 없앴다. 그 창은 화면에 연결되지 않아
 * [그래도 등록] 이 항상 실패했고(docs C-17), 업로드 전 판정이 같은 내용의 휴지통 후보를 함께 묻게 되어 더는 필요 없다.
 * [기존 영구 삭제 후 등록] 은 {@link NudgeReplacer}(휴지통 조회 → 자원명 확인 → 실행기)로 처리해 휴지통 실물 정리와 감사 기록을 남긴다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OSNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final NudgeReplacer nudgeReplacer;
	private final IsoUploadIntentService isoUploadIntentService;

	/** [그래도 등록] — 휴지통 후보는 그대로 두고 업로드 토큰을 다시 발급한다. */
	public IsoUploadIntentResponse proceedIntent(UUID nudgeId) {
		NudgeSession session = requireOsIsoSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		var reissue = isoUploadIntentService.reconstructFromAttributes(payload.attributes());
		IsoUploadIntentResponse response = isoUploadIntentService.issueAfterNudge(reissue.osMetadataId(), reissue.request());
		consumeSession(nudgeId);
		log.info("[osNudge.intent.proceed] nudgeId={}, response={}", nudgeId, summarize(response));
		return response;
	}

	/** [기존 영구 삭제 후 등록] — 고른 휴지통 후보를 영구 삭제한 뒤 업로드 토큰을 다시 발급한다. */
	public IsoUploadIntentResponse replaceIntent(UUID nudgeId, Long targetId, String typedName) {
		NudgeSession session = requireOsIsoSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		var reissue = isoUploadIntentService.reconstructFromAttributes(payload.attributes());
		IsoUploadIntentResponse response = nudgeReplacer.replace(session, ResourceType.OS_ISO, targetId, typedName,
				() -> isoUploadIntentService.issueAfterNudge(reissue.osMetadataId(), reissue.request()));
		consumeSession(nudgeId);
		log.info("[osNudge.intent.replace] nudgeId={}, replacedTarget={}, response={}",
				nudgeId, targetId, summarize(response));
		return response;
	}

	public void cancelIntent(UUID nudgeId) {
		NudgeSession session = requireOsIsoSession(nudgeId);
		requireIntentMetaPayload(session);
		consumeSession(nudgeId);
		log.info("[osNudge.intent.cancel] nudgeId={}", nudgeId);
	}

	private static String summarize(IsoUploadIntentResponse response) {
		if (response instanceof IsoUploadIntentResponse.IntentTokenIssued) {
			// 마스킹 — upload token 평문 로깅 금지 (phase 만 요약).
			return "TOKEN_ISSUED";
		}
		if (response instanceof IsoUploadIntentResponse.HashCheckRequired chk) {
			return "HASH_CHECK_REQUIRED(" + chk.candidates().size() + ")";
		}
		return "<unknown>";
	}

	private IntentMetaNudgePayload requireIntentMetaPayload(NudgeSession session) {
		if (!(session.payload() instanceof IntentMetaNudgePayload p)) {
			throw new NudgeNotFoundException(session.nudgeId());
		}
		return p;
	}

	private NudgeSession requireOsIsoSession(UUID nudgeId) {
		NudgeSession session = nudgeRegistry.require(nudgeId);
		if (session.resourceType() != NudgeResourceType.OS_ISO) {
			// 도메인 어긋남 — OS 컨트롤러가 다른 도메인의 nudgeId 를 받은 케이스. 노출하지 않음.
			throw new NudgeAlreadyResolvedException(nudgeId);
		}
		return session;
	}

	private void consumeSession(UUID nudgeId) {
		if (!nudgeRegistry.remove(nudgeId)) {
			// 동시성 race — 두 번째 confirm. require() 로 통과했어도 remove() 결과가 false 이면 이미 처리됨.
			throw new NudgeAlreadyResolvedException(nudgeId);
		}
	}
}
