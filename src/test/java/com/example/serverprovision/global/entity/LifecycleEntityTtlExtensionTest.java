package com.example.serverprovision.global.entity;

import com.example.serverprovision.global.lifecycle.exception.IllegalLifecycleTransitionException;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HF4-1 — 보존기간 연장 가산(ttl_extension_days)과 만료 SSOT({@code trashExpiresAt}) 검증.
 *
 * <p>{@link OSMetadata} 를 {@link LifecycleEntity} 구현체로 사용 (LifecycleEntityGuardTest 선례).
 * 연장 = 만료일 +N일 <b>가산</b> — trashed_at(휴지통 이동 시각)은 불변이어야 한다.</p>
 */
class LifecycleEntityTtlExtensionTest {

	private static final Duration TTL_30 = Duration.ofDays(30);

	private OSMetadata entity() {
		return OSMetadata.builder()
				.id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
				.build();
	}

	@Test
	@DisplayName("가산 누적 — extendTrashTtl(30) 2회 → 만료 = trashedAt + TTL(30) + 60일, trashedAt 불변")
	void extend_accumulates_withoutShiftingTrashedAt() {
		OSMetadata e = entity();
		e.markTrashed(null);
		var trashedAt = e.getTrashedAt();

		e.extendTrashTtl(30);
		e.extendTrashTtl(30);

		assertThat(e.getTtlExtensionDays()).isEqualTo(60);
		assertThat(e.getTrashedAt()).isEqualTo(trashedAt);   // 이동 시각 불변 (재기준 방식 폐기)
		assertThat(e.trashExpiresAt(TTL_30)).isEqualTo(trashedAt.plus(Duration.ofDays(90)));
	}

	@Test
	@DisplayName("trashed 상태 아님 — 만료 null + 연장은 invariant 가드로 거절")
	void notTrashed_expiryNull_andExtendRejected() {
		OSMetadata e = entity();

		assertThat(e.trashExpiresAt(TTL_30)).isNull();
		assertThatThrownBy(() -> e.extendTrashTtl(30))
				.isInstanceOf(IllegalLifecycleTransitionException.class);
	}

	@Test
	@DisplayName("markTrashed 진입 — 연장 누적 0 초기화 (재삭제 시 이전 연장 이월 방지)")
	void markTrashed_resetsExtension() {
		OSMetadata e = entity();
		e.markTrashed(null);
		e.extendTrashTtl(30);

		e.markTrashed(null);   // 복원 후 재삭제 등 휴지통 재진입 가정

		assertThat(e.getTtlExtensionDays()).isZero();
		assertThat(e.trashExpiresAt(TTL_30)).isEqualTo(e.getTrashedAt().plus(TTL_30));
	}

	@Test
	@DisplayName("clearTrashed (복원 경로) — 연장 누적 0 초기화 + 만료 null")
	void clearTrashed_resetsExtension() {
		OSMetadata e = entity();
		e.markTrashed(null);
		e.extendTrashTtl(30);

		e.clearTrashed();

		assertThat(e.getTtlExtensionDays()).isZero();
		assertThat(e.trashExpiresAt(TTL_30)).isNull();
	}
}
