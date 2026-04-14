package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * Rocky Linux 8.x 설치 모델.
 *
 * <p>호환 버전: 8.10. Kickstart 버전 헤더는 {@code #version=RHEL8}.
 * 8.x 도 UEFI 부팅을 공식 지원하므로 {@link #requireBootEfi()} 는 기본값 {@code true}.</p>
 */
public class RockyLinux8Installation extends RHELBasedInstallation {

    private static final List<String> COMPATIBLE_VERSIONS = List.of("8.10");

    @Builder
    @JsonCreator
    protected RockyLinux8Installation(
            @JsonProperty("partitions")     List<Partition> partitions,
            @JsonProperty("users")          List<User> users,
            @JsonProperty("rootPassword")   RootPassword rootPassword,
            @JsonProperty("installVersion") String installVersion,
            @JsonProperty("environment")    Environment environment,
            @JsonProperty("timezone")       Timezone timezone,
            @JsonProperty("KDumpEnabled")   boolean isKDumpEnabled
    ) {
        super(
                OSName.ROCKY_LINUX, COMPATIBLE_VERSIONS,
                partitions, users, rootPassword,
                installVersion, environment, timezone, isKDumpEnabled
        );
    }

    @Override
    protected String getKickstartVersionMarker() {
        return "#version=RHEL8";
    }
}
