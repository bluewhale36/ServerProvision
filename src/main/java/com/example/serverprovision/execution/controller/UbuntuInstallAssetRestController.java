package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.engine.ubuntu.UbuntuInstallTokenRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.util.UUID;

/**
 * Ubuntu 설치 서빙. iPXE 가 커널 · initrd 를, casper 가 ISO 를 당겨 가는 자리다. 무효 토큰은 게스트 체인이 401 로 끝낸다.
 * 트리 경로는 {@code InstallTree.resolve} 로만 파일에 대응시키므로 {@code ..} · 숨김 파일 · 트리 밖 링크는 404 다.
 * ISO 는 등록 원본을 그대로 내고, 그 파일이 사라졌으면 404 다.
 */
@Slf4j
@RestController
@RequestMapping("/api/pxe/v1/ubuntu")
@RequiredArgsConstructor
public class UbuntuInstallAssetRestController {

    private final UbuntuInstallTokenRegistry tokenRegistry;

    @GetMapping("/{token}/tree/{*path}")
    public ResponseEntity<Resource> tree(@PathVariable("token") UUID token, @PathVariable("path") String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        return tokenRegistry.resolve(token)
                .flatMap(serving -> serving.tree().resolve(relative))
                .map(file -> {
                    log.debug("[ubuntuinstall] 트리 서빙 : token={}, path={}", token, relative);
                    return ServedFileResponse.of(file);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{token}/install.iso")
    public ResponseEntity<Resource> iso(@PathVariable("token") UUID token) {
        return tokenRegistry.resolve(token)
                .map(serving -> serving.tree().source())
                .filter(Files::isRegularFile)
                .map(file -> {
                    log.info("[ubuntuinstall] ISO 서빙 : token={}", token);
                    return ServedFileResponse.of(file);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
