package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.DomainValidationException.Reason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.regex.Pattern;

public class Partition implements InstallScriptable {

    /**
     * 유효한 Linux 마운트포인트 패턴.
     * 허용: swap, /, /로 시작하는 절대경로 (각 경로 컴포넌트는 영문자로 시작, 영숫자·밑줄·하이픈 포함).
     * 공백·제어문자·특수문자를 차단해 Kickstart 스크립트 옵션 주입을 방지한다.
     */
    private static final Pattern VALID_MOUNT_POINT =
            Pattern.compile("^(swap|/|(/[A-Za-z][A-Za-z0-9_-]*)(/[A-Za-z][A-Za-z0-9_-]*)*)$");

    /**
     * 유효한 Linux 블록 디바이스명 패턴.
     * 허용: 영문자로 시작, 이후 영숫자 (예: sda, nvme0n1, vda).
     */
    private static final Pattern VALID_DISK_NAME =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*$");

    @Getter
    @NotBlank(message = "마운트 포인트는 필수 입력값입니다.")
    private final String mountPoint;
    @Getter
    @NotNull(message = "파일 시스템은 필수 값입니다.")
    private final FileSystem fileSystem;
    @Getter
    private final String diskName;
    @Getter
    private final long sizeInMB;
    @Getter
    private final boolean isGrow;

    @Builder
    private Partition(String mountPoint, FileSystem fileSystem, String diskName, long sizeInMB, boolean isGrow) {
        if (mountPoint == null || !VALID_MOUNT_POINT.matcher(mountPoint).matches()) {
            throw new DomainValidationException(Reason.INVALID_PARTITION_VALUE,
                    "마운트포인트에 허용되지 않는 문자가 포함되어 있습니다: '" + mountPoint +
                    "' (허용 형식: swap, /, /로 시작하는 절대경로)");
        }
        if (diskName != null && !diskName.isBlank() && !VALID_DISK_NAME.matcher(diskName).matches()) {
            throw new DomainValidationException(Reason.INVALID_PARTITION_VALUE,
                    "디스크명에 허용되지 않는 문자가 포함되어 있습니다: '" + diskName +
                    "' (영숫자만 허용, 첫 문자는 영문자)");
        }
        this.mountPoint = mountPoint;
        this.fileSystem = fileSystem;
        this.diskName   = diskName;
        this.sizeInMB   = sizeInMB;
        this.isGrow     = isGrow;
    }


    @Override
    public String getRHELScript() {
        StringBuilder sb = new StringBuilder();

        sb.append("part ").append(mountPoint).append(" --fstype=\"").append(fileSystem.getDisplayName()).append("\"");

        if (isGrow) {
            sb.append(" --grow");
        }
        if (diskName != null && !diskName.isBlank()) {
            sb.append(" --ondisk=").append(diskName);
        }
        // grow 전용 파티션(size=0)은 --size 생략. 그 외에는 --size 필수.
        if (sizeInMB > 0) {
            sb.append(" --size=").append(sizeInMB);
        }
        sb.append("\n");

        return sb.toString();
    }
}
