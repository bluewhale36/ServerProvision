package com.example.serverprovision.global.redfish;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * BootOrder 정착 정책(HF20) — 실측(2026-09-17)으로 확정된 AMI 규칙 "POST 때 BootOrder 첫 항목의 그룹이 Fixed Boot Order 1순위로
 * 맞바뀐다" 위에서, 첫 항목을 우리가 원하는 그룹으로 두고 BIOS 의 맞바꿈이 따라오게 한다. 상수가 규칙을 든다 — 소비처에 분기가 없다.
 *
 * <ul>
 *   <li>{@link #SHELL_LAST} — Once 무장 직전. 셸 항목을 맨 뒤로 보내 어떤 POST 에서도 셸이 첫 항목이 되지 않게 한다(AP 1순위 차단).</li>
 *   <li>{@link #DISK_FIRST} — 종단 때. 디스크 항목(Windows Boot Manager 우선)을 맨 앞으로 두어 다음 POST 에 Hard Disk 가 1순위로 돌아오게 한다.</li>
 * </ul>
 * 결과가 현재 순서와 같으면 empty — PATCH 를 내지 않는다(idempotent).
 */
public enum BootOrderPolicy {

    SHELL_LAST("셸을 맨 뒤로") {
        @Override
        List<String> arrange(List<String> current, Map<String, BootEntries.Entry> byId) {
            List<String> head = new ArrayList<>();
            List<String> shell = new ArrayList<>();
            for (String id : current) {
                (kindOf(id, byId) == BootEntries.Kind.SHELL ? shell : head).add(id);
            }
            head.addAll(shell);
            return head;
        }
    },

    DISK_FIRST("디스크를 맨 앞으로") {
        @Override
        List<String> arrange(List<String> current, Map<String, BootEntries.Entry> byId) {
            List<String> windows = new ArrayList<>();
            List<String> disks = new ArrayList<>();
            List<String> rest = new ArrayList<>();
            List<String> shell = new ArrayList<>();
            for (String id : current) {
                BootEntries.Entry e = byId.get(id);
                BootEntries.Kind kind = kindOf(id, byId);
                if (kind == BootEntries.Kind.SHELL) {
                    shell.add(id);
                } else if (kind == BootEntries.Kind.DISK) {
                    (e != null && e.isWindowsBootManager() ? windows : disks).add(id);
                } else {
                    rest.add(id);
                }
            }
            List<String> out = new ArrayList<>(windows);
            out.addAll(disks);
            out.addAll(rest);
            out.addAll(shell);
            return out;
        }
    };

    private final String label;

    BootOrderPolicy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 현재 순서와 항목 목록으로 새 순서를 내되, 같으면 empty. 항목 목록에 없는 id 는 OTHER 로 두고 자리를 지킨다. */
    public Optional<List<String>> reorder(List<String> currentOrder, List<BootEntries.Entry> entries) {
        if (currentOrder == null || currentOrder.isEmpty()) {
            return Optional.empty();
        }
        Map<String, BootEntries.Entry> byId = new LinkedHashMap<>();
        for (BootEntries.Entry e : entries) {
            byId.put(e.id(), e);
        }
        List<String> normalized = currentOrder.stream().map(BootEntries::normalizeId).toList();
        List<String> arranged = arrange(normalized, byId);
        return arranged.equals(normalized) ? Optional.empty() : Optional.of(arranged);
    }

    abstract List<String> arrange(List<String> current, Map<String, BootEntries.Entry> byId);

    private static BootEntries.Kind kindOf(String id, Map<String, BootEntries.Entry> byId) {
        BootEntries.Entry e = byId.get(id);
        return e == null ? BootEntries.Kind.OTHER : e.kind();
    }
}
