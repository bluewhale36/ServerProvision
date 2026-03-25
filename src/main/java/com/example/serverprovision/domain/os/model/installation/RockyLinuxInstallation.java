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

    /**
     * Kickstart 내에서 사용자 생성 관련 스크립트를 String 으로 반환.<br/>
     * Sudo-er 의 경우 해당 스크립트에서 생성되지 않음.
     *
     * @return 사용자 생성의 Kickstart Script 문자열.
     */
    protected String getUserScript() {
        StringBuilder sb = new StringBuilder();

        // root 유저가 없는 경우 LinuxInstallation class 에서 IllegalArgumentsException 발생.
        User root = users.stream().filter(User::isRoot).findFirst().get();
        List<User> withoutRoot = users.stream().filter(user -> !user.equals(root)).toList();

        // root 유저 script
        sb.append("rootpw ").append(root.getPassword());
        if (root.isPasswordEncrypted()) sb.append(" --iscrypted");
        else sb.append(" --plaintext");
        sb.append("\n");

        // 이 외 일반 사용자 script
        for (User user : withoutRoot) {
            sb.append("user --name=").append(user.getUsername());
            sb.append(" --password=").append(user.getPassword());
            if (user.isPasswordEncrypted()) sb.append(" --iscrypted");
            else sb.append(" --plaintext");
            sb.append("\n");
        }

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
