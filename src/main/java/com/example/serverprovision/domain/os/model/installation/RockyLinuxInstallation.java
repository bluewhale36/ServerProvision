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

    /**
     * Rocky Linux 9 Kickstart 파일 전체 내용을 조합하여 반환한다.
     *
     * <p>순서: 버전 주석 → 설치 방법(url) → 텍스트 모드 → 키보드/언어 → 네트워크 →
     * 사용자/루트 비밀번호 → 타임존 → 파티션 → 부트로더 → 패키지 → KDump → 재부팅</p>
     *
     * @param ctx 호스트명·IP·설치 소스 URL 등 런타임 컨텍스트
     * @return 완성된 Kickstart 9 파일 내용
     */
    @Override
    public String getKickstartScript(KickstartContext ctx) {
        StringBuilder sb = new StringBuilder();

        // Kickstart 버전 식별자
        sb.append("#version=RHEL9\n");

        // 설치 방법 — Rocky Linux 9(Anaconda)에서 'install' 키워드는 deprecated이므로 제거
        sb.append("# 설치 방법\n");
        sb.append("url --url=").append(ctx.installSourceUrl()).append("\n");
        sb.append("\n");

        // 그래픽/텍스트 모드, 첫 부팅 마법사 비활성화
        // firstboot --disable: 없으면 설치 후 Initial Setup 마법사가 콘솔 입력을 대기해 무인 프로비저닝 중단
        sb.append("# 그래픽/텍스트 모드\n");
        sb.append("text\n");
        sb.append("firstboot --disable\n");
        sb.append("\n");

        // 키보드 및 언어 설정
        sb.append("# 키보드/언어\n");
        sb.append("keyboard --xlayouts='us'\n");
        sb.append("lang en_US.UTF-8\n");
        sb.append("\n");

        // 네트워크 설정 — hostname 을 컨텍스트에서 주입
        sb.append("# 네트워크\n");
        sb.append("network --bootproto=dhcp");
        if (ctx.hostname() != null && !ctx.hostname().isBlank()) {
            sb.append(" --hostname=").append(ctx.hostname());
        }
        sb.append(" --activate\n");
        sb.append("\n");

        // 루트 비밀번호 + 사용자 계정
        sb.append("# 루트 비밀번호 + 사용자 계정\n");
        sb.append(getUserScript());

        // 타임존
        sb.append("# 타임존\n");
        sb.append(timezone.getRHELScript());
        sb.append("\n");

        // 파티션 설정
        sb.append("# 파티션 설정\n");
        sb.append(getPartitionScript());

        // 부트로더
        // --location 옵션은 Rocky Linux 9에서 deprecated이며, UEFI(/boot/efi 필수) 환경과도 모순됨
        // Anaconda가 UEFI/BIOS를 자동 감지하므로 --location 없이 사용
        sb.append("# 부트로더\n");
        sb.append("bootloader --append=\"crashkernel=auto\"\n");
        sb.append("\n");

        // 패키지 섹션
        sb.append("# 패키지\n");
        sb.append("%packages\n");
        sb.append(environment.getRHELScript());
        sb.append("%end\n");
        sb.append("\n");

        // KDump 애드온 (활성화 여부에 따라 조건부 포함)
        sb.append("# KDump\n");
        sb.append(getKDumpScript());
        sb.append("\n");

        // 설치 완료 후 재부팅
        sb.append("# 설치 완료 후 재부팅\n");
        sb.append("reboot\n");

        return sb.toString();
    }

    protected String getPartitionScript() {
        StringBuilder sb = new StringBuilder();

        // zerombr: 기존 파티션 테이블 시그니처(MBR/GPT)를 초기화한다.
        // clearpart --initlabel 만으로는 이전 시그니처가 남아 Anaconda가 대화형 확인을 요구할 수 있다.
        sb.append("zerombr\n");
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
        // Kickstart 파서는 쉘이 아니므로 따옴표를 포함하면 'auto' 가 값으로 전달돼 파싱 오류 발생
        sb.append(" --reserve-mb=auto");

        sb.append("\n");

        sb.append("%end").append("\n");

        return sb.toString();
    }

}
