package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.engine.windows.WindowsInstallBundle;
import com.example.serverprovision.execution.engine.windows.WindowsInstallFile;
import com.example.serverprovision.execution.engine.windows.WindowsInstallTokenRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.util.Optional;
import java.util.UUID;

/**
 * WinPE 부팅 번들 서빙(E4-1-a-3 D-3) — iPXE 가 wimboot 체인의 {@code kernel} · {@code initrd} 로 당겨 가는 자리.
 * 파일명은 {@link WindowsInstallFile} enum 으로만 매칭하므로 경로 조작이 성립하지 않고, 미발급 · 회수 토큰 · 목록 밖
 * 파일명은 모두 404 다({@code FirmwareImageRestController} 관례 — 존재 여부를 응답으로 흘리지 않는다).
 * boot.wim(수백 MB)은 {@link FileSystemResource} 로 스트리밍하며 Range 는 Spring 이 처리한다. 접근 로그에는 토큰과 파일명만 남긴다.
 */
@Slf4j
@RestController
@RequestMapping("/api/pxe/v1/windows")
@RequiredArgsConstructor
public class WindowsInstallAssetRestController {

    private final WindowsInstallTokenRegistry tokenRegistry;

    @GetMapping("/{token}/{fileName}")
    public ResponseEntity<Resource> serve(@PathVariable("token") UUID token, @PathVariable("fileName") String fileName) {
        Optional<WindowsInstallFile> file = WindowsInstallFile.of(fileName);
        Optional<WindowsInstallBundle> bundle = tokenRegistry.resolve(token);
        if (file.isEmpty() || bundle.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        log.debug("[wininstall] 번들 서빙 : token={}, file={}", token, fileName);
        WindowsInstallFile f = file.get();
        if (f.streamed()) {
            return bundle.get().pathOf(f)
                    .filter(Files::isRegularFile)
                    .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                            .contentType(f.mediaType())
                            .body(new FileSystemResource(path)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        return bundle.get().textOf(f)
                .<ResponseEntity<Resource>>map(text -> {
                    byte[] bytes = text.getBytes(f.charset());
                    return ResponseEntity.ok()
                            .contentType(f.mediaType())
                            .contentLength(bytes.length)
                            .body(new ByteArrayResource(bytes));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
