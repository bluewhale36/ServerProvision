package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;
import com.example.serverprovision.provisioning.setting.exception.InvalidPartitionException;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 리눅스 파티션 구성 규칙 — RHEL/Debian 계열 검사기가 공유하는 값 검증(의존성 0 static).
 *
 * <p>frontend {@code FS_CONSTRAINT}(setting-form.js) 와 같은 표가 SSOT(사용자 확정 2026-07-05):
 * 고정 마운트포인트는 허용 집합이 정해져 있고(/boot/efi = EFI·FAT32, swap = SWAP),
 * 예약 파일시스템(EFI/SWAP/FAT32)은 다른 마운트포인트에 쓸 수 없으며, NTFS 는 리눅스 설치
 * 파티션에서 전면 차단(설치기 Kickstart/preseed 가 NTFS 포맷 미지원 — 운영 중 마운트와는 별개).
 * Windows 계열은 파티션 모델이 달라 이 규칙을 공유하지 않는다(계열 검사기가 선택 호출).</p>
 */
public final class LinuxPartitionRules {

    private static final Map<String, Set<FileSystem>> FIXED_MOUNT_FS = Map.of(
            "/boot/efi", Set.of(FileSystem.EFI, FileSystem.FAT32),
            "swap", Set.of(FileSystem.SWAP));

    /** 일반 마운트포인트에서 금지 — 고정 마운트 전용(EFI/SWAP/FAT32) + 리눅스 설치 불가(NTFS). */
    private static final Set<FileSystem> RESERVED_FS =
            Set.of(FileSystem.EFI, FileSystem.SWAP, FileSystem.FAT32, FileSystem.NTFS);

    /** 리눅스 설치에 반드시 존재해야 하는 마운트포인트 (legacy MISSING_MANDATORY_MOUNT_POINTS 이관). */
    private static final Set<String> MANDATORY_MOUNT_POINTS = Set.of("/", "/boot", "/boot/efi", "swap");

    private LinuxPartitionRules() {
    }

    public static void validate(List<PartitionRequest> partitions) {
        validateMandatoryMounts(partitions);
        for (PartitionRequest partition : partitions) {
            Set<FileSystem> allowed = FIXED_MOUNT_FS.get(partition.getMountPoint());
            if (allowed != null && !allowed.contains(partition.getFileSystem())) {
                throw InvalidPartitionException.fixedFileSystem(partition.getMountPoint(), allowed);
            }
            if (allowed == null && RESERVED_FS.contains(partition.getFileSystem())) {
                throw InvalidPartitionException.reservedFileSystem(
                        partition.getMountPoint(), partition.getFileSystem());
            }
            // grow 가 아니면 크기 1 이상 필수 (legacy INVALID_PARTITION_SIZE 이관).
            if (!partition.isGrow() && partition.getSize() < 1) {
                throw InvalidPartitionException.invalidSize(partition.getMountPoint());
            }
        }
        validateGrowPerDisk(partitions);
    }

    private static void validateMandatoryMounts(List<PartitionRequest> partitions) {
        Set<String> mounts = partitions.stream()
                .map(PartitionRequest::getMountPoint).collect(Collectors.toSet());
        Set<String> missing = new LinkedHashSet<>(MANDATORY_MOUNT_POINTS);
        missing.removeAll(mounts);
        if (!missing.isEmpty()) {
            throw InvalidPartitionException.missingMandatoryMounts(missing);
        }
    }

    /** 같은 디스크(diskName null/공백 = 자동 할당 그룹)에 grow 는 1개만 (legacy MULTIPLE_GROW 이관). */
    private static void validateGrowPerDisk(List<PartitionRequest> partitions) {
        Map<String, Integer> growPerDisk = new HashMap<>();
        for (PartitionRequest partition : partitions) {
            if (!partition.isGrow()) {
                continue;
            }
            String disk = partition.getDiskName() == null || partition.getDiskName().isBlank()
                    ? "" : partition.getDiskName();
            int count = growPerDisk.merge(disk, 1, Integer::sum);
            if (count > 1) {
                throw InvalidPartitionException.multipleGrowOnDisk(
                        disk.isEmpty() ? "(자동 할당)" : disk);
            }
        }
    }
}
