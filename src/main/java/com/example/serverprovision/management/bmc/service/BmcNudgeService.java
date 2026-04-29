package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MK2 — BMC 도메인 nudge confirm 핸들러.
 *
 * <p>업로드 단계 B (해시 검증 후) 에서 충돌이 발견되면 컨트롤러는 {@link NudgeRegistry#register} 로 세션을
 * 만들고 클라이언트에 {@code 409 + nudgeId} 를 반환한다. 사용자가 modal 에서 3택 중 하나를 선택해
 * confirm endpoint 를 호출하면 본 서비스의 {@link #proceed} / {@link #replace} / {@link #cancel} 이 실행된다.</p>
 *
 * <p>본 슬라이스에서는 confirm 흐름의 컨트롤러 진입점 + Registry 세션 검증 + 도메인 가드만 안착시킨다.
 * 실제 임시 파일 → 정식 경로 이전 / 영속화 본체는 후속 슬라이스에서 {@link BmcService} 와 결합한다 —
 * BIOS 도메인의 정합 패턴이 안착된 뒤 동일 구조로 따라간다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BmcNudgeService {

    private final NudgeRegistry nudgeRegistry;
    private final BmcService bmcService;
    private final com.example.serverprovision.management.bmc.repository.BmcRepository bmcRepository;

    /**
     * "그래도 등록" — 기존 자원 유지 + 임시 자원을 ACTIVE 로 confirm.
     */
    @Transactional
    public void proceed(UUID nudgeId) {
        NudgeSession session = requireBmcSession(nudgeId);
        log.info("[bmc-nudge] proceed nudgeId={}, boardId={}, conflicts={}",
                session.nudgeId(), session.boardId(), session.conflictTargetIds());
        // TODO MK2-bmc — pendingPayload 의 임시 파일을 정식 경로로 이전 + ACTIVE 영속화.
        // 본 슬라이스에서는 세션 단순 회수만 수행. 실제 영속화는 후속 슬라이스에서 BmcService 와 결합.
        if (!nudgeRegistry.remove(nudgeId)) {
            throw new NudgeAlreadyResolvedException(nudgeId);
        }
    }

    /**
     * "기존 영구 삭제 후 등록" — {@code targetId} 로 지정된 기존 자원을 영구 삭제 (별도 트랜잭션 권장 —
     * 본 슬라이스는 단일 트랜잭션) 한 뒤 임시 자원을 ACTIVE 로 confirm.
     */
    @Transactional
    public void replace(UUID nudgeId, Long replaceTargetId) {
        NudgeSession session = requireBmcSession(nudgeId);
        if (replaceTargetId == null || !session.conflictTargetIds().contains(replaceTargetId)) {
            throw new InvalidReplaceTargetException(replaceTargetId);
        }
        // 기존 자원 영구 삭제 — soft-deleted 한정 가드는 BmcService.purge 가 수행.
        var existing = bmcRepository.findById(replaceTargetId)
                .orElseThrow(() -> new BmcNotFoundException(session.boardId(), replaceTargetId));
        bmcService.purge(existing.getBoardModel().getId(), replaceTargetId);
        log.info("[bmc-nudge] replace nudgeId={}, removedId={}", session.nudgeId(), replaceTargetId);
        // TODO MK2-bmc — 임시 파일 → 정식 경로 이전 + ACTIVE 영속화.
        if (!nudgeRegistry.remove(nudgeId)) {
            throw new NudgeAlreadyResolvedException(nudgeId);
        }
    }

    /**
     * "취소" — 임시 파일 cleanup + 세션 회수. 정식 자원에는 영향 없음.
     */
    @Transactional
    public void cancel(UUID nudgeId) {
        NudgeSession session = requireBmcSession(nudgeId);
        log.info("[bmc-nudge] cancel nudgeId={}", session.nudgeId());
        // TODO MK2-bmc — pendingPayload 의 임시 파일 cleanup.
        if (!nudgeRegistry.remove(nudgeId)) {
            throw new NudgeAlreadyResolvedException(nudgeId);
        }
    }

    /**
     * 세션 조회 + 도메인 검증 — 다른 도메인이 발급한 nudgeId 로 본 서비스가 호출되지 않도록 차단.
     */
    private NudgeSession requireBmcSession(UUID nudgeId) {
        NudgeSession session = nudgeRegistry.require(nudgeId);
        if (session.resourceType() != NudgeResourceType.BMC) {
            // 다른 도메인 세션을 BMC confirm 으로 호출한 경우 — 명시 예외 (NotFound 와 동등 매핑).
            throw new com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException(nudgeId);
        }
        return session;
    }
}
