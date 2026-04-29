package com.example.serverprovision.management.common.nudge;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MK2 — 업로드 후 해시 충돌이 발견되어 사용자 confirm 을 기다리는 임시 세션.
 *
 * <p>결정 박스 #1 (CP1 v3) 에 따라 단일 인스턴스 메모리 보관. {@link NudgeRegistry} 가
 * {@code ConcurrentMap<UUID, NudgeSession>} + 5분 TTL 로 관리한다. 5분 만료된 세션은
 * {@code @Scheduled} pruner 가 임시 row + 업로드 파일 cleanup 수행 후 제거.</p>
 *
 * <p>결정 박스 #3 (CP1 v3) 에 따라 PENDING_NUDGE 임시 자원의 DB row 는 생성하지 않는다.
 * 본 record 가 단일 진실원이며 {@code pendingPayload} 는 confirm 시점에 자원을 ACTIVE 로 영속화
 * 하기 위해 필요한 모든 메타 + 파일 경로를 담는다.</p>
 *
 * @param nudgeId            UUID v4
 * @param resourceType       어느 도메인 컨트롤러가 발급한 세션인지
 * @param boardId            BIOS/BMC/Subprogram 용 보드 id (OS/ISO 의 경우 osImageId 로 의미 전환)
 * @param conflictTargetIds  사용자에게 노출할 기존 충돌 후보 자원 id (soft-deleted 만)
 * @param pendingPayload     confirm 시 자원 영속화에 사용할 임시 파일/메타 ({@link PendingPayload})
 * @param createdAt          세션 생성 시각
 * @param expiresAt          createdAt + 5분
 */
public record NudgeSession(
        UUID nudgeId,
        NudgeResourceType resourceType,
        Long boardId,
        List<Long> conflictTargetIds,
        PendingPayload pendingPayload,
        Instant createdAt,
        Instant expiresAt
) {

    /**
     * 세션이 만료됐는지 확인. {@code @Scheduled} pruner 와 confirm 핸들러가 함께 사용한다.
     */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /**
     * confirm 시점에 자원을 ACTIVE 로 영속화하기 위해 필요한 임시 메타.
     *
     * <p>각 도메인의 자원 형태가 다르므로 보편 필드만 노출하고 도메인 추가 메타는
     * {@code attributes} 맵에 담는다. 파일 본체는 임시 경로에 이미 저장되어 있으며 confirm 시
     * 정식 경로로 이동/등록된다. cancel 시는 임시 경로를 cleanup.</p>
     */
    public record PendingPayload(
            String name,
            String version,
            String manifestHash,
            String tempFilePath,
            java.util.Map<String, String> attributes
    ) {}
}
