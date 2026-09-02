package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RAID 인벤토리 보고 파서(E3.5-1) — 에이전트가 {@code RAID_INVENTORY_COLLECTING} statusMeta 로 보낸
 * 봉투(JSON + base64 원문)를 {@link RaidInventory} 로 정규화한다. 원문 채집은 에이전트, 해석은 서버
 * ("원문 보고 · 서버 파싱" — 진단 수집 관례). 픽스처는 2026-08-31 실측 원문(storcli 007.2508 · sas3ircu 04.00).
 *
 * <p>봉투 계약(agent.sh 와의 SSOT): {@code {"tool":"storcli64|storcli|sas3ircu","lspci_b64":..,
 * "pd_b64":..,"vd_b64":..,"c0_b64":..,"display_b64":..}} — 원문에 개행이 있어 base64 로 나른다.
 * 칩 판별은 도구 이름이 아니라 lspci 의 Vendor:Device 가 한다(0097 = IR · 005d = MegaRAID).</p>
 */
@Component
@RequiredArgsConstructor
public class RaidInventoryParser {

    private static final Pattern SUBSYSTEM = Pattern.compile(
            "Subsystem:[^\\[\\n]*\\[([0-9a-fA-F]{1,4}):([0-9a-fA-F]{1,4})]");

    private final ObjectMapper objectMapper;

    /** 봉투 · 원문의 계약 위반 — 원문은 원장 statusMeta 가 보존하므로 여기서는 사유만 나른다. */
    public static class ReportUnparsableException extends RuntimeException {
        public ReportUnparsableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public RaidInventory parse(String statusMeta) {
        try {
            JsonNode envelope = objectMapper.readTree(statusMeta);
            String lspci = decode(envelope, "lspci_b64");
            if (lspci == null) {
                throw new ReportUnparsableException("봉투에 lspci_b64 가 없습니다", null);
            }
            RaidChipFamily family = familyOf(lspci);
            String subsystem = subsystemOf(lspci);
            if (family == RaidChipFamily.MEGARAID) {
                return parseMegaRaid(subsystem, decode(envelope, "pd_b64"),
                        decode(envelope, "vd_b64"), decode(envelope, "c0_b64"));
            }
            return parseIr(subsystem, decode(envelope, "display_b64"));
        } catch (ReportUnparsableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ReportUnparsableException("RAID 인벤토리 보고 해석 실패 : " + e.getMessage(), e);
        }
    }

    private RaidChipFamily familyOf(String lspci) {
        // 칩 id 의 SSOT 는 RaidChipFamily.chipPciIds — 에이전트 동봉 힌트와 같은 집합 · 같은 순서로 판별한다.
        return RaidChipFamily.fromLspci(lspci).orElseThrow(() ->
                new ReportUnparsableException("지원 칩(" + RaidChipFamily.agentChipHint() + ")이 lspci 에 없습니다", null));
    }

    private String subsystemOf(String lspci) {
        Matcher m = SUBSYSTEM.matcher(lspci);
        if (!m.find()) {
            return null;   // -nn 단독 출력 등 — 대조는 생략되고 카드 자체는 세워진다(관용)
        }
        return normalizePair(m.group(1), m.group(2));
    }

    /** {@code PciSubsystemId.toDisplay()} 와 같은 소문자 4자리 쌍 — 대조 키의 표기 통일. */
    private String normalizePair(String vendor, String device) {
        return String.format("%04x:%04x", Integer.parseInt(vendor, 16), Integer.parseInt(device, 16));
    }

    // ── MegaRAID (storcli JSON) ───────────────────────────────────────────────

    private RaidInventory parseMegaRaid(String subsystem, String pdJson, String vdJson, String c0Json) {
        String model = null;
        String firmware = null;
        if (c0Json != null) {
            JsonNode basics = responseData(c0Json).path("Basics");
            model = text(basics, "Model");
            firmware = text(responseData(c0Json).path("Version"), "Firmware Version");
        }
        List<RaidPhysicalDisk> disks = new ArrayList<>();
        if (pdJson != null) {
            JsonNode data = responseData(pdJson);
            for (Map.Entry<String, JsonNode> field : data.properties()) {
                String key = field.getKey();
                if (!key.startsWith("Drive /") || key.contains("Detailed")) {
                    continue;
                }
                JsonNode row = field.getValue().path(0);
                String serial = text(data.path(key + " - Detailed Information")
                        .path(key + " Device attributes"), "SN");
                String dg = text(row, "DG");
                disks.add(new RaidPhysicalDisk(text(row, "EID:Slt"), text(row, "Med"), text(row, "Intf"),
                        text(row, "Size"), text(row, "State"), trim(text(row, "Model")), trim(serial),
                        dg == null || dg.equals("-") ? null : "DG" + dg));
            }
        }
        List<RaidExistingVolume> volumes = new ArrayList<>();
        if (vdJson != null) {
            JsonNode data = responseData(vdJson);
            for (Map.Entry<String, JsonNode> field : data.properties()) {
                String key = field.getKey();
                if (!key.startsWith("/c0/v")) {
                    continue;
                }
                JsonNode row = field.getValue().path(0);
                String vdNo = key.substring(key.lastIndexOf('v') + 1);
                List<String> members = new ArrayList<>();
                for (JsonNode pd : data.path("PDs for VD " + vdNo)) {
                    members.add(text(pd, "EID:Slt"));
                }
                // WWN 은 "VD{N} Properties" 블록의 SCSI NAAB Id 가 아니라 "SCSI NAA Id" 다(2026-08-31 실측)
                String wwn = text(data.path("VD" + vdNo + " Properties"), "SCSI NAA Id");
                volumes.add(new RaidExistingVolume("VD" + vdNo, text(row, "TYPE"), text(row, "Size"),
                        text(row, "State"), text(row, "Name"), members, wwn));
            }
        }
        return new RaidInventory(
                new DetectedRaidCard(RaidChipFamily.MEGARAID, subsystem, model, firmware), disks, volumes);
    }

    private JsonNode responseData(String storcliJson) {
        return objectMapper.readTree(storcliJson).path("Controllers").path(0).path("Response Data");
    }

    // ── IR (sas3ircu display 텍스트) ──────────────────────────────────────────

    private RaidInventory parseIr(String subsystem, String display) {
        if (display == null) {
            throw new ReportUnparsableException("IR 계열인데 display_b64 가 없습니다", null);
        }
        String model = lineValue(display, "Controller type");
        String firmware = lineValue(display, "Firmware version");

        List<RaidExistingVolume> volumes = new ArrayList<>();
        Matcher vol = Pattern.compile("IR volume \\d+\\n(.*?)(?=\\n-{10,}|\\nIR volume |\\z)", Pattern.DOTALL)
                .matcher(display);
        while (vol.find()) {
            String block = vol.group(1);
            List<String> members = new ArrayList<>();
            Matcher phy = Pattern.compile("PHY\\[\\d+] Enclosure#/Slot#\\s*:\\s*(\\S+)").matcher(block);
            while (phy.find()) {
                members.add(phy.group(1));
            }
            String sizeMb = lineValue(block, "Size (in MB)");
            // 실기 2026-09-01: display 가 Volume Name 을 노출한다(사전 조사의 "이름 미노출" 전제 반증) — 이름 매칭의 재료
            volumes.add(new RaidExistingVolume(lineValue(block, "Volume ID"),
                    lineValue(block, "RAID level"), humanFromMb(sizeMb),
                    lineValue(block, "Status of volume"), lineValue(block, "Volume Name"), members,
                    lineValue(block, "Volume wwid")));
        }

        List<RaidPhysicalDisk> disks = new ArrayList<>();
        Matcher dev = Pattern.compile("Device is a Hard disk\\n(.*?)(?=\\nDevice is |\\n-{10,}|\\z)", Pattern.DOTALL)
                .matcher(display);
        while (dev.find()) {
            String block = dev.group(1);
            String slot = lineValue(block, "Enclosure #") + ":" + lineValue(block, "Slot #");
            String driveType = lineValue(block, "Drive Type");
            String type = driveType == null ? null
                    : driveType.toUpperCase(Locale.ROOT).endsWith("SSD") ? "SSD" : "HDD";
            String sizePair = lineValue(block, "Size (in MB)/(in sectors)");
            String size = sizePair == null ? null : humanFromMb(sizePair.split("/")[0].trim());
            String volumeRef = volumes.stream()
                    .filter(v -> v.memberSlots().contains(slot))
                    .map(RaidExistingVolume::id).findFirst().orElse(null);
            disks.add(new RaidPhysicalDisk(slot, type, lineValue(block, "Protocol"), size,
                    lineValue(block, "State"), trim(lineValue(block, "Model Number")),
                    trim(lineValue(block, "Serial No")), volumeRef));
        }
        return new RaidInventory(
                new DetectedRaidCard(RaidChipFamily.MPT_IR, subsystem, model, firmware), disks, volumes);
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    private String decode(JsonNode envelope, String field) {
        JsonNode node = envelope.path(field);
        if (node.isMissingNode() || node.isNull() || node.asString().isBlank()) {
            return null;
        }
        return new String(Base64.getDecoder().decode(node.asString().trim()), StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asString();
    }

    /** `Key    : Value` 형태 줄의 값 — 콜론 왼쪽 라벨은 접두 일치(원문 정렬 공백 흡수). */
    private String lineValue(String block, String label) {
        Matcher m = Pattern.compile("^\\s*" + Pattern.quote(label) + "\\s*:\\s*(.*)$", Pattern.MULTILINE)
                .matcher(block);
        return m.find() ? m.group(1).trim() : null;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** IR 의 MB 원값을 사람 단위로 — MegaRAID 표기(2진 환산 · GB/TB 라벨)와 통일해 화면 · 매칭 파싱이 한 형식을 본다(실기 2026-09-01 검수). */
    public static String humanFromMb(String mbText) {
        if (mbText == null || !mbText.matches("\\d+")) {
            return mbText == null ? null : mbText + " MB";
        }
        long mb = Long.parseLong(mbText);
        if (mb >= 1024L * 1024) {
            return String.format(java.util.Locale.ROOT, "%.3f TB", mb / 1048576.0);
        }
        if (mb >= 1024) {
            return String.format(java.util.Locale.ROOT, "%.3f GB", mb / 1024.0);
        }
        return mb + " MB";
    }
}
