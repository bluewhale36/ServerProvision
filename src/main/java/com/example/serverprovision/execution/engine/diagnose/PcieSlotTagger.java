package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.enums.PcieMount;
import com.example.serverprovision.execution.vo.HardwareSpec;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * lspci 장착물과 BMC 시스템 인벤토리({@code GET /api/system_inventory_gbt/pci_info})를 잇는 정적 진리표(HF15-6 D-4).
 * 열쇠는 PCI 버스 · 장치 번호다 — lspci 의 {@code ae:00.0} 은 버스 0xAE · 장치 0 · 기능 0 이고, BMC 는 같은 장치를
 * {@code hexBusNumber="0x00AE"} · {@code hexDeviceNum="0x0000"} 으로 준다(E0-3 · 2026-09-10 HAR). 같은 버스 · 장치의
 * 다중 기능(듀얼 포트 NIC)은 같은 태그를 받고, 짝이 없는 장치는 UNKNOWN 으로 남는다.
 */
public final class PcieSlotTagger {

    /** lspci 슬롯 표기 — 도메인이 있을 수도({@code 0000:ae:00.0}) 없을 수도({@code ae:00.0}) 있다. */
    private static final Pattern LSPCI_SLOT = Pattern.compile("^(?:[0-9a-fA-F]{4}:)?([0-9a-fA-F]{1,2}):([0-9a-fA-F]{1,2})\\.[0-9a-fA-F]$");
    private static final String ONBOARD_DESIGNATION = "Onboard";

    private PcieSlotTagger() {
    }

    /** 버스 · 장치 번호 한 쌍 — 기능 번호는 같은 카드의 포트라 구분하지 않는다. */
    record BusAddress(int bus, int device) {
    }

    /** BMC 인벤토리 항목 하나에서 뽑은 장착 사실. */
    record MountInfo(PcieMount mount, String slotDesignation) {
    }

    /**
     * BMC 가 인벤토리를 아직 채우지 못한 응답인가 — 배열이 아니거나 비어 있으면 미준비(재시도 대상)다. 장치가 있는
     * 서버에서 {@code []} 는 실측된 "최초 접속 뒤 조건 미충족" 증상이라 성공으로 읽지 않는다.
     */
    public static boolean looksReady(JsonNode pciInfo) {
        return pciInfo != null && pciInfo.isArray() && !pciInfo.isEmpty();
    }

    /** 장치 목록에 BMC 인벤토리의 장착 구분을 붙인 새 목록 — 순서 · 짝 없는 장치는 그대로. */
    public static List<HardwareSpec.PcieDevice> tag(List<HardwareSpec.PcieDevice> devices, JsonNode pciInfo) {
        Map<BusAddress, MountInfo> byAddress = index(pciInfo);
        List<HardwareSpec.PcieDevice> out = new ArrayList<>(devices.size());
        for (HardwareSpec.PcieDevice device : devices) {
            MountInfo info = device == null ? null
                    : parseLspciSlot(device.slot()).map(byAddress::get).orElse(null);
            out.add(info == null ? device : device.tagged(info.mount(), info.slotDesignation()));
        }
        return out;
    }

    static Optional<BusAddress> parseLspciSlot(String slot) {
        if (slot == null) {
            return Optional.empty();
        }
        Matcher m = LSPCI_SLOT.matcher(slot.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new BusAddress(Integer.parseInt(m.group(1), 16), Integer.parseInt(m.group(2), 16)));
    }

    private static Map<BusAddress, MountInfo> index(JsonNode pciInfo) {
        Map<BusAddress, MountInfo> byAddress = new HashMap<>();
        if (pciInfo == null || !pciInfo.isArray()) {
            return byAddress;
        }
        for (JsonNode entry : pciInfo) {
            Integer bus = hex(entry.path("hexBusNumber"));
            Integer device = hex(entry.path("hexDeviceNum"));
            if (bus == null || device == null) {
                continue;
            }
            String designation = entry.path("strSlotDesignation").asString(null);
            boolean onboard = entry.path("iOnBoard").asInt(0) == 1
                    || (designation != null && designation.trim().equalsIgnoreCase(ONBOARD_DESIGNATION));
            byAddress.put(new BusAddress(bus, device), onboard
                    ? new MountInfo(PcieMount.ONBOARD, null)
                    : new MountInfo(PcieMount.ADD_IN, blankToNull(designation)));
        }
        return byAddress;
    }

    private static Integer hex(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asString("").trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("0x")) {
            text = text.substring(2);
        }
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(text, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
