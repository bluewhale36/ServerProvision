package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.enums.SizeUnit;
import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.DomainValidationException.Reason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public abstract class LinuxInstallation extends OSInstallation {

    /**
     * 모든 Linux 계열에 공통으로 요구되는 마운트포인트.
     *
     * <p>{@code /boot/efi} 는 UEFI 부팅을 전제로 하는 배포판(RHEL 8+, Rocky 9+, Ubuntu UEFI)에만
     * 필수이므로 이 공통 목록에서는 제외되었다. UEFI 필수 배포판은
     * {@link RHELBasedInstallation#requireBootEfi()} 훅을 통해 자체적으로 추가 검증한다.</p>
     */
    private static final List<String> MANDATORY_MOUNT_POINTS = List.of("/", "/boot", "swap");

    /**
     * OS 설치 폼에서 "기본 파티션 자동 생성" 시 사용하는 권장 파티션 프리셋.
     *
     * <ul>
     *   <li>{@code /}         — EXT4, 크기 미지정(사용자 직접 입력 또는 grow)</li>
     *   <li>{@code /boot}     — EXT4, 1 GiB</li>
     *   <li>{@code /boot/efi} — EFI,  1 GiB</li>
     *   <li>{@code swap}      — SWAP, 8 GiB</li>
     * </ul>
     *
     * @param osName 대상 OS (현재 Linux 계열은 모두 동일한 프리셋 반환. 추후 분기 가능)
     * @return 권장 파티션 프리셋 목록 (MANDATORY_MOUNT_POINTS 순서와 일치)
     */
    public static List<PartitionPreset> getDefaultPartitions(OSName osName) {
        // 현재 지원하는 모든 Linux 계열(Rocky, Ubuntu, CentOS)은 동일한 기본 구성 사용.
        // OS별 차별화가 필요하면 osName 으로 분기한다.
        return List.of(
                new PartitionPreset("/",         FileSystem.EXT4, null, null,        false),
                new PartitionPreset("/boot",     FileSystem.EXT4, 1L,   SizeUnit.GB, false),
                new PartitionPreset("/boot/efi", FileSystem.EFI,  1L,   SizeUnit.GB, false),
                new PartitionPreset("swap",      FileSystem.SWAP, 8L,   SizeUnit.GB, false)
        );
    }

    /**
     * 마운트포인트별 강제 파일시스템 규칙.
     * 이 맵에 없는 마운트포인트는 EXCLUSIVE_FILESYSTEMS 에 속하지 않는 파일시스템만 허용한다.
     */
    private static final Map<String, FileSystem> REQUIRED_FILESYSTEM = Map.of(
            "/boot/efi", FileSystem.EFI,
            "swap",      FileSystem.SWAP
    );

    /**
     * 전용 마운트포인트 없이는 사용할 수 없는 파일시스템.
     * REQUIRED_FILESYSTEM 키에 해당하는 마운트포인트가 아닌 파티션에 지정하면 검증 실패.
     */
    private static final Set<FileSystem> EXCLUSIVE_FILESYSTEMS = Set.of(FileSystem.EFI, FileSystem.SWAP);

    @Valid
    @NotEmpty(message = "파티션 정보는 필수 값입니다.")
    protected final List<Partition> partitions;

    /** 일반(비-root) 사용자 목록. 비어 있어도 됨 (루트 비밀번호가 있는 경우). */
    @Valid
    protected final List<User> users;

    /**
     * 루트 계정 비밀번호. null 이면 루트 계정이 잠긴 상태로 설치된다.
     * Rocky Linux 9 기준, 루트가 잠긴 경우 wheel 그룹 사용자가 있으면 관리 가능.
     */
    protected final RootPassword rootPassword;

    public LinuxInstallation(
            OSName compatibleOS, List<String> compatibleOSVersion,
            List<Partition> partitions, List<User> users, RootPassword rootPassword
    ) {
        super(compatibleOS, compatibleOSVersion);

        Objects.requireNonNull(partitions, "partitions 는 null 일 수 없습니다.");

        validateLinux(compatibleOS);
        validateUserAccess(rootPassword, users);
        validateMountPoints(partitions);
        validatePartitionFileSystems(partitions, compatibleOS);
        validatePartitionSizes(partitions);
        validateGrowConstraint(partitions);

        this.partitions   = partitions;
        this.users        = users != null ? users : List.of();
        this.rootPassword = rootPassword;
    }

    /**
     * 설치 후 시스템 접근 가능 여부를 검증한다.
     *
     * <ul>
     *   <li>루트 비밀번호가 있는 경우 → 루트로 로그인 가능. 일반 사용자 불필요.</li>
     *   <li>일반 사용자만 있는 경우 → 루트 잠금 상태. 일반 사용자로 로그인 가능.
     *       sudo 없는 경우 관리 권한이 없으나 기술적으로 유효한 Kickstart.</li>
     *   <li>둘 다 없는 경우 → 설치 후 접근 불가. 반드시 거부.</li>
     * </ul>
     */
    private void validateUserAccess(RootPassword rootPassword, List<User> users) {
        boolean hasRoot  = rootPassword != null;
        boolean hasUsers = users != null && !users.isEmpty();
        if (!hasRoot && !hasUsers) {
            throw new DomainValidationException(Reason.NO_ACCESSIBLE_USER,
                    "루트 비밀번호 또는 일반 사용자 중 하나 이상을 입력해야 합니다. " +
                    "둘 다 없으면 설치 후 시스템에 접근할 수 없습니다.");
        }
    }

    /**
     * 파티션별 마운트포인트-파일시스템 조합을 검증한다.
     *
     * <ul>
     *   <li>현재 OS 와 호환되지 않는 파일시스템 사용 불가 (예: Linux 에 NTFS/FAT32)</li>
     *   <li>/boot/efi → EFI 파일시스템만 허용</li>
     *   <li>swap      → SWAP 파일시스템만 허용</li>
     *   <li>그 외     → EFI·SWAP 사용 불가 (EXT3·EXT4·XFS 등만 허용)</li>
     * </ul>
     */
    private void validatePartitionFileSystems(List<Partition> partitions, OSName osName) {
        for (Partition p : partitions) {
            String mp = p.getMountPoint();
            FileSystem fs = p.getFileSystem();

            // OS 호환성 검증: 현재 OS 에서 지원하지 않는 파일시스템 사용 불가 (예: Linux 파티션에 NTFS)
            if (!fs.getCompatibleOS().contains(osName)) {
                throw new DomainValidationException(Reason.INVALID_PARTITION_FILESYSTEM,
                        String.format("파일시스템 '%s' 은 %s 에서 지원되지 않습니다. (마운트포인트: %s)",
                                fs.getDisplayName(), osName.getDisplayName(), mp));
            }

            FileSystem required = REQUIRED_FILESYSTEM.get(mp);
            if (required != null && fs != required) {
                throw new DomainValidationException(Reason.INVALID_PARTITION_FILESYSTEM,
                        String.format("'%s' 파티션의 파일시스템은 '%s' 이어야 합니다. (현재: %s)",
                                mp, required.getDisplayName(), fs.getDisplayName()));
            }

            if (EXCLUSIVE_FILESYSTEMS.contains(fs) && required == null) {
                // 이 파일시스템이 전용으로 허용되는 마운트포인트를 메시지에 명시한다.
                String allowedMp = REQUIRED_FILESYSTEM.entrySet().stream()
                        .filter(e -> e.getValue() == fs)
                        .map(Map.Entry::getKey)
                        .findFirst().orElse("?");
                throw new DomainValidationException(Reason.INVALID_PARTITION_FILESYSTEM,
                        String.format("파일시스템 '%s' 은 '%s' 마운트포인트 전용입니다. (현재 마운트포인트: %s)",
                                fs.getDisplayName(), allowedMp, mp));
            }
        }
    }

    /**
     * 디스크당 grow 옵션이 1개 이하인지 검증한다.
     *
     * <p>Rocky Linux Kickstart 는 같은 디스크에 {@code --grow} 를 여러 개 허용하지만,
     * 그 경우 남은 공간을 분할해 예측하기 어려운 레이아웃이 만들어진다.
     * {@code diskName} 을 기준으로 그룹핑하며, null/빈 문자열은 "AUTO" 그룹으로 통합한다.
     */
    private void validateGrowConstraint(List<Partition> partitions) {
        // diskName → grow 파티션 수 집계. null·빈 문자열은 AUTO 키로 통합.
        Map<String, Long> growCountByDisk = partitions.stream()
                .filter(Partition::isGrow)
                .collect(Collectors.groupingBy(
                        p -> (p.getDiskName() == null || p.getDiskName().isBlank()) ? "AUTO" : p.getDiskName(),
                        Collectors.counting()
                ));

        growCountByDisk.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .findFirst()
                .ifPresent(e -> {
                    String diskLabel = "AUTO".equals(e.getKey())
                            ? "자동 할당 디스크"
                            : "디스크 '" + e.getKey() + "'";
                    throw new DomainValidationException(Reason.MULTIPLE_GROW_ON_SAME_DISK,
                            String.format("%s 에 grow 옵션이 %d개 지정되어 있습니다. 디스크당 grow 는 1개만 허용됩니다.",
                                    diskLabel, e.getValue()));
                });
    }

    /**
     * grow 옵션이 없는 파티션의 크기가 1 MiB 이상인지 검증한다.
     *
     * <p>grow 가 true 인 경우 size=0 이 유효하다 (Kickstart 가 남은 공간을 모두 할당).
     * grow 가 false 이면 size ≥ 1 이 필수이므로 0 이하 시 예외를 던진다.
     */
    private void validatePartitionSizes(List<Partition> partitions) {
        partitions.stream()
                .filter(p -> !p.isGrow() && p.getSizeInMB() <= 0)
                .findFirst()
                .ifPresent(p -> {
                    throw new DomainValidationException(Reason.INVALID_PARTITION_SIZE,
                            "파티션의 크기는 반드시 지정되거나 grow 가 지정되어야 합니다.");
                });
    }

    private void validateLinux(OSName compatibleOS) {
        List<OSName> compatibleLinuxOS = List.of(OSName.UBUNTU, OSName.CENTOS, OSName.ROCKY_LINUX);
        if (!compatibleLinuxOS.contains(compatibleOS)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Linux 설치는 다음 OS 들과 호환됩니다: %s",
                            String.join(
                                    ", ",
                                    compatibleLinuxOS.stream().map(OSName::getDisplayName).toList()
                            )
                    )
            );
        }
    }

    private void validateMountPoints(List<Partition> partitions) {
        // 도메인 규칙: "필수 마운트포인트가 전부 포함되어야 한다".
        // 기존 구현은 noneMatch(MANDATORY::contains) 로 "필수 목록 중 아무것도 없을 때만 throw"
        // 하도록 동작해 `/` 하나만 제공해도 통과하던 논리 버그가 있었다. 사용자가 무엇을
        // 추가해야 하는지 바로 알 수 있도록 누락된 항목을 메시지에 나열한다.
        //
        // 도메인 모델은 DTO 필드명("partitions") 을 알지 않는다. 이 예외를 catch 한 resolver 가
        // Reason → "partitions" 매핑을 수행해 FieldValidationException 으로 변환한다.
        List<String> providedMountPoints = partitions.stream().map(Partition::getMountPoint).toList();
        List<String> missing = MANDATORY_MOUNT_POINTS.stream()
                .filter(mp -> !providedMountPoints.contains(mp))
                .toList();
        if (!missing.isEmpty()) {
            throw new DomainValidationException(Reason.MISSING_MANDATORY_MOUNT_POINTS,
                    String.format(
                            "Linux 설치에 필수 마운트포인트가 누락되었습니다: %s. 필수 목록: %s",
                            String.join(", ", missing),
                            String.join(", ", MANDATORY_MOUNT_POINTS)
                    )
            );
        }
    }
}
