package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.management.bmc.dto.response.BmcUploadIntentResponse;
import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
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
    private final BmcService bmcService;
    private final BmcUploadIntentService bmcUploadIntentService;

    // ============================================================
    // 단계 B (해시 충돌, ContentNudgePayload)
    // ============================================================

    public Long proceed(UUID nudgeId) {
        NudgeSession session = requireBmcSession(nudgeId);
        ContentNudgePayload payload = requireContentPayload(session);
        Long bmcId = bmcService.persistFromNudge(session.boardId(), payload);
        nudgeRegistry.remove(nudgeId);
        log.info("[bmc-nudge.proceed] nudgeId={}, bmcId={}", nudgeId, bmcId);
        return bmcId;
    }

    public Long replace(UUID nudgeId, Long targetId) {
        NudgeSession session = requireBmcSession(nudgeId);
        ContentNudgePayload payload = requireContentPayload(session);
        if (!session.conflictTargetIds().contains(targetId)) {
            throw new InvalidReplaceTargetException(targetId);
        }
        bmcService.purge(session.boardId(), targetId);
        Long bmcId = bmcService.persistFromNudge(session.boardId(), payload);
        nudgeRegistry.remove(nudgeId);
        log.info("[bmc-nudge.replace] nudgeId={}, replacedTarget={}, bmcId={}", nudgeId, targetId, bmcId);
        return bmcId;
    }

    public void cancel(UUID nudgeId) {
        NudgeSession session = requireBmcSession(nudgeId);
        ContentNudgePayload payload = requireContentPayload(session);
        bmcService.purgeNudgeTempTree(Path.of(payload.tempFilePath()));
        nudgeRegistry.remove(nudgeId);
        log.info("[bmc-nudge.cancel] nudgeId={}, tempPath={}", nudgeId, payload.tempFilePath());
    }

    // ============================================================
    // 단계 A (intent 메타 충돌, IntentMetaNudgePayload, WAVE 2)
    // ============================================================

    public BmcUploadIntentResponse proceedIntent(UUID nudgeId) {
        NudgeSession session = requireBmcSession(nudgeId);
        IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
        BmcUploadIntentResponse response = bmcUploadIntentService.issueAfterNudge(
                session.boardId(),
                bmcUploadIntentService.reconstructRequestFromAttributes(payload.attributes()));
        nudgeRegistry.remove(nudgeId);
        log.info("[bmc-nudge.intent.proceed] nudgeId={}, boardId={}, newToken={}",
                nudgeId, session.boardId(), response.uploadToken());
        return response;
    }

    public BmcUploadIntentResponse replaceIntent(UUID nudgeId, Long targetId) {
        NudgeSession session = requireBmcSession(nudgeId);
        IntentMetaNudgePayload payload = requireIntentMetaPayload(session);
        if (!session.conflictTargetIds().contains(targetId)) {
            throw new InvalidReplaceTargetException(targetId);
        }
        bmcService.purge(session.boardId(), targetId);
        BmcUploadIntentResponse response = bmcUploadIntentService.issueAfterNudge(
                session.boardId(),
                bmcUploadIntentService.reconstructRequestFromAttributes(payload.attributes()));
        nudgeRegistry.remove(nudgeId);
        log.info("[bmc-nudge.intent.replace] nudgeId={}, replacedTarget={}, newToken={}",
                nudgeId, targetId, response.uploadToken());
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
