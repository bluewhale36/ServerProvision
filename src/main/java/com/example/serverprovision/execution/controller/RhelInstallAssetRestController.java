package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.engine.rhel.RhelInstaller;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTokenRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * RHEL 설치 서빙(E4-1-b-4-2) — 설치 프로그램(anaconda)이 kickstart 와 설치 트리를 당겨 가는 자리. 무효 토큰은 게스트 체인이
 * 401 로 끝내므로 여기 오는 토큰은 발급된 것이다. kickstart 는 설치가 진행 중(원장 RUNNING)일 때만 낸다 — 실패로 닫힌 서버의
 * 비밀번호 해시가 계속 열려 있지 않게. 트리 경로는 {@code InstallTree.resolve} 로만 파일에 대응시키므로 {@code ..} · 숨김 파일 ·
 * 트리 밖 링크는 404 다. 파일 응답 조립은 {@link ServedFileResponse} 다.
 */
@Slf4j
@RestController
@RequestMapping("/api/pxe/v1/rhel")
@RequiredArgsConstructor
public class RhelInstallAssetRestController {

    private final RhelInstallTokenRegistry tokenRegistry;
    private final RhelInstaller installer;

    @GetMapping("/{token}/ks.cfg")
    public ResponseEntity<Resource> kickstart(@PathVariable("token") UUID token) {
        return installer.kickstartWhileInstalling(token)
                .<ResponseEntity<Resource>>map(kickstart -> {
                    log.info("[rhelinstall] kickstart 서빙 : token={}", token);
                    byte[] bytes = kickstart.text().getBytes(StandardCharsets.UTF_8);
                    return ResponseEntity.ok()
                            .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                            .contentLength(bytes.length)
                            .body(new ByteArrayResource(bytes));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{token}/tree/{*path}")
    public ResponseEntity<Resource> tree(@PathVariable("token") UUID token, @PathVariable("path") String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        return tokenRegistry.resolve(token)
                .flatMap(serving -> serving.tree().resolve(relative))
                .map(file -> {
                    log.debug("[rhelinstall] 트리 서빙 : token={}, path={}", token, relative);
                    return ServedFileResponse.of(file);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
