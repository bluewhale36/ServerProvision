package com.example.serverprovision.global.redfish;

import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UEFI 부팅 항목({@code Systems/Self/BootOptions?$expand=.})의 분류(HF20). 이 AMI BIOS 는 POST 때 {@code BootOrder} 첫 항목이
 * 속한 그룹을 Fixed Boot Order 1순위로 맞바꾸므로(실측 2026-09-17), 항목이 어느 그룹인지 아는 것이 순서 정착의 전제다.
 * 분류 근거는 UEFI 장치 경로와 표시명이다 — {@code HD(} 는 디스크, {@code MAC(} 는 NIC(PXE), {@code VenMedia(} 와 "EFI Shell" 은 내장 셸.
 */
public final class BootEntries {

    /** 항목의 그룹 — AMI Fixed Boot Order 의 UEFI Hard Disk · Network · AP(셸)에 대응한다. */
    public enum Kind { DISK, NETWORK, SHELL, OTHER }

    /**
     * @param id          {@code Boot0005} 처럼 BootOrder 가 가리키는 식별자(멤버의 Id 또는 @odata.id 의 마지막 조각)
     * @param displayName BMC 표시명(예 "Windows Boot Manager (LSI Logical Volume 3000, Partition 1)")
     * @param devicePath  UEFI 장치 경로(없으면 빈 문자열)
     */
    public record Entry(String id, String displayName, String devicePath, Kind kind) {
        /** Windows 가 만든 항목 — 디스크 중에서도 맨 앞에 둘 것. */
        public boolean isWindowsBootManager() {
            return displayName != null && displayName.toLowerCase(Locale.ROOT).startsWith("windows boot manager");
        }
    }

    private BootEntries() {
    }

    /** 확장된 BootOptions 컬렉션 전문에서 항목 목록을 뽑는다 — 알 수 없는 모양은 빈 목록(정착은 조용히 건너뛴다). */
    public static List<Entry> parse(JsonNode bootOptions) {
        List<Entry> entries = new ArrayList<>();
        if (bootOptions == null) {
            return entries;
        }
        for (JsonNode member : bootOptions.path("Members")) {
            String id = member.path("Id").asText("");
            if (id.isBlank()) {
                String odata = member.path("@odata.id").asText("");
                id = odata.substring(odata.lastIndexOf('/') + 1);
            }
            if (id.isBlank()) {
                continue;
            }
            String name = member.path("DisplayName").asText("");
            String path = member.path("UefiDevicePath").asText("");
            entries.add(new Entry(normalizeId(id), name, path, classify(name, path)));
        }
        return entries;
    }

    /** BootOrder 는 "Boot0005" 꼴이고 BootOptions 의 Id 는 "0005" 꼴 — 한 이름표로 맞춘다. */
    public static String normalizeId(String id) {
        String v = id.trim();
        return v.toLowerCase(Locale.ROOT).startsWith("boot") ? v : "Boot" + v;
    }

    static Kind classify(String displayName, String devicePath) {
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String path = devicePath == null ? "" : devicePath;
        // 셸은 펌웨어 볼륨(Fv/FvFile) 또는 벤더 미디어 경로 — 표시명 "EFI Shell" 이 AMI 에서 가장 안정적인 표지다.
        if (name.contains("efi shell") || path.startsWith("Fv(") || path.contains("FvFile(")
                || path.startsWith("VenMedia(") || path.startsWith("VenHw(")) {
            return Kind.SHELL;
        }
        if (path.contains("MAC(") || name.contains("pxe")) {
            return Kind.NETWORK;
        }
        // Windows 는 짧은 HD(…) 경로를, 그 밖의 디스크(USB 포함)는 PciRoot(…)/…/HD(…) 전체 경로를 남긴다 — 위치 무관하게 HD( 로 본다.
        if (path.contains("HD(") || name.startsWith("windows boot manager")) {
            return Kind.DISK;
        }
        return Kind.OTHER;
    }
}
