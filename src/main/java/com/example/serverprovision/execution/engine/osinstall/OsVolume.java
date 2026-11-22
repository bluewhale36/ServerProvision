package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;

import java.util.List;
import java.util.Optional;

/**
 * OS 를 설치할 RAID 볼륨을 찾은 결과(E4-1-b-4-2, Windows 판정의 공통부를 옮김). RAID 구성 단계가 남아 실물 볼륨이 없으면
 * 계획을 근거로 "구성 뒤 확정" 이거나 미리 막고, 실물이 있으면 볼륨과 진단 인벤토리를 그대로 넘긴다. 실물에서 디스크를 고르는
 * 규칙은 계열마다 다르다(Windows = 디스크 번호, RHEL = by-id 이름).
 */
public sealed interface OsVolume {

    /**
     * 카드 CLI 가 보인 볼륨 WWN 을 OS 가 보는 WWN(소문자 hex, 접두 없음)으로 옮긴다. 카드 계열을 모르면 hex 정규화만 한다.
     * 형식을 모르면 null.
     */
    static String osWwnOf(RaidChipFamily family, String cardWwn) {
        return family == null ? RaidChipFamily.normalizeHex(cardWwn) : family.volumeWwnOf(cardWwn);
    }

    /** RAID 구성 뒤에 정해진다 — 준비도를 막지 않는다. */
    record Deferred(String note) implements OsVolume {
    }

    /** 설치할 디스크를 정할 수 없다 — 준비도 BLOCKED 로 나른다. */
    record Blocked(String wire, String note) implements OsVolume {
    }

    /**
     * RAID 구성이 남긴 볼륨 전부와 진단 인벤토리.
     *
     * @param inventory 진단 · 검증이 관측한 RAID 인벤토리 — null 이면 아직 진단 전
     */
    record Materialized(List<RaidVolume> volumes, RaidInventory inventory) implements OsVolume {

        public Materialized {
            volumes = List.copyOf(volumes);
        }

        public Optional<RaidVolume> osVolume() {
            return volumes.stream().filter(v -> v.getVolumeRole() == PlannedVolumeRole.OS).findFirst();
        }

        /** OS 영역 볼륨의 OS 쪽 WWN — 볼륨이 없거나 WWN 을 모르면 null. */
        public String osVolumeWwn() {
            return osVolume().map(v -> osWwnOf(chipFamily(), v.getWwn())).orElse(null);
        }

        /** 카드 계열 — 인벤토리가 없거나 카드를 모르면 null. */
        public RaidChipFamily chipFamily() {
            return inventory == null || inventory.card() == null ? null : inventory.card().chipFamily();
        }

        /**
         * 어느 계열이든 디스크를 고를 수 없는 상태 — OS 영역 볼륨이 없거나, 인벤토리가 없거나, 물리 디스크 직결(RAID 레벨 없는
         * 볼륨)이 섞였다. 직결 디스크의 순서와 식별자는 실측하지 않아 고르지 않는다.
         */
        public Optional<Blocked> unusable() {
            if (osVolume().isEmpty()) {
                return Optional.of(new Blocked("os volume missing",
                        "OS 를 설치할 RAID 볼륨이 없습니다. RAID 구성을 먼저 마쳐야 합니다"));
            }
            if (inventory == null || inventory.volumes().isEmpty()) {
                return Optional.of(new Blocked("raid inventory missing",
                        "RAID 인벤토리가 없습니다: 진단이 먼저 필요합니다"));
            }
            if (volumes.stream().anyMatch(v -> v.getRaidLevel() == null)) {
                return Optional.of(new Blocked("passthrough not supported",
                        "물리 디스크 직결 구성은 지원하지 않습니다"));
            }
            return Optional.empty();
        }
    }
}
