package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.service.FirmwareImageTokenRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 펌웨어 파일 서빙(E2-2 D-5) — BMC 가 {@code ImageURI} 로 당겨 가는 자리다.
 *
 * <p>부르는 쪽이 게스트가 아니라 그 게스트의 BMC 라는 점이 다른 PXE 엔드포인트와 구별되는 성질이다.
 * BMC 는 우리 인증 체계를 모르므로 자격증명을 요구할 수 없고, 그렇다고 자원 트리를 무인증으로 열 수도
 * 없다. 그래서 <b>집행 한 건마다 발급하는 일회용 토큰</b>이 인증을 대신한다
 * ({@link FirmwareImageTokenRegistry}).</p>
 *
 * <p>토큰이 없거나 이미 회수됐으면 404 다 — 위조와 만료를 같은 응답으로 다루는 것이 기존 관례이며,
 * 존재 여부를 응답으로 흘리지 않는다.</p>
 */
@RestController
@RequestMapping("/api/pxe/v1/firmware")
@RequiredArgsConstructor
public class FirmwareImageRestController {

    private final FirmwareImageTokenRegistry tokenRegistry;

    /** 토큰이 가리키는 펌웨어 파일. 64 MB 급이라 전송 중 연결이 끊길 수 있고, 그때는 BMC 가 다시 당긴다. */
    @GetMapping("/{token}")
    public ResponseEntity<Resource> download(@PathVariable("token") UUID token) {
        return tokenRegistry.resolve(token)
                .filter(java.nio.file.Files::isRegularFile)
                .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
