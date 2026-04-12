package com.example.serverprovision.domain.provisioning.model.strategy;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.OSInstallation;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OS 설치 단계의 iPXE 스크립트를 생성하는 {@link ProvisioningStrategy} 구현체이다.
 *
 * <p>역할: {@link OSInstallation} 타입의 세팅 프로세스를 지원하며,
 * 커널(vmlinuz) + initrd + Kickstart URL을 포함한 iPXE 스크립트를 생성한다.
 * PXE 부팅 중인 물리 서버는 이 스크립트를 받아 OS 설치를 자동으로 시작한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.domain.provisioning.service.ProvisioningScriptService}가
 * {@code List<ProvisioningStrategy>}에서 이 구현체를 찾아 {@code generateIPXEScript}를 호출한다.</p>
 */
@Slf4j
@Component
public class OSInstallationStrategy implements ProvisioningStrategy {

    /**
     * PXE 서버 자체의 HTTP base URL (예: http://192.168.1.100:7777).
     * Kickstart URL 구성 시 사용된다.
     */
    @Value("${pxe.server.base-url:}")
    private String serverBaseUrl;

    /**
     * OS 설치 소스 HTTP URL (예: http://192.168.1.1/rocky9).
     * serverBaseUrl 미설정 시 폴백으로 사용되지 않으며, 커널/initrd 경로 구성에 사용된다.
     */
    @Value("${pxe.server.install-source-url:}")
    private String installSourceUrl;

    /**
     * {@link OSInstallation} 타입의 프로세스만 지원한다.
     *
     * @param process 검사할 세팅 프로세스
     * @return {@code true}이면 이 전략이 해당 프로세스를 처리할 수 있음
     */
    @Override
    public boolean supports(AbstractSettingProcess process) {
        return process instanceof OSInstallation;
    }

    /**
     * OS 설치를 위한 iPXE 스크립트를 생성한다.
     *
     * <p>생성되는 스크립트 구조:</p>
     * <pre>
     * #!ipxe
     * set base-url &lt;installSourceUrl&gt;
     * kernel ${base-url}/images/pxeboot/vmlinuz inst.repo=${base-url} inst.ks=http://&lt;serverBaseUrl&gt;/pxe/v1/ks/${nodeId} ip=dhcp
     * initrd ${base-url}/images/pxeboot/initrd.img
     * boot
     * </pre>
     *
     * @param node    대상 서버 노드
     * @param process OS 설치 프로세스 ({@link OSInstallation}으로 캐스팅됨)
     * @return iPXE 스크립트 문자열
     * @throws IllegalStateException pxe.server.base-url 또는 pxe.server.install-source-url이 미설정된 경우
     */
    @Override
    public String generateIPXEScript(ServerNode node, AbstractSettingProcess process) {
        OSInstallation osInstallation = (OSInstallation) process;
        OSMetadataDTO osMetadata = osInstallation.getOsMetadata();

        // 필수 설정값 검증
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

        String base = serverBaseUrl.endsWith("/")
                ? serverBaseUrl.substring(0, serverBaseUrl.length() - 1)
                : serverBaseUrl;
        String kickstartUrl = base + "/pxe/v1/ks/" + node.getId();

        log.info("[OSInstallationStrategy] iPXE 스크립트 생성. nodeId={}, os={} {}, ksUrl={}",
                node.getId(), osMetadata.osName(), osMetadata.osVersion(), kickstartUrl);

        // iPXE 스크립트에서 ${base-url}은 iPXE 변수 참조이므로 Java 문자열에서 이스케이프하지 않는다.
        return """
               #!ipxe
               set base-url %s
               kernel ${base-url}/images/pxeboot/vmlinuz inst.repo=${base-url} inst.ks=%s ip=dhcp
               initrd ${base-url}/images/pxeboot/initrd.img
               boot
               """.formatted(installSourceUrl, kickstartUrl);
    }
}
