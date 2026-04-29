package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;

/**
 * MK2 — BIOS nudge 세션 confirm 핸들러. 사용자가 modal 에서 결정한 3택 (proceed / replace / cancel) 을
 * 실제 도메인 동작으로 환산한다.
 *
 * <p>책임 분할 :
 * <ul>
 *   <li>{@link NudgeRegistry} — 세션 메모리 진실원 (require / remove)</li>
 *   <li>{@link BiosService} — 트랜잭션 boundary 가 필요한 도메인 동작 (persist / purge)</li>
 *   <li>본 서비스 — 두 컴포넌트를 조합하고 세션 검증을 수행 (resourceType BIOS 인지, replace targetId 가
 *       세션 conflicts 후보에 속하는지)</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiosNudgeService {

    private final NudgeRegistry nudgeRegistry;
    private final BiosService biosService;

    /**
     * 사용자 "그래도 등록" — 기존 자원은 그대로 두고 임시 트리를 ACTIVE 자원으로 영속화.
     */
    public Long proceed(UUID nudgeId) {
        NudgeSession session = requireBiosSession(nudgeId);
        Long biosId = biosService.persistFromNudge(session.boardId(), session.pendingPayload());
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
        if (!session.conflictTargetIds().contains(targetId)) {
            throw new InvalidReplaceTargetException(targetId);
        }
        // BiosService.purge 가 SoftDeleted 만 허용. Deprecated 후보는 본 흐름에서 거절될 수 있으나
        // conflicts 후보 자체가 SoftDeleted/Deprecated 혼합이라 운영 정책에 따라 BiosService.purge 의
        // 가드를 완화하거나 confirm 단계에서 사전 transition 을 요구할 수 있다. 본 슬라이스는 SoftDeleted
        // 후보만 replace 가능하다는 단순 규칙을 유지 — Deprecated 후보가 섞여 있으면 IllegalBiosStateException
        // 이 자연스럽게 advice → 409 로 회신된다.
        biosService.purge(session.boardId(), targetId);
        Long biosId = biosService.persistFromNudge(session.boardId(), session.pendingPayload());
        nudgeRegistry.remove(nudgeId);
        log.info("[nudge.replace] nudgeId={}, replacedTarget={}, biosId={}", nudgeId, targetId, biosId);
        return biosId;
    }

    /**
     * 사용자 "취소" — 임시 트리 cleanup 후 세션 제거.
     */
    public void cancel(UUID nudgeId) {
        NudgeSession session = requireBiosSession(nudgeId);
        biosService.purgeNudgeTempTree(Path.of(session.pendingPayload().tempFilePath()));
        nudgeRegistry.remove(nudgeId);
        log.info("[nudge.cancel] nudgeId={}, tempPath={}", nudgeId, session.pendingPayload().tempFilePath());
    }

    /**
     * 본 서비스 전용 세션 가드 — resourceType 이 BIOS 가 아닌 nudgeId 로의 호출을 NotFound 로 거절.
     * 다른 도메인 (BMC / OS / Subprogram) 의 nudgeId 가 BIOS endpoint 로 잘못 라우팅되는 것을 방어.
     */
    private NudgeSession requireBiosSession(UUID nudgeId) {
        NudgeSession session = nudgeRegistry.require(nudgeId);
        if (session.resourceType() != NudgeResourceType.BIOS) {
            // 다른 도메인 세션을 잘못 호출 — NudgeNotFoundException 으로 통일.
            throw new com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException(nudgeId);
        }
        return session;
    }
}
