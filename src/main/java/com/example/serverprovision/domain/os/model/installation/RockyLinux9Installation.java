package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * Rocky Linux 9.x 설치 모델.
 *
 * <p>호환 버전: 9.0 ~ 9.7. 9.x 는 UEFI 부팅을 전제로 하므로
 * {@link #requireBootEfi()} 는 기본값 {@code true} 를 그대로 사용한다.
 * Kickstart 버전 헤더는 {@code #version=RHEL9}.</p>
 */
public class RockyLinux9Installation extends RHELBasedInstallation {

    private static final List<String> COMPATIBLE_VERSIONS =
            List.of("9.0", "9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7");

    @Builder
    @JsonCreator
    protected RockyLinux9Installation(
            @JsonProperty("partitions")     List<Partition> partitions,
            @JsonProperty("users")          List<User> users,
            @JsonProperty("rootPassword")   RootPassword rootPassword,
            @JsonProperty("installVersion") String installVersion,
            @JsonProperty("environment")    Environment environment,
            @JsonProperty("timezone")       Timezone timezone,
            // Jackson 이 isKDumpEnabled() getter 에서 "is" 접두사를 제거해 "KDumpEnabled" 로 직렬화함
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
        return "#version=RHEL9";
    }
}
