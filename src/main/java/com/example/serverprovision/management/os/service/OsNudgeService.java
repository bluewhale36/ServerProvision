package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * MK2 — OS_ISO 도메인의 nudge confirm 처리.
 *
 * <p>업로드 단계 B (해시 검증 후) 에서 충돌이 발견되어 {@link NudgeRegistry} 에 세션이 등록된 경우,
 * 사용자 modal 의 3택 (proceed / replace / cancel) 입력을 본 서비스가 처리한다.</p>
 *
 * <ul>
 *   <li>{@link #proceed} — 임시 파일이 가리키는 신규 자원을 ACTIVE 로 영속화 (기존 충돌 후보 보존).</li>
 *   <li>{@link #replace} — 사용자가 지목한 충돌 후보를 명시적 purge 후 신규 자원을 ACTIVE 로 영속화.</li>
 *   <li>{@link #cancel}  — 임시 파일 cleanup 만 수행하고 nudge 세션을 폐기.</li>
 * </ul>
 *
 * <p>각 종결 액션 후 {@link NudgeRegistry#remove} 로 세션을 회수해 동일 nudgeId 의 두 번째 confirm
 * 호출을 {@link NudgeAlreadyResolvedException} 으로 거절한다 (멱등 X — 명시적 충돌).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsNudgeService {

    private final NudgeRegistry nudgeRegistry;
    private final OSImageService osImageService;
    private final ISORepository isoRepository;

    /**
     * PROCEED — 기존 충돌 후보를 그대로 두고 신규 자원만 ACTIVE 로 등록한다.
     */
    @Transactional
    public Long proceed(UUID nudgeId) {
        NudgeSession session = requireOsIsoSession(nudgeId);
        Long isoId = osImageService.completePendingIsoFromNudge(session);
        consumeSession(nudgeId);
        log.info("[osNudge] proceed 완료. nudgeId={}, newIsoId={}", nudgeId, isoId);
        return isoId;
    }

    /**
     * REPLACE — 지목된 충돌 후보를 명시적 purge 후 신규 자원을 ACTIVE 로 등록한다.
     */
    @Transactional
    public Long replace(UUID nudgeId, Long targetId) {
        NudgeSession session = requireOsIsoSession(nudgeId);
        if (targetId == null || !session.conflictTargetIds().contains(targetId)) {
            throw new InvalidReplaceTargetException(targetId);
        }
        // 1) 충돌 후보 명시적 purge — 별도 트랜잭션 경계가 필요한 경우 OSImageService 의 purge 메서드를 호출.
        ISO target = isoRepository.findById(targetId)
                .orElseThrow(() -> new ISONotFoundException(session.boardId(), targetId));
        osImageService.purgeIsoForNudge(target);

        // 2) 신규 자원을 ACTIVE 로 영속화.
        Long newIsoId = osImageService.completePendingIsoFromNudge(session);
        consumeSession(nudgeId);
        log.info("[osNudge] replace 완료. nudgeId={}, purgedTargetId={}, newIsoId={}",
                nudgeId, targetId, newIsoId);
        return newIsoId;
    }

    /**
     * CANCEL — 임시 파일 정리하고 세션을 폐기한다. 신규 자원은 영속화되지 않는다.
     */
    public void cancel(UUID nudgeId) {
        NudgeSession session = requireOsIsoSession(nudgeId);
        cleanupTempFile(session.pendingPayload().tempFilePath());
        consumeSession(nudgeId);
        log.info("[osNudge] cancel 완료. nudgeId={}", nudgeId);
    }

    // ---- 내부 헬퍼 -----------------------------------------------------

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

    private void cleanupTempFile(String tempFilePath) {
        if (tempFilePath == null || tempFilePath.isBlank()) return;
        try {
            Files.deleteIfExists(Path.of(tempFilePath));
        } catch (IOException e) {
            // 임시 파일 정리 실패는 흐름을 막지 않는다 — 운영자가 별도 정리.
            log.warn("[osNudge] 임시 파일 정리 실패. path={}, msg={}", tempFilePath, e.getMessage());
        }
    }
}
