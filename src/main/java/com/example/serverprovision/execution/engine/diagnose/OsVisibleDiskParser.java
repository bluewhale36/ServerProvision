package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 에이전트 {@code lsblk} 보고({@code disks} 배열)의 해석 — 진단 수집(E1-2)과 RAID 검증 재채집(HF15-5)이 같은 계약을
 * 쓴다. 계약: {@code {"device":"sda","size":"446.6G","rota":"1","tran":"sas","wwn":"0x600605b0..."}}. 누락 필드는 null 로
 * 관용한다(구 에이전트는 wwn 을 보내지 않는다).
 */
public final class OsVisibleDiskParser {

    private OsVisibleDiskParser() {
    }

    public static List<HardwareSpec.DiskInfo> parse(JsonNode arr) {
        List<HardwareSpec.DiskInfo> disks = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            arr.forEach(d -> {
                String device = text(d, "device");
                if (device == null) {
                    return;
                }
                // lsblk ROTA: 1=회전(HDD) / 0=비회전(SSD·NVMe). 컨트롤러 뒤 볼륨은 이 값이 실물을 말하지 않는다 —
                // 그 판정은 표시 층(RAID 볼륨 대조)이 한다.
                String type = "1".equals(text(d, "rota")) ? "HDD" : "SSD";
                String tran = text(d, "tran");
                String transport = tran == null ? null : tran.toUpperCase(Locale.ROOT);
                disks.add(new HardwareSpec.DiskInfo(device, type, transport, text(d, "size"),
                        RaidChipFamily.normalizeHex(text(d, "wwn"))));
            });
        }
        return List.copyOf(disks);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asString(null);
        return s == null || s.isBlank() ? null : s.trim();
    }
}
