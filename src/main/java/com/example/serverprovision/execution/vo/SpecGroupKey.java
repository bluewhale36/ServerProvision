package com.example.serverprovision.execution.vo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 스펙 그룹의 동치 판정 키 (U3-3 DEC-D).
 *
 * <p>같은 하드웨어 구성의 서버를 한 묶음으로 보기 위한 정규형이다. 비교 기준은 여섯 가지 —
 * 보드 모델 · CPU 모델 · CPU 개수 · 메모리 용량과 개수 · PCIe 카드 · 디스크 개수와 용량.</p>
 *
 * <p><b>슬롯 위치와 나열 순서는 키에 넣지 않는다.</b> 같은 부품을 다른 슬롯에 꽂았다는 이유로
 * 다른 그룹이 되면 "같은 스펙끼리 묶는다" 는 목적이 깨지기 때문이다. 그래서 각 부품 목록을
 * <b>다중집합</b>으로 보고 정렬한 뒤 결합한다.</p>
 *
 * <p>키를 문자열로 물질화하지 않고 값 객체로 두는 이유는, 이 판정이 화면 · 후속 U3-4 의 일괄 할당에서
 * 함께 쓰이므로 비교 규칙이 한 곳에만 있어야 하기 때문이다.</p>
 */
public record SpecGroupKey(String value) implements Comparable<SpecGroupKey> {

    private static final String FIELD_SEP = " | ";
    private static final String ITEM_SEP = ",";
    private static final String UNKNOWN = "?";

    /**
     * 보드 모델명과 하드웨어 인벤토리로 키를 만든다.
     * 스펙이 없는 서버(수집 전)는 이 메서드를 부르지 않는다 — 그룹 자격 판정이 먼저다
     * ({@code DiscoveryStage.isSpecAvailable()}).
     */
    public static SpecGroupKey of(String boardModelName, HardwareSpec spec) {
        List<String> fields = new ArrayList<>();
        fields.add(nullSafe(boardModelName));
        fields.add(cpuField(spec));
        fields.add(memoryField(spec));
        fields.add(pcieField(spec));
        fields.add(diskField(spec));
        return new SpecGroupKey(String.join(FIELD_SEP, fields));
    }

    /** CPU — 모델별 소켓 수까지 담는다. 같은 모델이라도 1소켓과 2소켓은 다른 그룹이다(DEC-C 의 목적). */
    private static String cpuField(HardwareSpec spec) {
        if (spec == null || spec.cpuSockets() == null || spec.cpuSockets().isEmpty()) {
            return UNKNOWN;
        }
        Map<String, Integer> countByModel = new TreeMap<>();
        spec.cpuSockets().forEach(c ->
                countByModel.merge(nullSafe(c == null ? null : c.model()), 1, Integer::sum));
        return joinCounted(countByModel);
    }

    /** 메모리 — 용량별 개수. 슬롯 이름은 넣지 않는다(같은 구성을 다른 슬롯에 꽂아도 같은 그룹). */
    private static String memoryField(HardwareSpec spec) {
        if (spec == null || spec.memoryModules() == null || spec.memoryModules().isEmpty()) {
            return UNKNOWN;
        }
        Map<String, Integer> countBySize = new TreeMap<>();
        spec.memoryModules().forEach(m ->
                countBySize.merge(nullSafe(m == null ? null : m.size()), 1, Integer::sum));
        return joinCounted(countBySize);
    }

    /** PCIe — 분류와 모델명의 다중집합. */
    private static String pcieField(HardwareSpec spec) {
        if (spec == null || spec.pcieDevices() == null || spec.pcieDevices().isEmpty()) {
            return UNKNOWN;
        }
        List<String> items = spec.pcieDevices().stream()
                .map(p -> p == null ? UNKNOWN : nullSafe(p.kind()) + "/" + nullSafe(p.model()))
                .sorted(Comparator.naturalOrder())
                .toList();
        return String.join(ITEM_SEP, items);
    }

    /** 디스크 — 종류 · 전송 방식 · 용량의 다중집합. 장치명(device)은 부팅 순서에 따라 흔들리므로 제외한다. */
    private static String diskField(HardwareSpec spec) {
        if (spec == null || spec.disks() == null || spec.disks().isEmpty()) {
            return UNKNOWN;
        }
        List<String> items = spec.disks().stream()
                .map(d -> d == null ? UNKNOWN
                        : nullSafe(d.type()) + "/" + nullSafe(d.transport()) + "/" + nullSafe(d.size()))
                .sorted(Comparator.naturalOrder())
                .toList();
        return String.join(ITEM_SEP, items);
    }

    private static String joinCounted(Map<String, Integer> counted) {
        List<String> items = counted.entrySet().stream()
                .map(e -> e.getKey() + "×" + e.getValue())
                .toList();
        return String.join(ITEM_SEP, items);
    }

    private static String nullSafe(String s) {
        return (s == null || s.isBlank()) ? UNKNOWN : s.trim();
    }

    @Override
    public int compareTo(SpecGroupKey o) {
        return value.compareTo(o.value);
    }
}
