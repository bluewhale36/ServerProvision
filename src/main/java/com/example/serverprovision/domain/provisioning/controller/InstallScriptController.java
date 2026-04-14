package com.example.serverprovision.domain.provisioning.controller;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.os.model.installation.InstallScriptFormat;
import com.example.serverprovision.domain.os.model.installation.InstallationContext;
import com.example.serverprovision.domain.os.model.installation.RenderedScript;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OS 설치 자동화 스크립트를 HTTP 로 서빙하는 REST 컨트롤러이다.
 *
 * <p>역할: PXE 부팅 중인 물리 서버가 OS 설치를 시작할 때 이 엔드포인트를 호출하여
 * 완성된 설치 스크립트 (RHEL 계열 Kickstart / Ubuntu Subiquity autoinstall YAML /
 * Windows unattend.xml) 를 동적 Content-Type 으로 수신한다. 포맷은 도메인 계층
 * {@link RenderedScript#format()} 이 결정하며, 본 컨트롤러는 파일명·Content-Type 만
 * 포맷으로부터 파생시키고 본문은 그대로 통과시킨다.</p>
 *
 * <p>엔드포인트:
 * <ul>
 *   <li>{@code GET /pxe/v1/install/{nodeId}} — 주 설치 스크립트. 모든 포맷에 대해 기본 경로.
 *       RHEL Anaconda 는 {@code inst.ks=<url>} 으로, Ubuntu Subiquity 는
 *       {@code ds=nocloud-net;s=<url>/} 의 {@code user-data} 프로브로 사용.</li>
 *   <li>{@code GET /pxe/v1/install/{nodeId}/user-data} — cloud-init nocloud-net 데이터 소스가
 *       {@code user-data} 파일을 조회할 때 요구하는 경로. 주 엔드포인트와 동일 본문을 반환하되
 *       Content-Type 은 항상 YAML 로 한정된다.</li>
 *   <li>{@code GET /pxe/v1/install/{nodeId}/meta-data} — cloud-init nocloud-net 이 요구하는
 *       최소 메타데이터 ({@code instance-id}). 파일이 없으면 Subiquity 가 부팅을 멈추므로
 *       비어 있지 않은 본문을 반드시 제공해야 한다.</li>
 * </ul></p>
 *
 * <p>유스케이스: iPXE 스크립트가 OS 설치 단계에서 OS 패밀리별 커널 인자 —
 * RHEL: {@code inst.ks=http://<pxe-server>/pxe/v1/install/{nodeId}},
 * Ubuntu: {@code ds=nocloud-net;s=http://<pxe-server>/pxe/v1/install/{nodeId}/} —
 * 를 구성한다 ({@link com.example.serverprovision.domain.provisioning.model.strategy.OSInstallationStrategy}).</p>
 */
@Slf4j
@RestController
@RequestMapping("/pxe/v1/install")
@RequiredArgsConstructor
public class InstallScriptController {

    private final ServerNodeService serverNodeService;

    /**
     * PXE 서버의 OS 설치 소스 HTTP URL.
     * {@code application.properties} 의 {@code pxe.server.install-source-url} 또는
     * 환경변수 {@code PXE_INSTALL_SOURCE_URL} 로 주입한다. 기본값 빈 문자열이며
     * 미설정 시 500 반환 (설정 오류이므로 404 로 숨기지 않음).
     */
    @Value("${pxe.server.install-source-url:}")
    private String installSourceUrl;

    /**
     * 지정된 서버 노드의 설치 자동화 스크립트를 반환한다.
     *
     * <p>반환 Content-Type 은 {@link RenderedScript#format()} 이 제공하는
     * {@link InstallScriptFormat#getMediaType()} 로 결정된다. 추가로
     * {@code Content-Disposition: inline; filename=<defaultFileName>} 을 지정하여
     * Anaconda/Subiquity 가 로그·디버그 시 파일명을 인식할 수 있게 한다.</p>
     *
     * @param nodeId 조회할 서버 노드의 기본키
     * @return {@code 200 OK} + 포맷별 Content-Type + 스크립트 본문
     */
    @GetMapping("/{nodeId}")
    public ResponseEntity<String> getInstallScript(@PathVariable Long nodeId) {
        log.info("[InstallScriptController] 설치 스크립트 요청 수신. nodeId: {}", nodeId);

        ResolveResult result = resolveScript(nodeId);
        if (result.status() != HttpStatus.OK) {
            return ResponseEntity.status(result.status()).build();
        }

        RenderedScript rendered = result.rendered();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(rendered.format().getMediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + rendered.format().getDefaultFileName() + "\"")
                .body(rendered.content());
    }

    /**
     * cloud-init nocloud-net 데이터 소스가 {@code user-data} 파일을 조회할 때 사용하는 엔드포인트.
     *
     * <p>Subiquity autoinstall 은 nocloud-net URL 에 trailing slash 를 붙여
     * {@code <url>/user-data} 와 {@code <url>/meta-data} 를 자동으로 조회한다.
     * 본 엔드포인트는 주 엔드포인트와 동일 본문을 반환하되, Content-Type 은 항상 YAML 로 한정된다.</p>
     */
    @GetMapping("/{nodeId}/user-data")
    public ResponseEntity<String> getUserData(@PathVariable Long nodeId) {
        log.info("[InstallScriptController] user-data 요청 수신 (nocloud-net). nodeId: {}", nodeId);

        ResolveResult result = resolveScript(nodeId);
        if (result.status() != HttpStatus.OK) {
            return ResponseEntity.status(result.status()).build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(InstallScriptFormat.AUTOINSTALL_YAML.getMediaType()))
                .body(result.rendered().content());
    }

    /**
     * cloud-init nocloud-net 데이터 소스가 {@code meta-data} 파일을 조회할 때 사용하는 엔드포인트.
     *
     * <p>Subiquity 는 이 파일이 존재하지 않으면 데이터 소스 초기화에 실패한다. 본 구현은
     * 최소한의 {@code instance-id} 만 포함한 YAML 을 반환한다.</p>
     */
    @GetMapping("/{nodeId}/meta-data")
    public ResponseEntity<String> getMetaData(@PathVariable Long nodeId) {
        log.info("[InstallScriptController] meta-data 요청 수신 (nocloud-net). nodeId: {}", nodeId);

        ServerNode node = serverNodeService.findNodeById(nodeId).orElse(null);
        if (node == null) {
            log.warn("[InstallScriptController] meta-data 요청 실패 — 서버 노드를 찾을 수 없습니다. nodeId: {}", nodeId);
            return ResponseEntity.notFound().build();
        }

        // nocloud-net 이 요구하는 최소 필드는 instance-id 뿐.
        // 노드 ID + hostname 조합으로 인스턴스 고유 식별자 구성.
        String hostname = node.getHostname() != null ? node.getHostname() : ("node-" + nodeId);
        String body = "instance-id: " + hostname + "-" + nodeId + "\n"
                + "local-hostname: " + hostname + "\n";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(InstallScriptFormat.AUTOINSTALL_YAML.getMediaType()))
                .body(body);
    }

    /**
     * 공통 조회 로직 — 노드 → ServerSetting → SettingProcess → OSInstallation 체인을
     * 따라가며 설치 스크립트를 생성한다. 실패 원인별로 HTTP 상태를 구분:
     * <ul>
     *   <li>{@code 404}: 노드·세팅·프로세스·OSInstallation 누락 (클라이언트 조회 미스)</li>
     *   <li>{@code 500}: {@code installSourceUrl} 미설정 (서버 설정 오류)</li>
     *   <li>{@code 200}: 스크립트 생성 성공</li>
     * </ul>
     */
    private ResolveResult resolveScript(Long nodeId) {
        ServerNode node = serverNodeService.findNodeById(nodeId).orElse(null);
        if (node == null) {
            log.warn("[InstallScriptController] 서버 노드를 찾을 수 없습니다. nodeId: {}", nodeId);
            return ResolveResult.notFound();
        }

        if (node.getServerSetting() == null) {
            log.warn("[InstallScriptController] 세팅 주문서가 할당되지 않았습니다. nodeId: {}", nodeId);
            return ResolveResult.notFound();
        }

        SettingProcess settingProcess = node.getServerSetting().getSettingProcess();
        if (settingProcess == null || settingProcess.processList() == null) {
            log.warn("[InstallScriptController] 프로세스 목록이 비어 있습니다. nodeId: {}", nodeId);
            return ResolveResult.notFound();
        }

        List<AbstractSettingProcess> processes = settingProcess.processList();
        com.example.serverprovision.application.setting.model.OSInstallation osInstallationProcess =
                processes.stream()
                        .filter(p -> p instanceof com.example.serverprovision.application.setting.model.OSInstallation)
                        .map(p -> (com.example.serverprovision.application.setting.model.OSInstallation) p)
                        .findFirst()
                        .orElse(null);

        if (osInstallationProcess == null) {
            log.warn("[InstallScriptController] OS 설치 프로세스가 없습니다. nodeId: {}", nodeId);
            return ResolveResult.notFound();
        }

        // installSourceUrl 은 HTTP URL 이어야 한다 — 로컬 경로(isoMountPath) 폴백은 의미 없음.
        // 설정 누락은 서버 운영자 개입이 필요하므로 500 으로 구분한다.
        if (installSourceUrl == null || installSourceUrl.isBlank()) {
            log.error("[InstallScriptController] pxe.server.install-source-url 이 설정되지 않았습니다. "
                    + "application.properties 또는 환경변수 PXE_INSTALL_SOURCE_URL 을 확인하세요. nodeId: {}", nodeId);
            return ResolveResult.serverError();
        }

        InstallationContext ctx = new InstallationContext(
                node.getHostname(),
                node.getAssignedIp(),
                installSourceUrl
        );

        RenderedScript rendered = osInstallationProcess.getOsInstallation().getInstallScript(ctx);
        log.info("[InstallScriptController] 설치 스크립트 생성 완료. nodeId: {}, hostname: {}, format: {}",
                nodeId, ctx.hostname(), rendered.format());
        return ResolveResult.ok(rendered);
    }

    /**
     * 내부 조회 결과 — 성공 시 {@link RenderedScript} 와 {@code 200},
     * 실패 시 대응 HTTP 상태를 함께 반환하기 위한 값 객체.
     */
    private record ResolveResult(HttpStatus status, RenderedScript rendered) {
        static ResolveResult ok(RenderedScript rendered)  { return new ResolveResult(HttpStatus.OK, rendered); }
        static ResolveResult notFound()                   { return new ResolveResult(HttpStatus.NOT_FOUND, null); }
        static ResolveResult serverError()                { return new ResolveResult(HttpStatus.INTERNAL_SERVER_ERROR, null); }
    }
}
