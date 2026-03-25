package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class RockyLinuxInstallation extends LinuxInstallation {

    @NotEmpty(message = "버전 정보는 필수 값입니다.")
    private final String installVersion;

    @NotNull(message = "설치 환경 정보는 필수 값입니다.")
    private final Environment environment;

    private final boolean isKDumpEnabled;

    @Builder
    protected RockyLinuxInstallation(
            List<Partition> partitions, List<User> users,
            String installVersion, Environment environment, boolean isKDumpEnabled
    ) {
        super(
                OSName.ROCKY_LINUX,
                List.of("8.10", "9.0", "9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7"),

                partitions, users
        );

        isVersionCompatible(installVersion);

        this.installVersion = installVersion;
        this.environment = environment;
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
