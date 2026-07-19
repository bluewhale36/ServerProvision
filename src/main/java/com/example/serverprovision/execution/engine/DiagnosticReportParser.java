package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.execution.vo.SoftwareSpec;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 진단 수집 보고(statusMeta JSON)의 <b>관용 파서</b>(E1-2, plan Q3) — agent.sh 와의 보고 계약을 서버측에서
 * 해석한다. 원칙: 파싱 가능한 필드만 구조화하고, 실패·누락은 null/생략으로 흡수한다(예외로 close 를
 * 실패시키지 않는다 — 원문은 setup_step.statusMeta 가 append-only 로 보존하므로 파서 보강 후 소급
 * 재파싱이 가능하다).
 *
 * <p><b>placeholder 필터</b>: 벤더 미기입 관행 값("To Be Filled By O.E.M." 류)을 null 로 정규화한다 —
 * boardSerial UNIQUE 가 placeholder 중복으로 깨지는 사고를 차단(V8 실측 후 패턴 보강).</p>
 *
 * <p><b>PCIe 종류 분류</b>: lspci 원문 라인에서 슬롯·클래스·모델을 뽑고 모델명 규칙으로
 * RAID/LAN/10G(UTP·SFP+)/FC(16·32Gb)/GPU 를 분류한다. 미분류는 ETC + 원문 유지(수집 유실 없음) —
 * 사내 사용 카드 실측(T3 체크리스트)으로 규칙을 보강한다.</p>
 */
@Component
public class DiagnosticReportParser {

    /** 벤더 미기입 placeholder 관행 값 — 소문자 비교. V8 실측 후 보강. */
    private static final Set<String> PLACEHOLDERS = Set.of(
            "to be filled by o.e.m.", "default string", "system serial number",
            "not specified", "unknown", "none", "n/a", "0123456789");

    private final ObjectMapper objectMapper;

    public DiagnosticReportParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 파싱 결과 묶음 — 전부 nullable(관용). {@code placeholderFiltered} 는 관찰용(원장 기록에 실림). */
    public record Parsed(
            String boardSerial,
            HardwareSpec hardwareSpec,
            SoftwareSpec softwareSpec,
            IpAddressVO bmcIp,
            MacAddressVO bmcMac,
            List<String> placeholderFiltered
    ) {
    }

    /**
     * @throws ReportUnparsableException statusMeta 가 JSON 자체가 아닐 때만 — 호출자는 이를 잡아
     *         "적재 없이 close 성공" 으로 처리한다(§7 비예외 원칙의 경계 표식).
     */
    public Parsed parse(String statusMeta) {
        JsonNode root;
        try {
            root = objectMapper.readTree(statusMeta);
        } catch (RuntimeException e) {
            throw new ReportUnparsableException(e);
        }
        if (root == null || !root.isObject()) {
            throw new ReportUnparsableException(null);
        }

        List<String> filtered = new ArrayList<>();
        String boardSerial = filteredText(root, "boardSerial", filtered);
        String biosVersion = filteredText(root, "biosVersion", filtered);

        HardwareSpec hardware = new HardwareSpec(
                parseCpu(root.path("cpu"), filtered),
                parseMemory(root.path("memoryModules"), filtered),
                parseDisks(root.path("disks")),
                parsePcie(root.path("pcieRaw")));

        IpAddressVO bmcIp = null;
        MacAddressVO bmcMac = null;
        JsonNode bmc = root.path("bmc");
        if (bmc.isObject()) {
            bmcIp = tolerant(() -> IpAddressVO.of(text(bmc, "ip")));
            bmcMac = tolerant(() -> MacAddressVO.of(text(bmc, "mac")));
        }

        return new Parsed(boardSerial, hardware,
                new SoftwareSpec(biosVersion, null), bmcIp, bmcMac, List.copyOf(filtered));
    }

    /** statusMeta 가 JSON 이 아니어서 어떤 필드도 해석할 수 없음 — 승급 없이 원장만 남기는 경로의 신호. */
    public static class ReportUnparsableException extends RuntimeException {
        ReportUnparsableException(Throwable cause) {
            super("진단 수집 보고를 JSON 으로 해석할 수 없습니다 — 원문은 원장(statusMeta)에 보존됨", cause);
        }
    }

    // ─────────────────────────── 필드별 관용 파싱 ───────────────────────────

    private HardwareSpec.CpuInfo parseCpu(JsonNode cpu, List<String> filtered) {
        if (!cpu.isObject()) {
            return null;
        }
        String manufacturer = filteredText(cpu, "manufacturer", filtered);
        String model = filteredText(cpu, "model", filtered);
        return (manufacturer == null && model == null) ? null
                : new HardwareSpec.CpuInfo(manufacturer, model);
    }

    private List<HardwareSpec.MemoryModule> parseMemory(JsonNode arr, List<String> filtered) {
        List<HardwareSpec.MemoryModule> modules = new ArrayList<>();
        if (arr.isArray()) {
            arr.forEach(m -> {
                String slot = text(m, "slot");
                String size = text(m, "size");
                if (slot != null || size != null) {
                    modules.add(new HardwareSpec.MemoryModule(
                            slot, filteredText(m, "manufacturer", filtered), size));
                }
            });
        }
        return List.copyOf(modules);
    }

    private List<HardwareSpec.DiskInfo> parseDisks(JsonNode arr) {
        List<HardwareSpec.DiskInfo> disks = new ArrayList<>();
        if (arr.isArray()) {
            arr.forEach(d -> {
                String device = text(d, "device");
                if (device == null) {
                    return;
                }
                // lsblk ROTA: 1=회전(HDD) / 0=비회전(SSD·NVMe)
                String type = "1".equals(text(d, "rota")) ? "HDD" : "SSD";
                String tran = text(d, "tran");
                String transport = tran == null ? null : tran.toUpperCase(Locale.ROOT);
                disks.add(new HardwareSpec.DiskInfo(device, type, transport, text(d, "size")));
            });
        }
        return List.copyOf(disks);
    }

    private List<HardwareSpec.PcieDevice> parsePcie(JsonNode arr) {
        List<HardwareSpec.PcieDevice> devices = new ArrayList<>();
        if (arr.isArray()) {
            arr.forEach(line -> {
                HardwareSpec.PcieDevice device = classifyPcie(line.isValueNode() ? line.asString() : null);
                if (device != null) {
                    devices.add(device);
                }
            });
        }
        return List.copyOf(devices);
    }

    /** lspci 원문 1행: {@code "01:00.0 RAID bus controller: Broadcom / LSI MegaRAID ... (rev 02)"} */
    private HardwareSpec.PcieDevice classifyPcie(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int firstSpace = raw.indexOf(' ');
        int classSep = raw.indexOf(": ", firstSpace);
        if (firstSpace < 0 || classSep < 0) {
            return new HardwareSpec.PcieDevice(null, "ETC", null, raw.trim());
        }
        String slot = raw.substring(0, firstSpace).trim();
        String className = raw.substring(firstSpace + 1, classSep).trim();
        String descriptor = raw.substring(classSep + 2).trim();
        return new HardwareSpec.PcieDevice(slot, kindOf(className, descriptor),
                vendorOf(descriptor), descriptor);
    }

    private String kindOf(String className, String descriptor) {
        String cls = className.toLowerCase(Locale.ROOT);
        String desc = descriptor.toLowerCase(Locale.ROOT);
        if (cls.contains("raid") || desc.contains("megaraid")) {
            return "RAID";
        }
        if (cls.contains("fibre channel")) {
            if (desc.contains("32gb") || desc.contains("2772") || desc.contains("lpe32")) {
                return "FC_32G";
            }
            if (desc.contains("16gb") || desc.contains("269") || desc.contains("lpe31")) {
                return "FC_16G";
            }
            return "ETC";
        }
        if (cls.contains("ethernet") || cls.contains("network")) {
            if (desc.contains("sfp") || desc.contains("x710") || desc.contains("82599")
                    || desc.contains("xxv710") || desc.contains("connectx")) {
                return "LAN_10G_SFP";
            }
            if (desc.contains("10g") || desc.contains("x550") || desc.contains("x540")) {
                return "LAN_10G_UTP";
            }
            return "LAN";
        }
        if ((cls.contains("vga") || cls.contains("3d controller") || cls.contains("display"))
                && desc.contains("nvidia")) {
            return "GPU";
        }
        return "ETC";
    }

    /** descriptor 선두의 알려진 제조사명 매칭 — 미매칭은 첫 토큰(관용). */
    private static final List<String> KNOWN_VENDORS = List.of(
            "Broadcom / LSI", "Broadcom", "LSI", "Intel", "NVIDIA", "QLogic", "Emulex",
            "Mellanox", "Marvell", "Realtek", "Samsung", "Micron", "AMD", "ASPEED");

    private String vendorOf(String descriptor) {
        for (String vendor : KNOWN_VENDORS) {
            if (descriptor.regionMatches(true, 0, vendor, 0, vendor.length())) {
                return vendor;
            }
        }
        int space = descriptor.indexOf(' ');
        return space > 0 ? descriptor.substring(0, space) : descriptor;
    }

    // ─────────────────────────── 공용 헬퍼 ───────────────────────────

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isValueNode()) {
            return null;
        }
        String s = v.asString();   // Jackson 3 rename: asText → asString (값 노드 관용 강제 변환)
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** placeholder 필터 통과 텍스트 — 걸러진 필드명은 관찰 목록에 적립. */
    private String filteredText(JsonNode node, String field, List<String> filtered) {
        String value = text(node, field);
        if (value != null && PLACEHOLDERS.contains(value.toLowerCase(Locale.ROOT))) {
            filtered.add(field + "=" + value);
            return null;
        }
        return value;
    }

    private <T> T tolerant(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
