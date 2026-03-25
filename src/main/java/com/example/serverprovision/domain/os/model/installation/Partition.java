package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.FileSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;

@Builder
public class Partition implements InstallScriptable {

    @Getter
    @NotBlank(message = "마운트 포인트는 필수 입력값입니다.")
    private final String mountPoint;
    @NotNull(message = "파일 시스템은 필수 값입니다.")
    private final FileSystem fileSystem;
    private final String diskName;
    @Positive(message = "파티션 크기는 양수여야 합니다.")
    private final long sizeInMB;
    private final boolean isGrow;


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

        sb.append(" --size=").append(sizeInMB).append("\n");

        return sb.toString();
    }
}
