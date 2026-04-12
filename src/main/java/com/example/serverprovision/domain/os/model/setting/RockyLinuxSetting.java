package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;

import java.util.List;

/**
 * Rocky Linux 전용 OS 후처리 설정 클래스이다.
 *
 * <p>역할: Rocky Linux 설치 완료 후 두 번째 PXE 부팅 시 실행될
 * Kickstart {@code %post} 섹션 스크립트를 생성한다.
 * SELinux 모드 설정, 추가 패키지 설치, systemd 서비스 활성화를 담당한다.</p>
 */
public class RockyLinuxSetting extends OSSetting {

    protected RockyLinuxSetting() {
        super(OSName.ROCKY_LINUX, List.of());
    }

    protected RockyLinuxSetting(List<String> compatibleOSVersion) {
        super(OSName.ROCKY_LINUX, compatibleOSVersion);
    }

    /**
     * Kickstart {@code %post} 섹션 스크립트를 생성하여 반환한다.
     *
     * <p>생성 규칙:
     * <ul>
     *   <li>{@code selinuxMode}가 {@code "enforcing"}이면 {@code setenforce 1},
     *       {@code "permissive"}이면 {@code setenforce 0},
     *       {@code "disabled"}이면 {@code setenforce} 명령을 생략한다.</li>
     *   <li>{@code additionalPackages}가 비어 있으면 {@code dnf install} 섹션을 생략한다.</li>
     *   <li>{@code enabledServices}가 비어 있으면 {@code systemctl enable} 섹션을 생략한다.</li>
     * </ul>
     * </p>
     *
     * @param selinuxMode        SELinux 모드 ({@code "enforcing"}, {@code "permissive"}, {@code "disabled"})
     * @param enabledServices    활성화할 systemd 서비스 목록
     * @param additionalPackages 추가 설치할 패키지 목록
     * @return Kickstart {@code %post} 섹션 스크립트 문자열
     */
    public String getPostInstallScript(
            String selinuxMode,
            List<String> enabledServices,
            List<String> additionalPackages
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("%post\n");

        // SELinux 모드 설정
        if (selinuxMode != null && !selinuxMode.isBlank()) {
            sb.append("# SELinux 모드 설정\n");
            // setenforce 는 Kickstart %post(chroot 환경)에서 커널에 접근하지 못해 런타임 효과가 없다.
            // 실제 SELinux 모드 적용은 아래 sed 명령으로 /etc/selinux/config 를 수정하여
            // 다음 부팅 시 적용된다. setenforce 호출은 %post --nochroot 섹션에서만 유효하다.
            if ("enforcing".equalsIgnoreCase(selinuxMode)) {
                sb.append("setenforce 1\n");
            } else if ("permissive".equalsIgnoreCase(selinuxMode)) {
                sb.append("setenforce 0\n");
            }
            // "disabled" 모드는 런타임 전환 불가(setenforce 생략), config 파일 수정으로만 처리
            sb.append("sed -i 's/^SELINUX=.*/SELINUX=").append(selinuxMode.toLowerCase()).append("/' /etc/selinux/config\n");
            sb.append("\n");
        }

        // 추가 패키지 설치
        if (additionalPackages != null && !additionalPackages.isEmpty()) {
            sb.append("# 추가 패키지 설치\n");
            sb.append("dnf install -y");
            for (String pkg : additionalPackages) {
                sb.append(" ").append(pkg);
            }
            sb.append("\n\n");
        }

        // 서비스 활성화
        if (enabledServices != null && !enabledServices.isEmpty()) {
            sb.append("# 서비스 활성화\n");
            for (String service : enabledServices) {
                sb.append("systemctl enable ").append(service).append("\n");
            }
            sb.append("\n");
        }

        sb.append("%end\n");
        return sb.toString();
    }
}
