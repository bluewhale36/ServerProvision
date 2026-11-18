package com.example.serverprovision.management.common.nudge;

import com.example.serverprovision.management.common.nudge.exception.NudgeInProgressException;
import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import com.example.serverprovision.management.common.nudge.exception.NudgeSessionExpiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * MK2 — nudge_session 메모리 저장소 (결정 #1 권장값).
 *
 * <p>{@link NudgeSession} 인스턴스를 5분 TTL 로 {@link ConcurrentMap} 에 보관한다.
 * {@link Scheduled} 회수가 1분 간격으로 만료 세션을 지우며, 그 세션의 업로드 트리는 {@link NudgeUploadDiscarder} 가 정리한다(HF30-3).</p>
 *
 * <p>결정 #3 정합 — PENDING_NUDGE DB row 미생성. confirm 시점에 본 record 의 {@code pendingPayload}
 * 만으로 자원을 ACTIVE 로 영속화한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NudgeRegistry {

	private static final Duration TTL = Duration.ofMinutes(5);

	/**
	 * 결정 #1 — 메모리 ConcurrentMap. JVM 재시작 시 모든 nudge 세션 소실 (재업로드 필요).
	 */
	private final ConcurrentMap<UUID, NudgeSession> sessions = new ConcurrentHashMap<>();

	/**
	 * 확인 요청이 처리 중인 세션(HF30-3 앵커 조건). 만료 직전에 시작한 [그래도 등록] 이 트리 해시를 계산하는 동안
	 * 회수가 그 트리를 지우지 않게 한다. 요청 하나의 수명 동안만 붙으므로 실패 뒤 재시도를 막지 않는다.
	 */
	private final Set<UUID> inUse = ConcurrentHashMap.newKeySet();

	private final NudgeUploadDiscarder nudgeUploadDiscarder;
	private final Clock clock;

	/**
	 * 새 세션 등록. 호출자는 충돌 후보 발견 시점에 본 메서드 호출 후 응답에 nudgeId 동봉.
	 */
	public NudgeSession register(
			NudgeResourceType resourceType,
			Long boardId,
			List<Long> conflictTargetIds,
			NudgePayload payload
	) {
		Instant now = clock.instant();
		NudgeSession session = new NudgeSession(
				UUID.randomUUID(),
				resourceType,
				boardId,
				conflictTargetIds,
				payload,
				now,
				now.plus(TTL)
		);
		sessions.put(session.nudgeId(), session);
		return session;
	}

	/**
	 * 세션 조회. 만료 / 부재 시 명시적 도메인 예외. 만료 세션은 지우지 않는다. 지우면 업로드 트리를 정리할 주체가 사라진다(HF30-2 D-2).
	 */
	public NudgeSession require(UUID nudgeId) {
		NudgeSession session = sessions.get(nudgeId);
		if (session == null) {
			throw new NudgeNotFoundException(nudgeId);
		}
		if (session.isExpired(clock.instant())) {
			throw new NudgeSessionExpiredException(nudgeId);
		}
		return session;
	}

	/**
	 * 업로드 트리를 쓰는 확인 처리([그래도 등록] · [기존 영구 삭제 후 등록] · [취소])를 사용 중 표지 아래에서 실행한다.
	 * 표지를 먼저 달고 {@code work} 안에서 {@link #require} 하므로, 회수가 표지 없는 만료 세션을 고른 뒤에 들어온 요청은
	 * {@link #require} 에서 만료로 거절된다. 표지는 한 번에 하나다. 같은 세션을 이미 처리 중이면 {@link NudgeInProgressException}
	 * 으로 거절해, 해시 계산 중인 트리를 [취소] 가 지우지 못하게 한다. 요청이 끝나면 떨어지므로 실패 뒤 재시도는 막지 않는다.
	 */
	public <T> T whileInUse(UUID nudgeId, Supplier<T> work) {
		if (!inUse.add(nudgeId)) {
			throw new NudgeInProgressException();
		}
		try {
			return work.get();
		} finally {
			inUse.remove(nudgeId);
		}
	}

	public void whileInUse(UUID nudgeId, Runnable work) {
		whileInUse(nudgeId, () -> {
			work.run();
			return null;
		});
	}

	/**
	 * confirm 완료 후 세션 제거. {@code resolveNudge} 의 멱등성 차단을 위해 반환값으로 제거 여부 알림.
	 */
	public boolean remove(UUID nudgeId) {
		return sessions.remove(nudgeId) != null;
	}

	/**
	 * 만료 회수 — 1분 간격. 사용 중인 세션은 다음 회차로 미루고, 지운 세션의 업로드 트리를 정리한다.
	 */
	@Scheduled(fixedDelay = 60_000L)
	public void pruneExpired() {
		Instant now = clock.instant();
		int removed = 0;
		for (NudgeSession session : sessions.values()) {
			if (session.isExpired(now) && !inUse.contains(session.nudgeId())
					&& sessions.remove(session.nudgeId(), session)) {
				discardQuietly(session);
				removed++;
			}
		}
		if (removed > 0) {
			log.info("[nudge] 만료 세션 {} 건 회수 (활성 {} 건 잔존)", removed, sessions.size());
		}
	}

	/** 회수는 세션마다 독립이다. 한 트리의 정리 실패가 나머지 세션 회수를 멈추지 않게 남기고 넘어간다. */
	private void discardQuietly(NudgeSession session) {
		try {
			nudgeUploadDiscarder.discard(session, "nudge.expired");
		} catch (RuntimeException e) {
			log.warn("[nudge.expired] 업로드 트리 정리 실패. nudgeId={}", session.nudgeId(), e);
		}
	}
}
