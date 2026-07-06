package com.example.serverprovision.provisioning.setting.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;
import com.example.serverprovision.provisioning.setting.enums.FileSystem;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 리눅스 파티션 구성 규칙 위반 (400, field-bound=partitions).
 *
 * <p>UI 는 마운트포인트별 파일시스템을 강제(FS_CONSTRAINT — 고정 행은 select disabled)하므로
 * 정상 흐름에서 위반이 생기지 않는다 — direct POST 안전망. 규칙 SSOT 는
 * {@code LinuxPartitionRules}(frontend FS_CONSTRAINT 와 동일 표).</p>
 */
public class InvalidPartitionException extends FieldBoundBadRequestException {

    private InvalidPartitionException(String message) {
        super(message, "partitions");
    }

    /** 고정 마운트포인트(/boot/efi·swap)의 파일시스템이 허용 집합 밖. */
    public static InvalidPartitionException fixedFileSystem(String mountPoint, Set<FileSystem> allowed) {
        String allowedNames = allowed.stream().map(Enum::name).sorted().collect(Collectors.joining(", "));
        return new InvalidPartitionException(
                mountPoint + " 파티션의 파일시스템은 " + allowedNames + " 만 허용됩니다.");
    }

    /** 예약(EFI/SWAP/FAT32)·차단(NTFS) 파일시스템을 일반 마운트포인트에 사용. */
    public static InvalidPartitionException reservedFileSystem(String mountPoint, FileSystem fileSystem) {
        return new InvalidPartitionException(
                fileSystem.name() + " 파일시스템은 " + mountPoint + " 파티션에 사용할 수 없습니다.");
    }

    /** 필수 마운트포인트(/, /boot, /boot/efi, swap) 일부 누락. */
    public static InvalidPartitionException missingMandatoryMounts(Set<String> missing) {
        return new InvalidPartitionException(
                "필수 마운트포인트가 누락되었습니다: " + String.join(", ", missing));
    }

    /** grow 가 아닌 파티션의 크기가 1 미만. */
    public static InvalidPartitionException invalidSize(String mountPoint) {
        return new InvalidPartitionException(
                mountPoint + " 파티션은 grow 가 아니므로 1 이상의 크기가 필요합니다.");
    }

    /** 같은 디스크에 grow 파티션 2개 이상. */
    public static InvalidPartitionException multipleGrowOnDisk(String diskLabel) {
        return new InvalidPartitionException(
                "디스크 " + diskLabel + " 에 grow 파티션은 1개만 지정할 수 있습니다.");
    }
}
