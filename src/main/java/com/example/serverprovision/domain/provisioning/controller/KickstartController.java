package com.example.serverprovision.domain.provisioning.controller;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.os.model.installation.KickstartContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Kickstart 스크립트를 HTTP로 서빙하는 REST 컨트롤러이다.
 *
 * <p>역할: PXE 부팅 중인 물리 서버가 OS 설치를 시작할 때 이 엔드포인트를 호출하여
 * 완성된 Kickstart 9 파일 내용을 {@code text/plain}으로 수신한다.
 * {@code nodeId}로 {@link ServerNode}를 조회하고, 할당된 세팅 주문서의 프로세스 목록에서
 * {@link com.example.serverprovision.application.setting.model.OSInstallation} 타입을 찾아
 * Kickstart 스크립트를 동적으로 생성하여 반환한다.</p>
 *
 * <p>유스케이스: iPXE 스크립트가 OS 설치 단계에서
 * {@code http://<pxe-server>/pxe/v1/ks/{nodeId}} 를 ks URL 로 지정하면,
 * Anaconda(설치 프로그램)가 이 URL 을 호출하여 Kickstart 파일을 내려받는다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/pxe/v1/ks")
@RequiredArgsConstructor
public class KickstartController {

    private final ServerNodeService serverNodeService;

    /**
     * PXE 서버의 OS 설치 소스 HTTP URL.
     * {@code application.properties}에 {@code pxe.server.install-source-url}로 설정하거나
     * 환경변수 {@code PXE_SERVER_INSTALL_SOURCE_URL}로 주입한다.
     * 기본값은 빈 문자열이며, 미설정 시 로그 경고가 발생한다.
     */
    @Value("${pxe.server.install-source-url:}")
    private String installSourceUrl;

    /**
     * 지정된 서버 노드의 Kickstart 스크립트를 반환한다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>{@code nodeId}로 {@link ServerNode} 조회 — 없으면 404 반환</li>
     *   <li>할당된 {@code ServerSetting}의 프로세스 목록에서
     *       {@link com.example.serverprovision.application.setting.model.OSInstallation} 타입 탐색 — 없으면 404</li>
     *   <li>{@link KickstartContext} 빌드 (hostname, assignedIp, installSourceUrl)</li>
     *   <li>도메인 계층 {@code osInstallation.getKickstartScript(ctx)} 호출 후 반환</li>
     * </ol>
     *
     * @param nodeId 조회할 서버 노드의 기본키
     * @return {@code 200 OK} + Kickstart 파일 본문, 또는 {@code 404 Not Found}
     */
    @GetMapping(value = "/{nodeId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getKickstartScript(@PathVariable Long nodeId) {

        log.info("Kickstart 스크립트 요청 수신. nodeId: {}", nodeId);

        // 1. 서버 노드 조회 — Optional 반환으로 광범위한 RuntimeException catch 없이 안전하게 처리
        ServerNode node = serverNodeService.findNodeById(nodeId).orElse(null);
        if (node == null) {
            log.warn("Kickstart 요청 실패 — 서버 노드를 찾을 수 없습니다. nodeId: {}", nodeId);
            return ResponseEntity.notFound().build();
        }

        // 2. 할당된 세팅 주문서 및 프로세스 목록 확인
        if (node.getServerSetting() == null) {
            log.warn("Kickstart 요청 실패 — 세팅 주문서가 할당되지 않았습니다. nodeId: {}", nodeId);
            return ResponseEntity.notFound().build();
        }

        SettingProcess settingProcess = node.getServerSetting().getSettingProcess();
        if (settingProcess == null || settingProcess.processList() == null) {
            log.warn("Kickstart 요청 실패 — 프로세스 목록이 비어 있습니다. nodeId: {}", nodeId);
            return ResponseEntity.notFound().build();
        }

        // 3. OSInstallation 타입 프로세스 탐색 (application 계층 래퍼)
        List<AbstractSettingProcess> processes = settingProcess.processList();
        com.example.serverprovision.application.setting.model.OSInstallation osInstallationProcess =
                processes.stream()
                        .filter(p -> p instanceof com.example.serverprovision.application.setting.model.OSInstallation)
                        .map(p -> (com.example.serverprovision.application.setting.model.OSInstallation) p)
                        .findFirst()
                        .orElse(null);

        if (osInstallationProcess == null) {
            log.warn("Kickstart 요청 실패 — OS 설치 프로세스가 없습니다. nodeId: {}", nodeId);
            return ResponseEntity.notFound().build();
        }

        // 4. KickstartContext 빌드
        // installSourceUrl 은 HTTP URL 이어야 한다. isoMountPath() 는 로컬 경로이므로
        // 폴백으로 사용하면 원격 서버에서 접근 불가 — 미설정 시 명확한 500 반환
        if (installSourceUrl == null || installSourceUrl.isBlank()) {
            log.error("Kickstart 요청 실패 — pxe.server.install-source-url 이 설정되지 않았습니다. " +
                    "application.properties 또는 환경변수 PXE_INSTALL_SOURCE_URL 을 확인하세요. nodeId: {}", nodeId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        KickstartContext ctx = new KickstartContext(
                node.getHostname(),
                node.getAssignedIp(),
                installSourceUrl
        );

        // 5. 도메인 계층의 Kickstart 스크립트 생성
        String script = osInstallationProcess.getOsInstallation().getKickstartScript(ctx);

        log.info("Kickstart 스크립트 생성 완료. nodeId: {}, hostname: {}", nodeId, ctx.hostname());

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(script);
    }
}
