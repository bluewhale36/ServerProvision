package com.example.serverprovision.execution.engine.firmware;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 2026-09-17 — AMI UpdateService 확장의 진행률 판독. GCT 안내 문서의 응답 모양이 픽스처다. */
class FlashProgressTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("FlashPercentage '37% done.' → 37 · 상태 · 대상까지 읽고, 관측 줄은 'BIOS 37% (Flashing)'")
    void parsesAmiExtension() {
        Optional<FlashProgress> p = FlashProgress.parse(JSON.readTree("""
                {"AMIUpdateService":{"@odata.type":"#AMIUpdateService.v1_0_0.AMIUpdateService",
                 "UpdateInformation":{"FlashPercentage":"37% done.","UpdateStatus":"Flashing","UpdateTarget":"BIOS"}}}
                """));

        assertThat(p).isPresent();
        assertThat(p.get().percent()).isEqualTo(37);
        assertThat(p.get().status()).isEqualTo("Flashing");
        assertThat(p.get().summary("BMC")).isEqualTo("BIOS 37% (Flashing)");
    }

    @Test
    @DisplayName("실 BMC(MS04-CE0 · 13.06.29)는 확장을 Oem 아래에 둔다 — Oem.AMIUpdateService.UpdateInformation 을 먼저 읽는다 · 값이 전부 null 이면 empty")
    void parsesOemWrappedShape() {
        Optional<FlashProgress> p = FlashProgress.parse(JSON.readTree("""
                {"Id":"UpdateService","Oem":{"AMIUpdateService":{"@odata.type":"#AMIUpdateService.v1_0_0.AMIUpdateService",
                 "PreserveConfiguration":{"BMC":true},
                 "UpdateInformation":{"FlashPercentage":"62% done.","UpdateStatus":"Flashing","UpdateTarget":"BMC"}}}}
                """));
        assertThat(p).isPresent();
        assertThat(p.get().percent()).isEqualTo(62);
        assertThat(p.get().summary("BIOS")).isEqualTo("BMC 62% (Flashing)");

        Optional<FlashProgress> idle = FlashProgress.parse(JSON.readTree("""
                {"Oem":{"AMIUpdateService":{"UpdateInformation":{"FlashPercentage":null,"UpdateStatus":null,"UpdateTarget":null}}}}
                """));
        assertThat(idle).isEmpty();
    }

    @Test
    @DisplayName("확장 노드가 없거나 비면 empty — 진행률 없음은 오류가 아니다 · 퍼센트가 없으면 축 라벨과 상태만")
    void emptyWhenMissing() {
        assertThat(FlashProgress.parse(JSON.readTree("{\"Id\":\"UpdateService\"}"))).isEmpty();
        assertThat(FlashProgress.parse(null)).isEmpty();
        Optional<FlashProgress> statusOnly = FlashProgress.parse(JSON.readTree(
                "{\"AMIUpdateService\":{\"UpdateInformation\":{\"UpdateStatus\":\"Completed\"}}}"));
        assertThat(statusOnly).isPresent();
        assertThat(statusOnly.get().percent()).isNull();
        assertThat(statusOnly.get().summary("BMC")).isEqualTo("BMC (Completed)");
    }

    @Test
    @DisplayName("퍼센트는 0~100 으로 자르고 소수 · 공백 변형도 읽는다")
    void clampsAndTolerates() {
        assertThat(FlashProgress.parse(JSON.readTree(
                "{\"AMIUpdateService\":{\"UpdateInformation\":{\"FlashPercentage\":\" 250 % done\"}}}")).get().percent()).isEqualTo(100);
    }
}
