package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-4 R5 · R7 — 원장 meta 의 화면 판독. note 는 detail 우선 · origin 폴백이고, 자원 이름(name)은
 * 표시용으로만 실려 대조 재료(target)와 분리되며 닫힘을 지나도 살아남는다.
 */
class ProvisioningHistoryMetaTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 31, 12, 0);

    private static GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    @Test
    @DisplayName("displayNote — detail 이 있으면 그것, 없으면 origin 코드, 시작 · 운영자 마커는 null")
    void displayNotePrecedence() {
        ProvisioningHistory withDetail = ProvisioningHistory.instant(server(), ProvisioningPhaseStep.BIOS_UPDATING,
                ProvisioningStatus.FAILED,
                ProvisioningHistory.flashOutcomeMeta("verify-mismatch", "목표 F29 · 확인 F27"), T);
        assertThat(withDetail.displayNote()).isEqualTo("목표 F29 · 확인 F27");

        ProvisioningHistory originOnly = ProvisioningHistory.instant(server(), ProvisioningPhaseStep.BIOS_UPDATING,
                ProvisioningStatus.FAILED, ProvisioningHistory.flashOutcomeMeta("return-timeout", null), T);
        assertThat(originOnly.displayNote()).isEqualTo("return-timeout");

        ProvisioningHistory flashOpen = ProvisioningHistory.openRunning(server(), ProvisioningPhaseStep.BIOS_UPDATING,
                T, ProvisioningHistory.flashTargetMeta("F29", 1L, "/task"));
        assertThat(flashOpen.displayNote()).isNull();

        ProvisioningHistory operator = ProvisioningHistory.instant(server(), ProvisioningPhaseStep.BIOS_UPDATING,
                ProvisioningStatus.FAILED, ProvisioningHistory.OPERATOR_ORIGIN_META, T);
        assertThat(operator.displayNote()).isNull();

        ProvisioningHistory noMeta = ProvisioningHistory.instant(server(), ProvisioningPhaseStep.NETWORK_ALLOCATING,
                ProvisioningStatus.SUCCEEDED, null, T);
        assertThat(noMeta.displayNote()).isNull();
    }

    @Test
    @DisplayName("E3.5-5-a F-2 — 게스트 원문 보고에 중첩된 raid.detail 은 사유가 아니다(최상위 키만 판독)")
    void displayNote_ignoresNestedKeysInRawReport() {
        String rawReport = "{\"boardSerial\":\"X\",\"disks\":[],\"raid\":{\"reason\":\"TOOL_MISSING\",\"detail\":\"storcli64 not found\"}}";
        ProvisioningHistory collecting = ProvisioningHistory.openRunning(server(), ProvisioningPhaseStep.INFORMATION_COLLECTING, T);
        collecting.close(ProvisioningStatus.SUCCEEDED, rawReport, T);
        assertThat(collecting.displayNote()).isNull();

        ProvisioningHistory plain = ProvisioningHistory.instant(server(), ProvisioningPhaseStep.INFORMATION_COLLECTING,
                ProvisioningStatus.SUCCEEDED, "not-json \"detail\":\"x\"", T);
        assertThat(plain.displayNote()).isNull();
    }

    @Test
    @DisplayName("E3.5-5-a F-1 — 사유가 없으면 관용 흡수 목록(filtered)을 한 줄로 잇는다 · 빈 목록은 null")
    void displayNote_fallsBackToAbsorbedList() {
        ProvisioningHistory absorbed = ProvisioningHistory.instant(server(), ProvisioningPhaseStep.INFORMATION_PERSISTING,
                ProvisioningStatus.SUCCEEDED,
                "{\"filtered\":[\"raid(TOOL_MISSING)=storcli64/storcli not found\",\"boardSerial(duplicate)=ABC\"]}", T);
        assertThat(absorbed.displayNote())
                .isEqualTo("raid(TOOL_MISSING)=storcli64/storcli not found · boardSerial(duplicate)=ABC");

        ProvisioningHistory clean = ProvisioningHistory.instant(server(), ProvisioningPhaseStep.INFORMATION_PERSISTING,
                ProvisioningStatus.SUCCEEDED, "{\"filtered\":[]}", T);
        assertThat(clean.displayNote()).isNull();
    }

    @Test
    @DisplayName("flashTargetMeta(name) — 이름은 name 키로만 실리고 target 은 버전 그대로다(R7 회귀 함정)")
    void nameDoesNotPolluteTarget() {
        ProvisioningHistory row = ProvisioningHistory.openRunning(server(), ProvisioningPhaseStep.BIOS_UPDATING,
                T, ProvisioningHistory.flashTargetMeta("BIOS 표준 이미지", "F29", 1L, "/task/2"));

        assertThat(row.flashResourceName()).isEqualTo("BIOS 표준 이미지");
        assertThat(row.flashTargetVersion()).isEqualTo("F29");
        assertThat(row.flashTaskPath()).isEqualTo("/task/2");
    }

    @Test
    @DisplayName("closeFlash — 이름 · 목표 · Task 경로가 닫힘을 지나도 살아남는다(E2-2 F-1 결)")
    void closePreservesNameAndTarget() {
        ProvisioningHistory row = ProvisioningHistory.openRunning(server(), ProvisioningPhaseStep.BIOS_UPDATING,
                T, ProvisioningHistory.flashTargetMeta("BIOS 표준 이미지", "F29", 1L, "/task/2"));

        row.closeFlash(ProvisioningStatus.SUCCEEDED, "flash-completed", "전송 완료", T.plusMinutes(2));

        assertThat(row.flashResourceName()).isEqualTo("BIOS 표준 이미지");
        assertThat(row.flashTargetVersion()).isEqualTo("F29");
        assertThat(row.flashFailureReason()).isEqualTo("flash-completed");
        assertThat(row.displayNote()).isEqualTo("전송 완료");
    }

    @Test
    @DisplayName("이름 없는 구 행 — name 은 null 이고 나머지 판독은 종전과 같다(호환)")
    void legacyMetaWithoutName() {
        ProvisioningHistory row = ProvisioningHistory.openRunning(server(), ProvisioningPhaseStep.BMC_UPDATING,
                T, ProvisioningHistory.flashTargetMeta("13.06.27", 2L, "/task/3"));

        assertThat(row.flashResourceName()).isNull();
        assertThat(row.flashTargetVersion()).isEqualTo("13.06.27");

        row.closeFlash(ProvisioningStatus.SUCCEEDED, "flash-completed", null, T.plusMinutes(2));
        assertThat(row.flashResourceName()).isNull();
        assertThat(row.flashTargetVersion()).isEqualTo("13.06.27");
    }

    @Test
    @DisplayName("close(HF11 CP5 F-1) — JSON 이 아닌 statusMeta 는 JSON 문자열로 감싸 저장(원문 보존 · status_meta CHECK 통과) · 유효 JSON 은 그대로")
    void close_wrapsNonJsonMeta() {
        GuestServer g = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
        LocalDateTime at = LocalDateTime.of(2026, 9, 5, 19, 0);
        ProvisioningHistory raw = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, at);
        assertThat(raw.close(ProvisioningStatus.SUCCEEDED, "not json \"quoted\"", at.plusSeconds(1))).isTrue();
        assertThat(raw.getStatusMeta()).isEqualTo("\"not json \\\"quoted\\\"\"");
        assertThat(raw.displayNote()).isNull();

        ProvisioningHistory ok = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, at);
        ok.close(ProvisioningStatus.SUCCEEDED, "{\"detail\":\"fine\"}", at.plusSeconds(1));
        assertThat(ok.getStatusMeta()).isEqualTo("{\"detail\":\"fine\"}");
        assertThat(ok.displayNote()).isEqualTo("fine");

        ProvisioningHistory arr = ProvisioningHistory.openRunning(g, ProvisioningPhaseStep.INFORMATION_COLLECTING, at);
        arr.close(ProvisioningStatus.SUCCEEDED, "[1,2,3]", at.plusSeconds(1));
        assertThat(arr.getStatusMeta()).isEqualTo("[1,2,3]");   // 유효 JSON(객체 아님)은 그대로 — 파서가 해석 불가로 다룬다
        assertThat(ProvisioningHistory.storableMeta(null)).isNull();
        assertThat(ProvisioningHistory.storableMeta("")).isNull();      // R-O1 — 빈 본문도 CHECK 에 걸리므로 '메타 없음' 으로
        assertThat(ProvisioningHistory.storableMeta("   ")).isNull();
    }
}
