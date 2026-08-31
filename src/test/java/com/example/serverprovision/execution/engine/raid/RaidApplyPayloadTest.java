package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3.5-3 V10 — 집행 payload 의 계획 파생과 직렬화 왕복(agent.sh 계약), 칩 판별 힌트의 형식.
 */
class RaidApplyPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RaidPlan plan() {
        return new RaidPlan(true,
                List.of(new PlannedVolume("spvR1V1", RaidLevel.RAID1, List.of("252:0", "252:1"),
                        1L, PlannedVolumeRole.OS, 1)),
                List.of(new PlannedPassthrough("252:4", 1L, PlannedVolumeRole.DATA, 2)),
                List.of(), List.of(), null);
    }

    @Test
    @DisplayName("from — 계획에서 집행에 필요한 것만 추린다(볼륨 명세 · jbod · 선행 삭제)")
    void from_extractsExecutionEssentials() {
        RaidApplyPayload payload = RaidApplyPayload.from(plan());
        assertThat(payload.deleteExisting()).isTrue();
        assertThat(payload.volumes()).singleElement().satisfies(v -> {
            assertThat(v.name()).isEqualTo("spvR1V1");
            assertThat(v.level()).isEqualTo(RaidLevel.RAID1);
            assertThat(v.slots()).containsExactly("252:0", "252:1");
        });
        assertThat(payload.jbod()).containsExactly("252:4");
    }

    @Test
    @DisplayName("직렬화 왕복 — JSON 으로 나갔다 돌아와도 같다(agent.sh 파싱 계약의 형태 고정)")
    void jsonRoundTrip() {
        RaidApplyPayload payload = RaidApplyPayload.from(plan());
        String json = objectMapper.writeValueAsString(payload);
        assertThat(json).contains("\"name\":\"spvR1V1\"").contains("\"level\":\"RAID1\"")
                .contains("\"deleteExisting\":true");
        assertThat(objectMapper.readValue(json, RaidApplyPayload.class)).isEqualTo(payload);
    }

    @Test
    @DisplayName("동결 계획(RaidPlan)도 직렬화 왕복 — PLANNED statusMeta 의 형태 고정")
    void frozenPlanRoundTrip() {
        String json = objectMapper.writeValueAsString(plan());
        assertThat(objectMapper.readValue(json, RaidPlan.class)).isEqualTo(plan());
    }

    @Test
    @DisplayName("칩 힌트 — id=계열 공백 구분 · 선언 순, lspci 판별과 같은 SSOT")
    void agentChipHint_format() {
        assertThat(RaidChipFamily.agentChipHint()).isEqualTo("1000:0097=MPT_IR 1000:005d=MEGARAID");
        assertThat(RaidChipFamily.fromLspci("RAID bus controller [0104]: ... [1000:005d]"))
                .contains(RaidChipFamily.MEGARAID);
        assertThat(RaidChipFamily.fromLspci("Serial Attached SCSI controller ... [1000:0097]"))
                .contains(RaidChipFamily.MPT_IR);
        assertThat(RaidChipFamily.fromLspci("no raid here")).isEmpty();
    }
}
