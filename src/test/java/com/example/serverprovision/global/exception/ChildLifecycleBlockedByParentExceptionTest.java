package com.example.serverprovision.global.exception;

import com.example.serverprovision.global.marker.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S22-4 — 자식 lifecycle 거절 문장은 상태 · 동작 코드(DELETED · restore)를 화면 말로 바꿔 쓰고 합니다체로 끝난다.
 */
class ChildLifecycleBlockedByParentExceptionTest {

	private static ChildLifecycleBlockedByParentException blocked(String parentState, String action) {
		return new ChildLifecycleBlockedByParentException(
				ResourceType.BOARD_MODEL, 1L, parentState, ResourceType.BIOS_BUNDLE, 2L, action, "X11DPi");
	}

	@Test
	@DisplayName("삭제된 부모 + 복구 : 코드 원문 없이 '복구할 수 없습니다' 와 다음 행동을 말한다")
	void deletedParentRestore() {
		assertThat(blocked("DELETED", "restore").getMessage())
				.isEqualTo("부모 자원 'X11DPi' 이(가) 삭제 상태라 복구할 수 없습니다. 부모부터 복구해야 합니다.");
	}

	@Test
	@DisplayName("비활성 부모 + 활성화 / Deprecated 부모 + 해제 : 동작 이름을 화면 말로 쓴다")
	void otherCombinations() {
		assertThat(blocked("DISABLED", "enable").getMessage())
				.isEqualTo("부모 자원 'X11DPi' 이(가) 비활성 상태라 활성화할 수 없습니다. 부모를 먼저 활성화해야 합니다.");
		assertThat(blocked("DEPRECATED", "undeprecate").getMessage())
				.isEqualTo("부모 자원 'X11DPi' 이(가) 사용 중단 상태라 사용 중단 권고를 해제할 수 없습니다. "
						+ "부모의 사용 중단 권고를 먼저 해제해야 합니다.");
	}

	@Test
	@DisplayName("알 수 없는 코드 : 원문을 화면에 싣지 않는다")
	void unknownCodesAreNotExposed() {
		assertThat(blocked("PURGED", "purge").getMessage())
				.doesNotContain("PURGED").doesNotContain("purge")
				.endsWith("부모 상태를 먼저 확인해야 합니다.");
	}
}
