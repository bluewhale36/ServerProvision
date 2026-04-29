package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Ubuntu 22.04.x 설치 모델 (Subiquity autoinstall 포맷).
 *
 * <p>호환 버전: 22.04.3, 22.04.4, 22.04.5. Ubuntu 는 Kickstart 대신
 * cloud-init user-data YAML ({@code autoinstall}) 포맷을 사용하므로
 * {@link #getInstallScript(InstallationContext)} 는 {@link InstallScriptFormat#AUTOINSTALL_YAML}
 * 포맷의 {@link RenderedScript} 를 반환한다.</p>
 *
 * <p>현재 구현 범위는 스켈레톤 — RHEL 계열과 달리 Environment/PackageGroup 추상화가 아직
 * 적용되지 않았다. 후속 단계에서 Ubuntu Server/Desktop snap 카탈로그 기반의 패키지 선택
 * 메커니즘을 별도로 설계한다.</p>
 */
@Getter
public class UbuntuInstallation extends LinuxInstallation {

    private static final List<String> COMPATIBLE_VERSIONS =
            List.of("22.04.3", "22.04.4", "22.04.5");

    @NotEmpty(message = "버전 정보는 필수 값입니다.")
    private final String installVersion;

    private final Timezone timezone;

    /** hostname은 autoinstall identity.hostname 필드로 매핑된다. cloud-init 이후 네트워크 설정에 사용. */
    private final String hostname;

    /**
     * 추가 설치 패키지 목록. APT 패키지 이름 (예: {@code "openssh-server"}, {@code "curl"}).
     *
     * <p>RHEL 의 Environment/PackageGroup 개념 대신 Ubuntu 는 autoinstall {@code packages:}
     * 배열에 직접 평면적으로 나열한다.</p>
     */
    private final List<String> packages;

    @Builder
    @JsonCreator
    protected UbuntuInstallation(
            @JsonProperty("partitions")     List<Partition> partitions,
            @JsonProperty("users")          List<User> users,
            @JsonProperty("rootPassword")   RootPassword rootPassword,
            @JsonProperty("installVersion") String installVersion,
            @JsonProperty("timezone")       Timezone timezone,
            @JsonProperty("hostname")       String hostname,
            @JsonProperty("packages")       List<String> packages
    ) {
        super(OSName.UBUNTU, COMPATIBLE_VERSIONS,
                partitions, users, rootPassword);
        isVersionCompatible(installVersion);
        this.installVersion = installVersion;
        this.timezone       = timezone;
        this.hostname       = hostname;
        this.packages       = packages == null ? List.of() : packages;
    }

    @Override
    public RenderedScript getInstallScript(InstallationContext ctx) {
        return new RenderedScript(buildAutoinstallYaml(ctx), InstallScriptFormat.AUTOINSTALL_YAML);
    }

    /**
     * Ubuntu Subiquity autoinstall YAML 스켈레톤을 생성한다.
     *
     * <p>현재 구현은 최소 실행 가능한 구조만 포함한다. 후속 단계에서 파티션/스토리지,
     * user-data, network, late-commands 등 섹션이 확장된다.</p>
     */
    private String buildAutoinstallYaml(InstallationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("#cloud-config\n");
        sb.append("autoinstall:\n");
        sb.append("  version: 1\n");
        sb.append("  locale: en_US.UTF-8\n");
        sb.append("  keyboard:\n");
        sb.append("    layout: us\n");
        sb.append("  identity:\n");
        sb.append("    hostname: ").append(hostname != null ? hostname
                : (ctx.hostname() != null ? ctx.hostname() : "ubuntu-server")).append("\n");
        if (!users.isEmpty()) {
            User first = users.get(0);
            sb.append("    username: ").append(first.getUsername()).append("\n");
            sb.append("    password: ").append(first.getPassword()).append("\n");
        }
        sb.append("  storage:\n");
        sb.append("    layout:\n");
        sb.append("      name: direct\n");
        if (timezone != null) {
            sb.append("  timezone: ").append(timezone.getTimezone()).append("\n");
        }
        if (!packages.isEmpty()) {
            sb.append("  packages:\n");
            for (String pkg : packages) {
                sb.append("    - ").append(pkg).append("\n");
            }
        }
        return sb.toString();
    }
}
