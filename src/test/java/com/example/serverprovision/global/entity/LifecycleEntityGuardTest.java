package com.example.serverprovision.global.entity;

import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R2-2 — LifecycleEntity 부모-자식 가드 SSOT 진리표.
 *
 * <p>서버 가드(throw 조건) 와 뷰 capability 플래그가 공유하는 단일 소스를 직접 검증한다.
 * {@link OSMetadata} 를 {@link LifecycleEntity} 구현체로 사용. 특히 {@code DELETED} comprehensive 분기
 * (부모 삭제 시 enable / undeprecate 도 차단) 가 핵심 — 우회 경로로 "삭제된 부모 밑 active 자식" 모순 방어.</p>
 */
class LifecycleEntityGuardTest {

	private OSMetadata parent(boolean enabled, boolean deprecated, boolean deleted) {
		return OSMetadata.builder()
				.id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.5")
				.isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted).build();
	}

	@Test
	@DisplayName("ACTIVE 부모 : 자식 액션 전부 허용")
	void active_allowsAll() {
		OSMetadata p = parent(true, false, false);
		assertThat(p.childEnableBlockReason()).isNull();
		assertThat(p.blocksChildEnable()).isFalse();
		assertThat(p.blocksChildRestore()).isFalse();
		assertThat(p.blocksChildUndeprecate()).isFalse();
	}

	@Test
	@DisplayName("DISABLED 부모 : enable 만 차단 (reason=DISABLED)")
	void disabled_blocksEnable() {
		OSMetadata p = parent(false, false, false);
		assertThat(p.childEnableBlockReason()).isEqualTo("DISABLED");
		assertThat(p.blocksChildEnable()).isTrue();
		assertThat(p.blocksChildRestore()).isFalse();
		assertThat(p.blocksChildUndeprecate()).isFalse();
	}

	@Test
	@DisplayName("DEPRECATED 부모 : enable + undeprecate 차단 (reason=DEPRECATED)")
	void deprecated_blocksEnableAndUndeprecate() {
		OSMetadata p = parent(true, true, false);
		assertThat(p.childEnableBlockReason()).isEqualTo("DEPRECATED");
		assertThat(p.blocksChildEnable()).isTrue();
		assertThat(p.blocksChildRestore()).isFalse();
		assertThat(p.blocksChildUndeprecate()).isTrue();
	}

	@Test
	@DisplayName("DELETED 부모 (comprehensive) : enable + restore + undeprecate 모두 차단 (reason=DELETED 우선)")
	void deleted_blocksAll() {
		// 삭제됐지만 enabled 플래그가 잔존하는 우회 경로 핵심 케이스.
		OSMetadata p = parent(true, false, true);
		assertThat(p.childEnableBlockReason()).isEqualTo("DELETED");
		assertThat(p.blocksChildEnable()).isTrue();
		assertThat(p.blocksChildRestore()).isTrue();
		assertThat(p.blocksChildUndeprecate()).isTrue();
	}
}
