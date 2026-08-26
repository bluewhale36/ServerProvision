package com.example.serverprovision.execution.engine.setting;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-2 D-6 — 저장소가 정본인 보드별 Fan Profile 4종이 단일행 JSON 으로 파싱되고 strMode 가 FAN_PROFILE 인지 고정한다. */
class FanProfileResourcesTest {

    private final FanProfileResources resources = new FanProfileResources(new ObjectMapper());

    @Test
    @DisplayName("4 보드(MS03-CE0 · MS04-CE0 · MS73-HB1 · MS74-HB0) 자원이 있고 strMode 는 FAN_PROFILE 이다")
    void fourBoardsPresent() {
        for (String board : List.of("MS03-CE0", "MS04-CE0", "MS73-HB1", "MS74-HB0")) {
            FanProfileResources.FanProfile profile = resources.forBoard(board).orElseThrow();
            assertThat(profile.mode()).as(board).isEqualTo("FAN_PROFILE");
            assertThat(profile.body().path("arrProfile").size()).as(board).isEqualTo(2);
            assertThat(profile.boardModelName()).isEqualTo(board);
        }
    }

    @Test
    @DisplayName("자원이 없는 보드 · 보드 미상은 empty — 항목 SKIPPED 의 근거")
    void unknownBoardIsEmpty() {
        assertThat(resources.forBoard("RX1330M6")).isEmpty();
        assertThat(resources.forBoard(null)).isEmpty();
        assertThat(resources.forBoard("")).isEmpty();
    }

    @Test
    @DisplayName("같은 보드는 한 번만 읽는다(캐시) — 같은 인스턴스")
    void cached() {
        assertThat(resources.forBoard("MS74-HB0").orElseThrow()).isSameAs(resources.forBoard("MS74-HB0").orElseThrow());
    }
}
