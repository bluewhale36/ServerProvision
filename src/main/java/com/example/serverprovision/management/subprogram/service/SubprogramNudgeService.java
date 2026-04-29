package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.management.common.nudge.NudgeAction;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * MK2 — Subprogram 도메인 nudge confirm 흐름.
 *
 * <p>{@link NudgeRegistry} 의 단일 진실원에서 세션을 잡고 사용자 결정 ({@link NudgeAction}) 에 따라
 * 도메인 자원 상태를 확정한다 :</p>
 * <ul>
 *   <li>{@link NudgeAction#PROCEED} — 기존 충돌 후보 보존, 새 자원 정식 등록</li>
 *   <li>{@link NudgeAction#REPLACE} — targetId 의 기존 자원을 영구 삭제 후 새 자원 등록</li>
 *   <li>{@link NudgeAction#CANCEL}  — 임시 업로드 cleanup</li>
 * </ul>
 *
 * <p>본 서비스는 {@link NudgeResourceType#SUBPROGRAM} 세션만 수용한다. 세션 도메인이 다른 경우
 * {@link NudgeAlreadyResolvedException} 은 아니지만 별도 검증으로 거절 — 추후 nudge 도메인 통합 검증
 * 인프라가 들어오면 본 메서드는 단순 forwarder 로 축소된다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubprogramNudgeService {

    private final NudgeRegistry nudgeRegistry;
    private final SubprogramService subprogramService;

    public void proceed(UUID nudgeId) {
        NudgeSession session = requireSubprogramSession(nudgeId);
        // PROCEED — 기존 자원 보존 + 새 자원 정식 등록은 confirm 시점의 pendingPayload 로 처리.
        // 본 슬라이스의 SubprogramService 등록 경로가 nudge pendingPayload 를 받아 정식화하는 메서드는
        // 다른 도메인 (BIOS/BMC) 의 pendingPayload 형식이 안착된 뒤 통합 도입한다. 본 메서드는 세션을
        // 소비하고 호출자가 후속 정식 등록을 별도 수행하도록 한다.
        consume(session);
        log.info("[nudge/subprogram] PROCEED 처리. nudgeId={}", nudgeId);
    }

    public void replace(UUID nudgeId, Long targetId) {
        NudgeSession session = requireSubprogramSession(nudgeId);
        if (targetId == null || !session.conflictTargetIds().contains(targetId)) {
            throw new InvalidReplaceTargetException(targetId);
        }
        // REPLACE — 별도 트랜잭션으로 기존 자원 영구 삭제. 이후 정식 등록은 PROCEED 와 동일 후속 경로.
        subprogramService.purge(targetId);
        consume(session);
        log.info("[nudge/subprogram] REPLACE 처리. nudgeId={}, targetId={}", nudgeId, targetId);
    }

    public void cancel(UUID nudgeId) {
        NudgeSession session = requireSubprogramSession(nudgeId);
        // CANCEL — 임시 업로드 cleanup 책임은 pendingPayload.tempFilePath() 가 있는 경우 호출자가 수행.
        // 본 슬라이스에서 Subprogram 은 디스크 트리를 정식 경로에 직접 풀므로 별도 임시 cleanup 대상이
        // 없다 (실패 시는 SubprogramService.addSubprogram 의 catch 가 cleanup 수행). 세션만 제거.
        consume(session);
        log.info("[nudge/subprogram] CANCEL 처리. nudgeId={}", nudgeId);
    }

    private NudgeSession requireSubprogramSession(UUID nudgeId) {
        NudgeSession session = nudgeRegistry.require(nudgeId);
        if (session.resourceType() != NudgeResourceType.SUBPROGRAM) {
            throw new NudgeAlreadyResolvedException(nudgeId);
        }
        return session;
    }

    private void consume(NudgeSession session) {
        if (!nudgeRegistry.remove(session.nudgeId())) {
            throw new NudgeAlreadyResolvedException(session.nudgeId());
        }
    }
}
