package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * CentOS 7.x 설치 모델.
 *
 * <p>호환 버전: 7.9 (EOL 된 마지막 안정 버전). Kickstart 버전 헤더는 {@code #version=RHEL7}.
 * CentOS 7 은 BIOS/MBR 부팅도 표준이므로 {@link #requireBootEfi()} 를 {@code false} 로
 * override 하여 {@code /boot/efi} 파티션 없이도 검증을 통과하도록 한다.</p>
 */
public class CentOS7Installation extends RHELBasedInstallation {

    private static final List<String> COMPATIBLE_VERSIONS = List.of("7.9");

    @Builder
    @JsonCreator
    protected CentOS7Installation(
            @JsonProperty("partitions")     List<Partition> partitions,
            @JsonProperty("users")          List<User> users,
            @JsonProperty("rootPassword")   RootPassword rootPassword,
            @JsonProperty("installVersion") String installVersion,
            @JsonProperty("environment")    Environment environment,
            @JsonProperty("timezone")       Timezone timezone,
            @JsonProperty("KDumpEnabled")   boolean isKDumpEnabled
    ) {
        super(
                OSName.CENTOS, COMPATIBLE_VERSIONS,
                partitions, users, rootPassword,
                installVersion, environment, timezone, isKDumpEnabled
        );
    }

    @Override
    protected String getKickstartVersionMarker() {
        return "#version=RHEL7";
    }

    @Override
    protected boolean requireBootEfi() {
        // CentOS 7 은 BIOS/MBR 부팅도 표준 — /boot/efi 파티션이 없어도 설치 가능.
        return false;
    }
}
