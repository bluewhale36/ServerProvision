package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.DomainValidationException.Reason;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * RHEL 계열(Rocky Linux, CentOS) 설치의 공통 추상 클래스.
 *
 * <p>역할: Kickstart 포맷을 공유하는 모든 RHEL 기반 배포판의 공통 필드와 스크립트 구성
 * 템플릿을 제공한다. {@link #getInstallScript(InstallationContext)} 는 Template Method
 * 패턴으로 동작하며, 메이저 버전별 차이(버전 헤더, rootpw 포맷, UEFI 파티션 요구 여부)는
 * 하위 클래스가 {@code protected} 훅을 override 하여 반영한다.</p>
 *
 * <p>하위 클래스: {@link RockyLinux8Installation}, {@link RockyLinux9Installation},
 * {@link RockyLinux10Installation}, {@link CentOS7Installation}.</p>
 *
 * <p>설계 결정:
 * <ul>
 *     <li>{@code /boot/efi} 필수 여부는 생성자 인자 {@code requireBootEfi} 로 전달된다.
 *         Java 가 super() 실행 중 하위 클래스의 메서드 호출을 허용하지만 하위 인스턴스 필드가
 *         아직 초기화되지 않은 상태이므로, 상수에 가까운 값도 생성자 인자로 명시하는 편이 안전하다.</li>
 *     <li>Jackson {@code @JsonCreator}/{@code @JsonSubTypes} 는 각 구체 클래스에 위임한다.
 *         추상 클래스 자체는 Jackson 이 인스턴스화하지 않는다.</li>
 * </ul>
 * </p>
 */
@Getter
public abstract class RHELBasedInstallation extends LinuxInstallation {

    @NotEmpty(message = "버전 정보는 필수 값입니다.")
    protected final String installVersion;

    @NotNull(message = "설치 환경 정보는 필수 값입니다.")
    protected final Environment environment;

    @NotNull(message = "타임존 정보는 필수 값입니다.")
    protected final Timezone timezone;

    protected final boolean isKDumpEnabled;

    protected RHELBasedInstallation(
            OSName osName,
            List<String> compatibleVersions,
            List<Partition> partitions,
            List<User> users,
            RootPassword rootPassword,
            String installVersion,
            Environment environment,
            Timezone timezone,
            boolean isKDumpEnabled
    ) {
        super(osName, compatibleVersions, partitions, users, rootPassword);

        Objects.requireNonNull(environment, "environment 는 null 일 수 없습니다.");

        // UEFI 파티션 요구 — 각 하위 클래스의 requireBootEfi() 훅 기준으로 추가 검증.
        // super() 시점에는 하위 vtable 이 연결되어 있으므로 훅 호출이 안전하다 (상태 필드 참조 없음).
        if (requireBootEfi()) {
            boolean hasBootEfi = partitions.stream()
                    .anyMatch(p -> "/boot/efi".equals(p.getMountPoint()));
            if (!hasBootEfi) {
                throw new DomainValidationException(Reason.MISSING_MANDATORY_MOUNT_POINTS,
                        "이 RHEL 계열 버전은 UEFI 부팅을 사용하므로 '/boot/efi' 마운트포인트가 필수입니다.");
            }
        }

        // installVersion 은 호환성 매트릭스로만 유효성 체크 (isVersionCompatible 는 boolean 반환).
        // 실제 호환성 거부는 application.setting.model.OSInstallation 의 isCompatible() 에서 수행한다.
        isVersionCompatible(installVersion);

        this.installVersion = installVersion;
        this.environment    = environment;
        this.timezone       = timezone;
        this.isKDumpEnabled = isKDumpEnabled;
    }

    /**
     * Kickstart 스크립트를 완성하여 {@link RenderedScript} 로 반환한다.
     *
     * <p>Template Method — 모든 RHEL 계열이 동일한 섹션 구성을 따르고,
     * 버전별 차이점은 {@link #getKickstartVersionMarker()} 와 {@link #renderRootPwLine()}
     * 훅에서만 발생한다.</p>
     */
    @Override
    public RenderedScript getInstallScript(InstallationContext ctx) {
        return new RenderedScript(buildKickstart(ctx), InstallScriptFormat.KICKSTART);
    }

    private String buildKickstart(InstallationContext ctx) {
        StringBuilder sb = new StringBuilder();

        // Kickstart 버전 식별자 — RHEL 메이저 버전별 상이 (#version=RHEL7/8/9/10)
        sb.append(getKickstartVersionMarker()).append("\n");

        // 설치 방법 — Anaconda 에서 'install' 키워드는 deprecated 이므로 제거
        sb.append("# 설치 방법\n");
        sb.append("url --url=").append(ctx.installSourceUrl()).append("\n");
        sb.append("\n");

        // 그래픽/텍스트 모드, 첫 부팅 마법사 비활성화 (콘솔 대기 회피)
        sb.append("# 그래픽/텍스트 모드\n");
        sb.append("text\n");
        sb.append("firstboot --disable\n");
        sb.append("\n");

        sb.append("# 키보드/언어\n");
        sb.append("keyboard --xlayouts='us'\n");
        sb.append("lang en_US.UTF-8\n");
        sb.append("\n");

        sb.append("# 네트워크\n");
        sb.append("network --bootproto=dhcp");
        if (ctx.hostname() != null && !ctx.hostname().isBlank()) {
            sb.append(" --hostname=").append(ctx.hostname());
        }
        sb.append(" --activate\n");
        sb.append("\n");

        sb.append("# 루트 비밀번호 + 사용자 계정\n");
        sb.append(renderUserSection());

        sb.append("# 타임존\n");
        sb.append(timezone.getRHELScript());
        sb.append("\n");

        sb.append("# 파티션 설정\n");
        sb.append(renderPartitionSection());

        // 부트로더 — UEFI/BIOS 자동 감지이므로 --location 플래그는 생략
        sb.append("# 부트로더\n");
        sb.append("bootloader --append=\"crashkernel=auto\"\n");
        sb.append("\n");

        sb.append("# 패키지\n");
        sb.append("%packages\n");
        sb.append(environment.getRHELScript());
        sb.append("%end\n");
        sb.append("\n");

        sb.append("# KDump\n");
        sb.append(renderKDumpSection());
        sb.append("\n");

        sb.append("# 설치 완료 후 재부팅\n");
        sb.append("reboot\n");

        return sb.toString();
    }

    protected String renderPartitionSection() {
        StringBuilder sb = new StringBuilder();
        // zerombr: 기존 MBR/GPT 시그니처 초기화 (Anaconda 대화형 확인 회피)
        sb.append("zerombr\n");
        sb.append("clearpart --all --initlabel\n");
        partitions.forEach(p -> sb.append(p.getRHELScript()));
        sb.append("\n");
        return sb.toString();
    }

    protected String renderUserSection() {
        StringBuilder sb = new StringBuilder();
        // rootpw 는 renderRootPwLine() 훅을 통해 생성 (Rocky 10 이 --allow-ssh override)
        String rootPwLine = renderRootPwLine();
        if (!rootPwLine.isEmpty()) sb.append(rootPwLine);
        users.forEach(u -> sb.append(u.getRHELScript()));
        sb.append("\n");
        return sb.toString();
    }

    protected String renderKDumpSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("%addon com_redhat_kdump");
        if (isKDumpEnabled) sb.append(" --enable");
        else sb.append(" --disable");
        // Kickstart 파서는 쉘이 아니므로 따옴표 없이 'auto' 값만 전달
        sb.append(" --reserve-mb=auto");
        sb.append("\n");
        sb.append("%end\n");
        return sb.toString();
    }

    /**
     * Kickstart 파일 상단의 버전 주석 (예: {@code "#version=RHEL9"}).
     * Anaconda 가 이 값을 읽어 호환성을 판단하므로 실제 RHEL 메이저 버전에 맞추어 반환해야 한다.
     */
    protected abstract String getKickstartVersionMarker();

    /**
     * UEFI {@code /boot/efi} 파티션 요구 여부. 기본값은 {@code true}.
     *
     * <p>CentOS 7 등 BIOS/MBR 부팅도 허용되는 버전은 {@code false} 로 override 한다.</p>
     */
    protected boolean requireBootEfi() {
        return true;
    }

    /**
     * Kickstart {@code rootpw} 라인 전체(개행 포함)를 렌더링한다.
     *
     * <p>기본 구현: {@link RootPassword#getRHELScript()} 위임. {@code rootPassword} 가 {@code null}
     * 이면 빈 문자열 (root 잠금 상태 — 일반 사용자로 접근).</p>
     *
     * <p>Rocky 10 override: {@code rootpw --allow-ssh --iscrypted <hash>} 형태로
     * {@code --allow-ssh} 플래그를 추가한다.</p>
     */
    protected String renderRootPwLine() {
        return rootPassword != null ? rootPassword.getRHELScript() : "";
    }
}
