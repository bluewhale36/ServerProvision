package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.raid.RaidExistingVolume;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidPhysicalDisk;
import com.example.serverprovision.execution.engine.windows.WindowsDiskSelection;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 게스트 상세의 "OS 가시 디스크(lsblk)" 행 조립(2026-09-17 · HF15-5 F-9 확장). RAID 카드 뒤에서 lsblk 가 보는 것은
 * 볼륨이라 {@code tran} 은 카드가 OS 에 내미는 전송(IR · MegaRAID 모두 SAS)이지 디스크의 실제 전송이 아니다 — 실기
 * (2026-09-17 · CRA3338 · 9361-8i)에서 SATA SSD 가 "SAS" 로 보였다. 볼륨과 짝이 맞으면 <b>멤버 물리 디스크</b>의
 * 종류 · 전송(CLI 보고)을 쓰고, 짝이 없어도 카드 뒤면 카드 전체가 한 전송이면 그것을, 아니면 비운다.
 */
final class OsVisibleDiskRows {

    private OsVisibleDiskRows() {
    }

    static List<GuestServerDetailResponse.OsVisibleDisk> of(HardwareSpec spec, RaidInventory raidInventory) {
        if (spec == null || spec.disks() == null) {
            return List.of();
        }
        boolean behindRaidCard = raidInventory != null && raidInventory.card() != null;
        RaidChipFamily family = behindRaidCard ? raidInventory.card().chipFamily() : null;
        Map<String, VolumeView> volumeByUniqueId = new HashMap<>();
        if (raidInventory != null) {
            for (RaidExistingVolume v : raidInventory.volumes()) {
                String key = family == null ? RaidChipFamily.normalizeHex(v.wwn()) : family.windowsUniqueIdOf(v.wwn());
                if (key != null) {
                    volumeByUniqueId.put(key, VolumeView.of(v, raidInventory.disks()));
                }
            }
        }
        String cardWideTransport = behindRaidCard ? uniform(raidInventory.disks().stream().map(RaidPhysicalDisk::transport).toList()) : null;

        List<GuestServerDetailResponse.OsVisibleDisk> rows = new ArrayList<>();
        for (HardwareSpec.DiskInfo d : spec.disks()) {
            if (!WindowsDiskSelection.isControllerDiskForDisplay(d)) {
                continue;
            }
            String uniqueId = RaidChipFamily.normalizeHex(d.wwn());
            VolumeView volume = uniqueId == null ? null : volumeByUniqueId.get(uniqueId);
            if (volume != null) {
                rows.add(new GuestServerDetailResponse.OsVisibleDisk(d.device(), volume.type(), volume.transport(), d.size(), volume.name()));
            } else if (behindRaidCard) {
                // 볼륨과 짝이 없는 카드 뒤 장치 — lsblk 의 tran 은 카드 것이라 버리고, 카드 전체가 한 전송이면 그것만 말한다.
                rows.add(new GuestServerDetailResponse.OsVisibleDisk(d.device(), null, cardWideTransport, d.size(), null));
            } else {
                rows.add(new GuestServerDetailResponse.OsVisibleDisk(d.device(), d.type(), d.transport(), d.size(), null));
            }
        }
        return List.copyOf(rows);
    }

    /** 볼륨 이름 + 멤버 물리 디스크에서 모은 종류 · 전송(서로 다르면 "SSD/HDD" 처럼 병기). */
    private record VolumeView(String name, String type, String transport) {
        static VolumeView of(RaidExistingVolume v, List<RaidPhysicalDisk> disks) {
            String name = v.name() == null || v.name().isBlank() ? v.id() : v.name().trim();
            List<RaidPhysicalDisk> members = disks.stream()
                    .filter(d -> d.volumeRef() != null && d.volumeRef().equals(v.id()))
                    .toList();
            return new VolumeView(name, joined(members.stream().map(RaidPhysicalDisk::type).toList()),
                    joined(members.stream().map(RaidPhysicalDisk::transport).toList()));
        }
    }

    private static String joined(List<String> values) {
        Set<String> distinct = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return distinct.isEmpty() ? null : String.join("/", distinct);
    }

    private static String uniform(List<String> values) {
        String j = joined(values);
        return j == null || j.contains("/") ? null : j;
    }
}
