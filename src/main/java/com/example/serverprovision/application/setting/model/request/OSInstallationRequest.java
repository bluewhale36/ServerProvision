package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Getter
public class OSInstallationRequest extends AbstractProcessRequest {

    // os_metadata.id — OS 타입과 버전 모두를 식별 (installVersion은 서비스에서 파생)
    @NotNull(message = "OS 메타데이터 ID는 필수 값입니다.")
    private final Long osMetadataId;

    private final boolean isKDumpEnabled;

    @NotNull(message = "타임존 정보는 필수 값입니다.")
    @Valid
    private final TimezoneRequest timezone;

    @NotNull(message = "설치 환경 ID는 필수 값입니다.")
    private final Long environmentId;

    @NotEmpty(message = "패키지 그룹을 하나 이상 선택해야 합니다.")
    private final List<Long> packageGroupIds;

    @NotEmpty(message = "파티션 정보는 필수 값입니다.")
    @Valid
    private final List<PartitionRequest> partitions;

    @NotEmpty(message = "사용자 정보는 필수 값입니다.")
    @Valid
    private final List<UserRequest> users;

    @JsonCreator
    public OSInstallationRequest(
            @JsonProperty("osMetadataId")    Long osMetadataId,
            @JsonProperty("isKDumpEnabled")  boolean isKDumpEnabled,
            @JsonProperty("timezone")        TimezoneRequest timezone,
            @JsonProperty("environmentId")   Long environmentId,
            @JsonProperty("packageGroupIds") List<Long> packageGroupIds,
            @JsonProperty("partitions")      List<PartitionRequest> partitions,
            @JsonProperty("users")           List<UserRequest> users) {
        this.osMetadataId    = osMetadataId;
        this.isKDumpEnabled  = isKDumpEnabled;
        this.timezone        = timezone;
        this.environmentId   = environmentId;
        this.packageGroupIds = packageGroupIds;
        this.partitions      = partitions;
        this.users           = users;
    }
}
