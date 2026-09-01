package com.example.serverprovision.execution.engine.raid;

import java.util.List;

/**
 * RAID 인벤토리 한 벌(E3.5-1) — {@code guest_server_detail.raid_inventory_json} 의 앱측 구조이자
 * 상세 화면 · 계획 산출(E3.5-2)의 조회 모델. 직렬화는 Jackson 3({@code tools.jackson.*}) 관용 원칙
 * (모르는 필드 무시 · 누락 null) — 원문은 {@code RAID_INVENTORY_COLLECTING} 원장 statusMeta 가 보존한다.
 */
public record RaidInventory(
        DetectedRaidCard card,
        List<RaidPhysicalDisk> disks,
        List<RaidExistingVolume> volumes
) {

    public RaidInventory {
        disks = disks == null ? List.of() : List.copyOf(disks);
        volumes = volumes == null ? List.of() : List.copyOf(volumes);
    }
}
