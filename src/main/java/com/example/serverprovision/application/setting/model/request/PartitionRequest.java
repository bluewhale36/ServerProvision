package com.example.serverprovision.application.setting.model.request;

import com.example.serverprovision.domain.os.model.enums.FileSystem;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class PartitionRequest {

    @NotBlank(message = "마운트 포인트는 필수 입력값입니다.")
    private final String mountPoint;

    @NotNull(message = "파일 시스템은 필수 값입니다.")
    private final FileSystem fileSystem;

    private final String diskName;      // null 허용 (자동 할당)

    @Positive(message = "파티션 크기는 양수여야 합니다.")
    private final long sizeInMB;

    private final boolean isGrow;

    @JsonCreator
    public PartitionRequest(
            @JsonProperty("mountPoint") String mountPoint,
            @JsonProperty("fileSystem") FileSystem fileSystem,
            @JsonProperty("diskName")   String diskName,
            @JsonProperty("sizeInMB")   long sizeInMB,
            @JsonProperty("isGrow")     boolean isGrow) {
        this.mountPoint = mountPoint;
        this.fileSystem = fileSystem;
        this.diskName   = diskName;
        this.sizeInMB   = sizeInMB;
        this.isGrow     = isGrow;
    }
}
