package com.example.serverprovision.execution.engine.raid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E3.5-1 — 봉투(base64) · 계열별 원문의 정규화. 픽스처는 2026-08-31 실측 원문
 * (9361-8i: storcli 007.2508 JSON / CRA3338: sas3ircu 04.00 display · lspci -nn -vv)이라
 * 이 테스트가 곧 실기 계약의 회귀 가드다.
 */
class RaidInventoryParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RaidInventoryParser parser = new RaidInventoryParser(objectMapper);

    private static String fixture(String name) {
        try (var in = RaidInventoryParserTest.class.getResourceAsStream("/raid/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | NullPointerException e) {
            throw new UncheckedIOException(new IOException("픽스처 없음: " + name, e));
        }
    }

    private static String b64(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String megaRaidEnvelope() {
        return "{\"tool\":\"storcli64\",\"lspci_b64\":\"" + b64(fixture("mr-lspci-nnvv.txt"))
                + "\",\"pd_b64\":\"" + b64(fixture("mr-pd-all.json"))
                + "\",\"vd_b64\":\"" + b64(fixture("mr-vd-all.json"))
                + "\",\"c0_b64\":\"" + b64(fixture("mr-c0-show-all.json")) + "\"}";
    }

    @Test
    @DisplayName("MegaRAID 실측 — 카드(1000:9361) · 물리 디스크 8 · 볼륨 2(RAID1 + RAID5) 정규화")
    void megaRaid_realFixture() {
        RaidInventory inv = parser.parse(megaRaidEnvelope());

        assertThat(inv.card().chipFamily()).isEqualTo(RaidChipFamily.MEGARAID);
        assertThat(inv.card().pciSubsystemId()).isEqualTo("1000:9361");
        assertThat(inv.card().model()).isEqualTo("AVAGO MegaRAID SAS 9361-8i");
        assertThat(inv.card().firmware()).isEqualTo("4.680.00-8577");

        assertThat(inv.disks()).hasSize(8);
        RaidPhysicalDisk ssd = inv.disks().stream().filter(d -> d.slot().equals("252:0")).findFirst().orElseThrow();
        assertThat(ssd.type()).isEqualTo("SSD");
        assertThat(ssd.transport()).isEqualTo("SATA");
        assertThat(ssd.size()).isEqualTo("446.625 GB");   // 원문 보존 — 계급 스냅은 E3.5-2
        assertThat(ssd.volumeRef()).isEqualTo("DG0");
        assertThat(ssd.serial()).isNotBlank();

        assertThat(inv.volumes()).hasSize(2);
        RaidExistingVolume vd0 = inv.volumes().stream().filter(v -> v.id().equals("VD0")).findFirst().orElseThrow();
        assertThat(vd0.level()).isEqualTo("RAID1");
        assertThat(vd0.memberSlots()).containsExactlyInAnyOrder("252:0", "252:1");
        assertThat(vd0.name()).isEmpty();   // 실측 — 기존 볼륨은 이름이 빈 값(이름 규약 볼륨과 구분됨)
        assertThat(vd0.wwn()).isEqualTo("600605b00d18aa1e322807f9084a72aa");   // W11 — VD Properties 의 SCSI NAA Id(E3.5-4)
        RaidExistingVolume vd1 = inv.volumes().stream().filter(v -> v.id().equals("VD1")).findFirst().orElseThrow();
        assertThat(vd1.level()).isEqualTo("RAID5");
        assertThat(vd1.memberSlots()).hasSize(6);
    }

    @Test
    @DisplayName("IR 실측 — 카드(1458:3008) · 디스크 2(볼륨 소속 파생) · IR RAID1 볼륨 1 정규화")
    void ir_realFixture() {
        String envelope = "{\"tool\":\"sas3ircu\",\"lspci_b64\":\"" + b64(fixture("cra-lspci-nnvv.txt"))
                + "\",\"display_b64\":\"" + b64(fixture("cra-display.txt")) + "\"}";

        RaidInventory inv = parser.parse(envelope);

        assertThat(inv.card().chipFamily()).isEqualTo(RaidChipFamily.MPT_IR);
        assertThat(inv.card().pciSubsystemId()).isEqualTo("1458:3008");
        assertThat(inv.card().model()).isEqualTo("SAS3008");
        assertThat(inv.card().firmware()).isEqualTo("15.00.00.00");

        assertThat(inv.volumes()).singleElement().satisfies(v -> {
            assertThat(v.id()).isEqualTo("323");
            assertThat(v.level()).isEqualTo("RAID1");
            assertThat(v.memberSlots()).containsExactly("1:0", "1:1");
            assertThat(v.name()).isNull();   // display 는 이름을 내지 않는다(실측)
            assertThat(v.wwn()).isEqualTo("0097bf86d7e97988");   // W11 — Volume wwid(E3.5-4)
        });
        assertThat(inv.disks()).hasSize(2);
        RaidPhysicalDisk d0 = inv.disks().get(0);
        assertThat(d0.slot()).isEqualTo("1:0");
        assertThat(d0.type()).isEqualTo("HDD");
        assertThat(d0.transport()).isEqualTo("SAS");
        assertThat(d0.size()).isEqualTo("3815447 MB");
        assertThat(d0.volumeRef()).isEqualTo("323");
        assertThat(d0.serial()).isEqualTo("WQB0YS0C0000K5049W9A");
    }

    @Test
    @DisplayName("봉투 손상 — JSON 이 아니면 ReportUnparsable (원문은 원장 보존 전제)")
    void malformedEnvelope_throws() {
        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOf(RaidInventoryParser.ReportUnparsableException.class);
    }

    @Test
    @DisplayName("lspci 에 지원 칩이 없으면 ReportUnparsable — 미지 카드를 조용히 흡수하지 않는다")
    void unsupportedChip_throws() {
        String envelope = "{\"tool\":\"storcli64\",\"lspci_b64\":\"" + b64("c6:00.0 RAID bus controller [0104]: Other [1000:00ff]") + "\"}";
        assertThatThrownBy(() -> parser.parse(envelope))
                .isInstanceOf(RaidInventoryParser.ReportUnparsableException.class)
                .hasMessageContaining("지원 칩");
    }

    @Test
    @DisplayName("IR 계열인데 display 원문이 없으면 ReportUnparsable")
    void ir_missingDisplay_throws() {
        String envelope = "{\"tool\":\"sas3ircu\",\"lspci_b64\":\"" + b64(fixture("cra-lspci-nnvv.txt")) + "\"}";
        assertThatThrownBy(() -> parser.parse(envelope))
                .isInstanceOf(RaidInventoryParser.ReportUnparsableException.class);
    }

    @Test
    @DisplayName("직렬화 왕복 — 적재 JSON 을 관용 파싱으로 되읽어도 같은 구조 (상세 화면 조회 경로)")
    void roundTrip() {
        RaidInventory inv = parser.parse(megaRaidEnvelope());
        String json = objectMapper.writeValueAsString(inv);
        RaidInventory back = objectMapper.readValue(json, RaidInventory.class);
        assertThat(back.card().pciSubsystemId()).isEqualTo("1000:9361");
        assertThat(back.disks()).hasSize(8);
        assertThat(back.volumes()).hasSize(2);
    }
}
