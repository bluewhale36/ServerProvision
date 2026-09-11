package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.engine.raid.RaidExistingVolume;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;

import java.util.List;
import java.util.function.Function;

/**
 * OS 설치 대상 디스크 번호 판정(E4-1-a-6 D-1 · HF15-5 개정) — 의존 0 인 정적 진리표. autounattend 는 디스크를 정수
 * 번호로만 지목하므로(WWN · 크기 지정 불가) 서버가 번호를 계산해 응답 파일에 박는다.
 *
 * <p>번호의 근거는 카드 계열의 순서 규칙({@link RaidChipFamily#windowsDiskOrder})으로 인벤토리를 재배열한 인덱스 하나다
 * (실기 3호 F-6 — sas3ircu 나열 순서는 Windows 열거 순서의 역순). 실기 4호 O-3 에서 Linux(mpt3sas)는 IR 볼륨을 id 오름차순,
 * Windows 는 내림차순으로 열거함이 확인돼 "lsblk 순서 = Windows 번호" 라는 옛 ① 근거는 폐기했다 — RAID 검증 재채집의
 * OS 가시 디스크는 화면 표시(F-9 배지)에만 쓴다. 순서 규칙으로도 못 찾으면 BLOCKED — 준비도가 서빙을 막는다.</p>
 *
 * <p>확증 기준 {@code expectedUniqueId} 는 계열별 변환({@link RaidChipFamily#windowsUniqueIdOf})의 결과라 설치 뒤
 * {@code Get-Disk} UniqueId 와 그대로 비교된다(F-7). {@code DEFERRED} 는 RAID 구성 단계가 아직 남아 볼륨 실물이 없을 때
 * 계획의 OS 영역으로 "구성 뒤 확정" 을 알리는 값이다(F-1) — 준비도를 막지 않는다.</p>
 */
public final class WindowsDiskSelection {

    public enum Confidence {
        /** 계산 성립 — {@code diskId} 유효. */
        CONFIDENT,
        /** RAID 구성 뒤 확정(F-1) — 실물 볼륨이 아직 없고 계획이 OS 영역을 갖고 있다. 준비도를 막지 않는다. */
        DEFERRED,
        /** 계산 불가 — 준비도 BLOCKED 로 나른다. {@code diskId} 는 -1. */
        BLOCKED
    }

    /** 번호의 근거 — 원장 meta · 로그 · 보고에 실려 어느 규칙이 번호를 냈는지 남는다(옛 "lsblk" 근거는 실기 4호 O-3 로 폐기). */
    public enum Basis {
        INVENTORY_ORDER("inventory-order", "카드 계열 순서 규칙");

        private final String wire;
        private final String label;

        Basis(String wire, String label) {
            this.wire = wire;
            this.label = label;
        }

        public String wire() {
            return wire;
        }

        /** 화면 문구 — 서빙 전(판정 결과)과 서빙 뒤(원장 meta)가 같은 문구를 쓴다. */
        public String label() {
            return "근거 " + label;
        }

        public static Basis fromWire(String wire) {
            for (Basis b : values()) {
                if (b.wire.equals(wire)) {
                    return b;
                }
            }
            return null;
        }
    }

    /**
     * 판정 결과. BLOCKED 면 {@code diskId}=-1 이고 {@code wire}(영문 코드) · {@code note}(한국어 사유)가 채워진다.
     * CONFIDENT 면 {@code diskId} · {@code expectedUniqueId}(사후 확증 기준 · 미노출이면 null) · {@code expectedBytes} ·
     * {@code basis} 가 채워진다. DEFERRED 는 {@code note} 만 든다.
     */
    public record DiskSelection(Confidence confidence, int diskId, String expectedUniqueId, long expectedBytes,
                                Basis basis, String wire, String note) {

        public boolean confident() {
            return confidence == Confidence.CONFIDENT;
        }

        public boolean blocked() {
            return confidence == Confidence.BLOCKED;
        }

        static DiskSelection blocked(String wire, String note) {
            return new DiskSelection(Confidence.BLOCKED, -1, null, 0L, null, wire, note);
        }

        public static DiskSelection deferred(String note) {
            return new DiskSelection(Confidence.DEFERRED, -1, null, 0L, null, null, note);
        }

        public static DiskSelection confident(int diskId, String expectedUniqueId, long expectedBytes, Basis basis) {
            return new DiskSelection(Confidence.CONFIDENT, diskId, expectedUniqueId, expectedBytes, basis, null, null);
        }
    }

    private WindowsDiskSelection() {
    }

    /**
     * @param allVolumes     이 게스트의 저장된 RAID 볼륨 전부(OS 영역 볼륨 선별 · passthrough 감지)
     * @param inventory      진단 · 검증이 관측한 RAID 인벤토리(카드 계열 · 볼륨 순서 · WWN) — null 이면 아직 진단 전
     */
    public static DiskSelection judge(List<RaidVolume> allVolumes, RaidInventory inventory) {
        RaidVolume osVolume = allVolumes.stream()
                .filter(v -> v.getVolumeRole() == PlannedVolumeRole.OS)
                .findFirst()
                .orElse(null);
        if (osVolume == null) {
            return DiskSelection.blocked("os volume missing",
                    "OS 영역 RAID 볼륨이 없습니다 — RAID 구성을 먼저 완료하세요");
        }
        if (inventory == null || inventory.volumes().isEmpty()) {
            return DiskSelection.blocked("raid inventory missing",
                    "RAID 인벤토리가 없습니다 — 진단이 먼저 필요합니다");
        }
        if (hasPassthrough(allVolumes)) {
            return DiskSelection.blocked("passthrough not supported",
                    "물리 디스크 직결 구성은 아직 지원하지 않습니다 — 실측 후 지원 예정");
        }
        RaidChipFamily family = inventory.card() == null ? null : inventory.card().chipFamily();
        String expected = uniqueIdOf(family, osVolume.getWwn());

        int byOrder = indexInInventoryOrder(osVolume, expected, family, inventory.volumes());
        if (byOrder >= 0) {
            return DiskSelection.confident(byOrder, expected, osVolume.getUsableBytes(), Basis.INVENTORY_ORDER);
        }
        return DiskSelection.blocked("os volume not in inventory",
                "OS 볼륨을 디스크 목록에서 찾지 못했습니다 — 재구성이 필요할 수 있습니다");
    }

    /** 계열 순서 규칙으로 재배열한 인벤토리에서 OS 볼륨을 식별자(우선) · 이름으로 찾은 인덱스. */
    private static int indexInInventoryOrder(RaidVolume osVolume, String expected, RaidChipFamily family,
                                             List<RaidExistingVolume> volumes) {
        List<RaidExistingVolume> ordered = family == null ? volumes
                : family.windowsDiskOrder(volumes, (Function<RaidExistingVolume, String>) RaidExistingVolume::id);
        String name = osVolume.getName() == null ? null : osVolume.getName().trim();
        for (int i = 0; i < ordered.size(); i++) {
            String observed = uniqueIdOf(family, ordered.get(i).wwn());
            if (expected != null && expected.equals(observed)) {
                return i;
            }
        }
        for (int i = 0; i < ordered.size(); i++) {
            String observedName = ordered.get(i).name() == null ? null : ordered.get(i).name().trim();
            if (name != null && !name.isEmpty() && name.equalsIgnoreCase(observedName)) {
                return i;
            }
        }
        return -1;
    }

    /** 카드 계열의 변환이 있으면 그것을, 계열을 모르면 hex 정규화만. */
    static String uniqueIdOf(RaidChipFamily family, String wwn) {
        return family == null ? RaidChipFamily.normalizeHex(wwn) : family.windowsUniqueIdOf(wwn);
    }

    /** 표시 층(상세 화면 F-9)의 걸러내기 — 가상 미디어(USB · 0B)는 컨트롤러 디스크가 아니므로 디스크 표에 세우지 않는다. */
    public static boolean isControllerDiskForDisplay(HardwareSpec.DiskInfo disk) {
        return isControllerDisk(disk);
    }

    /** RAID 컨트롤러에 달린 장치인가 — USB(가상 미디어 · 외장) · 크기 0 은 제외. */
    static boolean isControllerDisk(HardwareSpec.DiskInfo disk) {
        if (disk.transport() != null && disk.transport().equalsIgnoreCase("USB")) {
            return false;
        }
        return !isZeroSize(disk.size());
    }

    private static boolean isZeroSize(String size) {
        return size != null && size.trim().matches("0(\\.0+)?[KMGTPE]?i?B?");
    }

    /** RAID 레벨이 없는(단독 디스크 · passthrough) 볼륨이 섞였는가 — 그 순서는 미실측이라 계산을 막는다. */
    private static boolean hasPassthrough(List<RaidVolume> allVolumes) {
        return allVolumes.stream().anyMatch(v -> v.getRaidLevel() == null);
    }
}
