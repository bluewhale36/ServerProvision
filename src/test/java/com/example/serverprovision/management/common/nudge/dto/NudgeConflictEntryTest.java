package com.example.serverprovision.management.common.nudge.dto;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S22-4 — 중복 확인 창은 상태 상수 원문(SOFT_DELETED 등) 대신 화면 이름을 보인다.
 * 응답 JSON 에 {@code stateLabel} 이 함께 실리고, JS 판정용 {@code state} 원문은 그대로 남는다.
 */
class NudgeConflictEntryTest {

	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	@DisplayName("직렬화 : state 원문과 함께 화면 이름 stateLabel 을 싣는다")
	void serializesStateLabel() {
		NudgeConflictEntry entry = new NudgeConflictEntry(12L, LifecycleStage.SOFT_DELETED, "abc", "BIOS", "1.2", "BIOS", null);

		JsonNode node = JSON.valueToTree(entry);

		assertThat(node.get("state").asString()).isEqualTo("SOFT_DELETED");
		assertThat(node.get("stateLabel").asString()).isEqualTo("휴지통");
	}

	@Test
	@DisplayName("화면 이름 : 상태 셋 모두 상수 원문이 아닌 이름을 가진다")
	void everyStageHasScreenName() {
		assertThat(LifecycleStage.ACTIVE.description()).isEqualTo("정상");
		assertThat(LifecycleStage.DEPRECATED.description()).isEqualTo("사용 중단");
		assertThat(LifecycleStage.SOFT_DELETED.description()).isEqualTo("휴지통");
	}

	@Test
	@DisplayName("HF30-3 of : 자원명 입력이 맞춰야 하는 displayName 을 서버 대조와 같은 Markable 값으로 싣는다")
	void of_carriesDisplayName() {
		BoardBIOS bios = BoardBIOS.builder().id(7L).name("MZ33 BIOS").version("6.5")
				.manifestHash("h7").isDeleted(true).isDeprecated(false).build();

		NudgeConflictEntry entry = NudgeConflictEntry.of(bios, bios.getName(), bios.getVersion());
		JsonNode node = JSON.valueToTree(entry);

		assertThat(entry.id()).isEqualTo(7L);
		assertThat(entry.state()).isEqualTo(LifecycleStage.SOFT_DELETED);
		assertThat(node.get("displayName").asString()).isEqualTo(bios.displayName());
	}
}
