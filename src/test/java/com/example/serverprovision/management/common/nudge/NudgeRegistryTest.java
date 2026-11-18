package com.example.serverprovision.management.common.nudge;

import com.example.serverprovision.management.common.nudge.exception.NudgeInProgressException;
import com.example.serverprovision.management.common.nudge.exception.NudgeSessionExpiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HF30-3 D13 — 만료 회수와 사용 중 표지(앵커 CP1 조건 1).
 */
@ExtendWith(MockitoExtension.class)
class NudgeRegistryTest {

	private static final Instant T0 = Instant.parse("2026-11-17T03:00:00Z");

	@Mock NudgeUploadDiscarder discarder;

	MutableClock clock;
	NudgeRegistry registry;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(T0);
		registry = new NudgeRegistry(discarder, clock);
	}

	private NudgeSession register() {
		return registry.register(NudgeResourceType.BIOS, 1L, List.of(9L),
				new ContentNudgePayload("n", "1.0", "h", "/storage/bios/x", Map.of()));
	}

	private void passExpiry() {
		clock.advance(Duration.ofMinutes(6));
	}

	@Test
	@DisplayName("만료 전 : 회수하지 않는다")
	void prune_notExpired_keeps() {
		NudgeSession session = register();

		registry.pruneExpired();

		assertThat(registry.require(session.nudgeId())).isEqualTo(session);
		verify(discarder, never()).discard(any(), any());
	}

	@Test
	@DisplayName("만료 · 표지 없음 : 회수하고 업로드 트리를 정리한다")
	void prune_expired_removesAndDiscards() {
		NudgeSession session = register();
		passExpiry();

		registry.pruneExpired();

		verify(discarder).discard(session, "nudge.expired");
		assertThat(registry.remove(session.nudgeId())).isFalse();
	}

	@Test
	@DisplayName("만료 + 사용 중 : 회수하지 않고, 표지가 떨어진 다음 회차에 회수와 정리를 한다")
	void prune_expiredInUse_waitsForNextRound() {
		NudgeSession session = register();

		registry.whileInUse(session.nudgeId(), () -> {
			registry.require(session.nudgeId());   // 만료 전에 확인을 시작했다
			passExpiry();                           // 해시 계산 도중 만료가 지났다
			registry.pruneExpired();
			verify(discarder, never()).discard(any(), any());
			return null;
		});
		registry.pruneExpired();

		verify(discarder).discard(session, "nudge.expired");
	}

	@Test
	@DisplayName("require : 만료 세션은 거절만 하고 지우지 않는다(회수가 트리를 정리할 수 있게)")
	void require_expired_rejectsWithoutRemoving() {
		NudgeSession session = register();
		passExpiry();

		assertThatThrownBy(() -> registry.require(session.nudgeId())).isInstanceOf(NudgeSessionExpiredException.class);
		registry.pruneExpired();

		verify(discarder).discard(session, "nudge.expired");
	}

	@Test
	@DisplayName("whileInUse : 안에서 만료 세션을 require 하면 거절되고, 표지는 떨어져 회수가 가져간다")
	void whileInUse_expiredRequire_releasesMarker() {
		NudgeSession session = register();
		passExpiry();

		assertThatThrownBy(() -> registry.whileInUse(session.nudgeId(), () -> registry.require(session.nudgeId())))
				.isInstanceOf(NudgeSessionExpiredException.class);
		registry.pruneExpired();

		verify(discarder).discard(session, "nudge.expired");
	}

	@Test
	@DisplayName("whileInUse : work 가 예외를 던져도 표지가 떨어진다(실패 뒤 재시도 가능)")
	void whileInUse_workThrows_releasesMarker() {
		NudgeSession session = register();

		assertThatThrownBy(() -> registry.whileInUse(session.nudgeId(), () -> {
			throw new IllegalStateException("등록 실패");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(registry.whileInUse(session.nudgeId(), () -> registry.require(session.nudgeId()))).isEqualTo(session);
	}

	@Test
	@DisplayName("whileInUse : 같은 세션을 이미 처리 중이면 NudgeInProgressException(해시 계산 중 [취소] 차단)")
	void whileInUse_alreadyInUse_rejects() {
		NudgeSession session = register();

		registry.whileInUse(session.nudgeId(), () -> {
			assertThatThrownBy(() -> registry.whileInUse(session.nudgeId(), () -> { }))
					.isInstanceOf(NudgeInProgressException.class);
			return null;
		});
	}

	@Test
	@DisplayName("회수 : 한 세션 정리가 실패해도 다음 세션은 회수된다")
	void prune_oneDiscardFails_othersContinue() {
		NudgeSession first = register();
		NudgeSession second = register();
		passExpiry();
		doThrow(new IllegalStateException("io")).when(discarder).discard(eq(first), any());

		registry.pruneExpired();

		verify(discarder).discard(second, "nudge.expired");
		assertThat(registry.remove(first.nudgeId())).isFalse();
		assertThat(registry.remove(second.nudgeId())).isFalse();
	}

	/** 테스트에서 시각을 앞으로 돌리는 시계. */
	static final class MutableClock extends Clock {
		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
