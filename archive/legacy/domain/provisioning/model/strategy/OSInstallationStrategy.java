package com.example.serverprovision.domain.provisioning.model.strategy;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.OSInstallation;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.model.enums.OSFamily;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OS 설치 단계의 iPXE 스크립트를 생성하는 {@link ProvisioningStrategy} 구현체이다.
 *
 * <p>역할: {@link OSInstallation} 타입의 세팅 프로세스를 지원하며, OS 패밀리
 * ({@link OSFamily}) 에 따라 커널·initrd 경로와 autoinstall 파라미터를 분기한다.</p>
 *
 * <ul>
 *   <li>{@link OSFamily#RHEL_BASED}: {@code images/pxeboot/vmlinuz} + {@code inst.ks=<url>} —
 *       Anaconda 가 Kickstart URL 을 통해 설치 구성을 수신한다.</li>
 *   <li>{@link OSFamily#DEBIAN_BASED}: {@code casper/vmlinuz} + {@code autoinstall "ds=nocloud-net;s=<url>/"} —
 *       Subiquity 가 nocloud-net 데이터 소스에서 user-data/meta-data 를 조회한다.
 *       URL 말미의 {@code /} 는 nocloud-net 스펙 필수 사항 (디렉터리 인식).</li>
 *   <li>{@link OSFamily#WINDOWS_BASED}: 미지원 — {@link UnsupportedOperationException} 을 던진다.
 *       (향후 unattend.xml + wimboot 기반 구현 예정.)</li>
 * </ul>
 *
 * <p>유스케이스: {@link com.example.serverprovision.domain.provisioning.service.ProvisioningScriptService}
 * 가 {@code List<ProvisioningStrategy>} 에서 이 구현체를 찾아 {@link #generateIPXEScript} 를 호출한다.
 * 실제 스크립트 본문은 {@link com.example.serverprovision.domain.provisioning.controller.InstallScriptController}
 * 가 별도 HTTP 엔드포인트 ({@code /pxe/v1/install/{nodeId}}) 에서 서빙한다.</p>
 */
@Slf4j
@Component
public class OSInstallationStrategy implements ProvisioningStrategy {

    /**
     * PXE 서버 자체의 HTTP base URL (예: http://192.168.1.100:7777).
     * 설치 스크립트 URL 구성 시 사용된다.
     */
    @Value("${pxe.server.base-url:}")
    private String serverBaseUrl;

    /**
     * OS 설치 소스 HTTP URL (예: http://192.168.1.1/rocky9, http://192.168.1.1/ubuntu2204).
     * 커널·initrd 파일을 서빙하는 HTTP 마운트 포인트로, 패밀리별 하위 경로 규약이 다르다.
     */
    @Value("${pxe.server.install-source-url:}")
    private String installSourceUrl;

    /**
     * {@link OSInstallation} 타입의 프로세스만 지원한다.
     */
    @Override
    public boolean supports(AbstractSettingProcess process) {
        return process instanceof OSInstallation;
    }

    /**
     * OS 설치를 위한 iPXE 스크립트를 생성한다. 패밀리별 커널 인자 규약에 따라 분기한다.
     *
     * @param node    대상 서버 노드 ({@link ServerNode#getId()} 로 설치 스크립트 URL 구성)
     * @param process OS 설치 프로세스 ({@link OSInstallation} 으로 캐스팅됨)
     * @return iPXE 스크립트 문자열
     * @throws IllegalStateException         {@code pxe.server.base-url} 또는
     *                                       {@code pxe.server.install-source-url} 미설정 시
     * @throws UnsupportedOperationException Windows 계열 OS 선택 시 (현재 미구현)
     */
    @Override
    public String generateIPXEScript(ServerNode node, AbstractSettingProcess process) {
        OSInstallation osInstallation = (OSInstallation) process;
        OSMetadataDTO osMetadata = osInstallation.getOsMetadata();

        // 필수 설정값 검증 — 패밀리 분기와 무관하게 공통 선행 조건.
        if (serverBaseUrl == null || serverBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "pxe.server.base-url 이 설정되지 않았습니다. " +
                    "application.properties 또는 환경변수 PXE_SERVER_BASE_URL 을 확인하세요.");
        }
        if (installSourceUrl == null || installSourceUrl.isBlank()) {
            throw new IllegalStateException(
                    "pxe.server.install-source-url 이 설정되지 않았습니다. " +
                    "application.properties 또는 환경변수 PXE_INSTALL_SOURCE_URL 을 확인하세요.");
        }

        OSFamily family = osMetadata.osName().getFamily();
        String base = stripTrailingSlash(serverBaseUrl);
        String scriptUrl = base + "/pxe/v1/install/" + node.getId();

        log.info("[OSInstallationStrategy] iPXE 스크립트 생성. nodeId={}, os={} {}, family={}, scriptUrl={}",
                node.getId(), osMetadata.osName(), osMetadata.osVersion(), family, scriptUrl);

        return switch (family) {
            case RHEL_BASED     -> buildRhelScript(scriptUrl);
            case DEBIAN_BASED   -> buildUbuntuScript(scriptUrl);
            case WINDOWS_BASED  -> throw new UnsupportedOperationException(
                    "Windows 계열 OS (" + osMetadata.osName() + ") 의 PXE 자동 설치는 아직 구현되지 않았습니다. " +
                    "Phase 10 이후 unattend.xml + wimboot 기반으로 구현 예정.");
        };
    }

    /**
     * RHEL 계열 (Rocky/CentOS) iPXE 스크립트 — Anaconda Kickstart.
     *
     * <p>커널 인자 규약:
     * <ul>
     *   <li>{@code inst.repo=${base-url}} — 설치 소스 리포지토리 (installSourceUrl)</li>
     *   <li>{@code inst.ks=<scriptUrl>} — Kickstart 파일 HTTP URL</li>
     *   <li>{@code ip=dhcp} — 설치 중 네트워크는 DHCP 로 획득 (고정 IP 는 Kickstart 내 network 지시자로 제어)</li>
     * </ul></p>
     */
    private String buildRhelScript(String scriptUrl) {
        // iPXE 스크립트에서 ${base-url}은 iPXE 변수 참조이므로 Java 문자열에서 이스케이프하지 않는다.
        return """
               #!ipxe
               set base-url %s
               kernel ${base-url}/images/pxeboot/vmlinuz inst.repo=${base-url} inst.ks=%s ip=dhcp
               initrd ${base-url}/images/pxeboot/initrd.img
               boot
               """.formatted(installSourceUrl, scriptUrl);
    }

    /**
     * Ubuntu 22.04 계열 iPXE 스크립트 — Subiquity autoinstall (nocloud-net).
     *
     * <p>커널 인자 규약:
     * <ul>
     *   <li>{@code casper/vmlinuz} + {@code casper/initrd} — Ubuntu live server 커널/initramfs 경로</li>
     *   <li>{@code autoinstall} — Subiquity 의 비대화형 설치 모드 활성화</li>
     *   <li>{@code ds=nocloud-net;s=<scriptUrl>/} — cloud-init 데이터 소스 ({@code /} 필수).
     *       {@code s=} 뒤 URL 은 디렉터리여야 하며, 이 경로 아래에서
     *       {@code user-data}, {@code meta-data} 파일을 자동 조회한다.</li>
     *   <li>{@code ---} — Subiquity 가 이후 인자는 커널이 아닌 OS 런타임으로 전달하도록 하는 구분자</li>
     * </ul></p>
     */
    private String buildUbuntuScript(String scriptUrl) {
        // scriptUrl 끝에 명시적으로 '/' 를 붙여 nocloud-net 이 디렉터리로 인식하도록 함.
        // "ds=nocloud-net;s=..." 는 세미콜론이 iPXE 명령 구분자로 오인되지 않게 쌍따옴표로 감싼다.
        return """
               #!ipxe
               set base-url %s
               kernel ${base-url}/casper/vmlinuz autoinstall "ds=nocloud-net;s=%s/" ip=dhcp ---
               initrd ${base-url}/casper/initrd
               boot
               """.formatted(installSourceUrl, scriptUrl);
    }

    /**
     * URL 의 trailing slash 를 제거한다. 이중 슬래시 방지용 헬퍼.
     */
    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
