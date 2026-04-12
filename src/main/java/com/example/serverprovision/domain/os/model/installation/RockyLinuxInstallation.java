package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class RockyLinuxInstallation extends LinuxInstallation {

    @NotEmpty(message = "버전 정보는 필수 값입니다.")
    private final String installVersion;

    @NotNull(message = "설치 환경 정보는 필수 값입니다.")
    private final Environment environment;

    @NotNull(message = "타임존 정보는 필수 값입니다.")
    private final Timezone timezone;

    private final boolean isKDumpEnabled;

    @Builder
    @JsonCreator
    protected RockyLinuxInstallation(
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
                OSName.ROCKY_LINUX,
                List.of("8.10", "9.0", "9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7"),
                partitions, users, rootPassword
        );

        isVersionCompatible(installVersion);

        Objects.requireNonNull(environment, "environment 는 null 일 수 없습니다.");

        this.installVersion = installVersion;
        this.environment    = environment;
        this.timezone       = timezone;
        this.isKDumpEnabled = isKDumpEnabled;
    }

    @Override
    public String getKickstartScript() {
        return "";
    }

    protected String getPartitionScript() {
        StringBuilder sb = new StringBuilder();

        sb.append("clearpart --all --initlabel\n");
        partitions.forEach(p -> sb.append(p.getRHELScript()));

        sb.append("\n");

        return sb.toString();
    }

    protected String getUserScript() {
        StringBuilder sb = new StringBuilder();

        // root 비밀번호가 있는 경우 rootpw 명령을 먼저 생성한다
        if (rootPassword != null) sb.append(rootPassword.getRHELScript());

        users.forEach(u -> sb.append(u.getRHELScript()));

        sb.append("\n");

        return sb.toString();
    }

    protected String getKDumpScript() {
        StringBuilder sb = new StringBuilder();

        sb.append("%addon com_redhat_kdump");
        if (isKDumpEnabled) sb.append(" --enable");
        else sb.append(" --disable");
        sb.append(" --reserve-mb='auto'");

        sb.append("\n");

        sb.append("%end").append("\n");

        return sb.toString();
    }

}
