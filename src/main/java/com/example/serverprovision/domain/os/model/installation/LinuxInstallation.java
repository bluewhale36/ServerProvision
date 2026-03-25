package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
public abstract class LinuxInstallation extends OSInstallation {

    private final List<String> MANDATORY_MOUNT_POINTS = List.of("/", "/boot", "/boot/efi", "swap");

    @NotEmpty(message = "파티션 정보는 필수 값입니다.")
    protected final List<Partition> partitions;

    @NotEmpty(message = "사용자 정보는 필수 값입니다.")
    protected final List<User> users;

    public LinuxInstallation(
            OSName compatibleOS, List<String> compatibleOSVersion,
            List<Partition> partitions, List<User> users
    ) {
        super(compatibleOS, compatibleOSVersion);

        validateLinux(compatibleOS);
        validateUsers(users);
        validateMountPoints(partitions);

        this.partitions = partitions;
        this.users = users;
    }

    private void validateUsers(List<User> users) {
        if (users.stream().noneMatch(User::isRoot)) {
            throw new IllegalArgumentException("사용자 목록에는 반드시 root 사용자가 포함되어야 합니다.");
        }
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
        if (
                partitions.stream().map(Partition::getMountPoint).noneMatch(MANDATORY_MOUNT_POINTS::contains)
        ) {
            throw new IllegalArgumentException(
                    String.format(
                            "Rocky Linux 설치에는 반드시 %s 파티션이 포함되어야 합니다.",
                            String.join(", ", MANDATORY_MOUNT_POINTS)
                    )
            );
        }
    }
}
