package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.model.enums.ServiceAction;
import lombok.Getter;

import java.util.List;

/**
 * RHEL 계열 OS(Rocky Linux, CentOS) 의 공통 Post-install 설정 베이스.
 *
 * <p>Kickstart {@code %post} 섹션 스크립트 생성 템플릿을 제공한다. SELinux 모드,
 * 추가 패키지 설치(dnf), systemd 서비스 지시(enable/disable) 3요소를 조립한다.</p>
 *
 * <p>버전별 서브클래스({@code RockyLinux9Setting}, {@code CentOS7Setting} 등)는 호환 OS/버전만 선언하고
 * 필요 시 {@code getPostInstallScript()} 를 오버라이드하여 버전 고유 로직을 추가한다.</p>
 */
@Getter
public abstract class RHELBasedSetting extends OSSetting {

    /** SELinux 모드: {@code enforcing}, {@code permissive}, {@code disabled} 중 하나. */
    private final String selinuxMode;

    /**
     * systemd 서비스 지시 목록. 각 원소는 {@link ServiceDirective} 로 이름과 동작(enable/disable)을 보유한다.
     */
    private final List<ServiceDirective> services;

    /** {@code dnf install -y} 로 설치할 추가 패키지 목록. */
    private final List<String> additionalPackages;

    protected RHELBasedSetting(
            OSName compatibleOS,
            List<String> compatibleOSVersion,
            String selinuxMode,
            List<ServiceDirective> services,
            List<String> additionalPackages
    ) {
        super(compatibleOS, compatibleOSVersion);
        this.selinuxMode        = selinuxMode;
        this.services           = services           != null ? services           : List.of();
        this.additionalPackages = additionalPackages != null ? additionalPackages : List.of();
    }

    /**
     * Kickstart {@code %post} 섹션 스크립트를 조립하여 반환한다.
     *
     * <p>조립 규칙:
     * <ul>
     *   <li>{@code selinuxMode} 가 {@code "enforcing"} 이면 {@code setenforce 1}, {@code "permissive"} 이면
     *       {@code setenforce 0}, {@code "disabled"} 는 {@code setenforce} 를 생략한다. 모든 모드에서
     *       {@code /etc/selinux/config} 를 sed 로 수정해 다음 부팅부터 적용되도록 한다.</li>
     *   <li>{@code additionalPackages} 가 비어 있으면 {@code dnf install} 블록을 생략한다.</li>
     *   <li>{@code services} 가 비어 있으면 {@code systemctl} 블록을 생략한다.
     *       각 지시는 {@link ServiceAction} 에 따라 {@code systemctl enable} 혹은 {@code systemctl disable}
     *       로 변환된다.</li>
     * </ul>
     */
    @Override
    public String getPostInstallScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("%post\n");

        if (selinuxMode != null && !selinuxMode.isBlank()) {
            sb.append("# SELinux 모드 설정\n");
            // setenforce 는 chroot 환경의 %post 에서 커널 접근이 없어 런타임 효과가 없지만,
            // 표기상 명시해두고, 실제 적용은 아래 sed 로 /etc/selinux/config 를 수정해
            // 다음 부팅부터 반영되도록 한다.
            if ("enforcing".equalsIgnoreCase(selinuxMode)) {
                sb.append("setenforce 1\n");
            } else if ("permissive".equalsIgnoreCase(selinuxMode)) {
                sb.append("setenforce 0\n");
            }
            sb.append("sed -i 's/^SELINUX=.*/SELINUX=")
                    .append(selinuxMode.toLowerCase())
                    .append("/' /etc/selinux/config\n\n");
        }

        if (!additionalPackages.isEmpty()) {
            sb.append("# 추가 패키지 설치\n");
            sb.append("dnf install -y");
            for (String pkg : additionalPackages) {
                sb.append(" ").append(pkg);
            }
            sb.append("\n\n");
        }

        if (!services.isEmpty()) {
            sb.append("# 서비스 활성화/비활성화\n");
            for (ServiceDirective directive : services) {
                sb.append("systemctl ")
                        .append(directive.action().getSystemctlSubcommand())
                        .append(" ")
                        .append(directive.name())
                        .append("\n");
            }
            sb.append("\n");
        }

        sb.append("%end\n");
        return sb.toString();
    }
}
