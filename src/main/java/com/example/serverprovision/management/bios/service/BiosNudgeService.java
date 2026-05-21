package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.response.BiosUploadIntentResponse;
import com.example.serverprovision.management.common.nudge.*;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * MK2 — BIOS nudge 세션 confirm 핸들러. 사용자가 modal 에서 결정한 3택 (proceed / replace / cancel) 을
 * 실제 도메인 동작으로 환산한다.
 *
 * <p>두 phase 지원:
 * <ul>
 *   <li>단계 B (해시 충돌, ContentNudgePayload) — {@link #proceed} / {@link #replace} / {@link #cancel}
 *       이 임시 트리를 ACTIVE 자원으로 영속화 (또는 cleanup).</li>
 *   <li>단계 A (intent 메타 충돌, IntentMetaNudgePayload, WAVE 2) — {@link #proceedIntent} /
 *       {@link #replaceIntent} / {@link #cancelIntent} 가 nudge 세션을 회수하고 새 upload-intent token 을
 *       발급해 클라이언트가 정상 업로드 흐름을 시작하도록 한다.</li>
 * </ul>
 *
 * <p>각 endpoint 는 phase 별 분리된 메서드를 호출하므로 sealed payload 의 잘못된 phase 호출은 런타임
 * 검증으로 차단 (정상적인 클라이언트는 응답에 분기되어 endpoint 가 어긋나지 않음).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiosNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final BiosService biosService;
	private final BiosUploadIntentService biosUploadIntentService;

	// ============================================================
	// 단계 B (해시 충돌, ContentNudgePayload)
	// ============================================================

	/**
	 * 사용자 "그래도 등록" — 기존 자원은 그대로 두고 임시 트리를 ACTIVE 자원으로 영속화.
	 */
	public Long proceed(UUID nudgeId) {
		NudgeSession session = requireBiosSession(nudgeId);
		ContentNudgePayload payload = requireContentPayload(session);
		Long biosId = biosService.persistFromNudge(session.boardId(), payload);
		nudgeRegistry.remove(nudgeId);
		log.info("[nudge.proceed] nudgeId={}, biosId={}", nudgeId, biosId);
		return biosId;
	}

	/**
	 * 사용자 "기존 영구 삭제 후 등록" — targetId 자원을 purge (별도 트랜잭션 내부) 한 후 임시 트리를
	 * ACTIVE 자원으로 영속화. targetId 가 세션의 conflicts 후보에 없으면 거절 (S4 fieldErrors 매핑).
	 */
	public Long replace(UUID nudgeId, Long targetId) {
		NudgeSession session = requireBiosSession(nudgeId);
		ContentNudgePayload payload = requireContentPayload(session);
		if (!session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		biosService.purge(session.boardId(), targetId);
		Long biosId = biosService.persistFromNudge(session.boardId(), payload);
		nudgeRegistry.remove(nudgeId);
		log.info("[nudge.replace] nudgeId={}, replacedTarget={}, biosId={}", nudgeId, targetId, biosId);
		return biosId;
	}

	/**
	 * 사용자 "취소" — 임시 트리 cleanup 후 세션 제거.
	 */
	public void cancel(UUID nudgeId) {
		NudgeSession session = requireBiosSession(nudgeId);
		ContentNudgePayload payload = requireContentPayload(session);
		biosService.purgeNudgeTempTree(Path.of(payload.tempFilePath()));
		nudgeRegistry.remove(nudgeId);
		log.info("[nudge.cancel] nudgeId={}, tempPath={}", nudgeId, payload.tempFilePath());
	}

	// ============================================================
	// 단계 A (intent 메타 충돌, IntentMetaNudgePayload, WAVE 2)
	// ============================================================

	/**
	 * MK2 WAVE 2 — intent nudge "그래도 등록". 기존 SoftDeleted/Deprecated 자원은 그대로 두고 메타 검사를
	 * 건너뛴 상태로 새 upload-intent token 을 발급. 클라이언트는 token 으로 정상 업로드 시작.
	 */
	public BiosUploadIntentResponse proceedIntent(UUID nudgeId) {
		NudgeSession session = requireBiosSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		BiosUploadIntentResponse response = biosUploadIntentService.issueAfterNudge(
				session.boardId(),
				biosUploadIntentService.reconstructRequestFromAttributes(payload.attributes())
		);
		nudgeRegistry.remove(nudgeId);
		log.info(
				"[nudge.intent.proceed] nudgeId={}, boardId={}, newToken={}",
				nudgeId, session.boardId(), response.uploadToken()
		);
		return response;
	}

	/**
	 * MK2 WAVE 2 — intent nudge "기존 영구 삭제 후 등록". targetId 자원 purge 후 새 upload-intent token 발급.
	 */
	public BiosUploadIntentResponse replaceIntent(UUID nudgeId, Long targetId) {
		NudgeSession session = requireBiosSession(nudgeId);
		IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
		if (!session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		biosService.purge(session.boardId(), targetId);
		BiosUploadIntentResponse response = biosUploadIntentService.issueAfterNudge(
				session.boardId(),
				biosUploadIntentService.reconstructRequestFromAttributes(payload.attributes())
		);
		nudgeRegistry.remove(nudgeId);
		log.info(
				"[nudge.intent.replace] nudgeId={}, replacedTarget={}, newToken={}",
				nudgeId, targetId, response.uploadToken()
		);
		return response;
	}

	/**
	 * MK2 WAVE 2 — intent nudge "취소". 임시 파일이 없으므로 세션만 회수.
	 */
	public void cancelIntent(UUID nudgeId) {
		NudgeSession session = requireBiosSession(nudgeId);
		requireIntentMetaPayload(session); // phase 검증
		nudgeRegistry.remove(nudgeId);
		log.info("[nudge.intent.cancel] nudgeId={}", nudgeId);
	}

	// ============================================================
	// 내부 헬퍼
	// ============================================================

	/**
	 * 본 서비스 전용 세션 가드 — resourceType 이 BIOS 가 아닌 nudgeId 로의 호출을 NotFound 로 거절.
	 * 다른 도메인 (BMC / OS / Subprogram) 의 nudgeId 가 BIOS endpoint 로 잘못 라우팅되는 것을 방어.
	 */
	private NudgeSession requireBiosSession(UUID nudgeId) {
		NudgeSession session = nudgeRegistry.require(nudgeId);
		if (session.resourceType() != NudgeResourceType.BIOS) {
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

	/**
	 * sealed payload 의 phase 검증. endpoint 가 잘못된 phase 의 nudgeId 로 호출됐을 때 NudgeNotFound 로
	 * 통일 거절. 정상 클라이언트는 응답에 따라 phase 별 endpoint 로 자동 분기되므로 본 검증은 방어용.
	 */
	private <T extends NudgePayload> T castPayload(NudgeSession session, Class<T> expected) {
		NudgePayload payload = session.payload();
		if (!expected.isInstance(payload)) {
			throw new NudgeNotFoundException(session.nudgeId());
		}
		return expected.cast(payload);
	}
}
