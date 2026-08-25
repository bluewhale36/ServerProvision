package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 펌웨어 파일을 BMC 에게 내주기 위한 일회용 URL 발급소(E2-2 D-5).
 *
 * <p>BMC 가 {@code ImageURI} 로 파일을 당겨 가려면 그 파일이 HTTP 로 열려 있어야 한다. 그런데 자원 파일은
 * 관리 영역의 저장 경로에 있고 그 트리에는 다른 자원도 함께 있으므로, 무인증 정적 서빙을 열면
 * <b>자원 트리 전체가 열람 대상</b>이 된다. 반대로 Redfish 의 {@code User} · {@code Password} 파라미터로
 * 인증을 걸면 <b>우리 자격증명을 BMC 에게 넘겨야</b> 한다. 일회용 토큰 URL 이 양쪽을 피한다 —
 * 게스트 토큰이 같은 방식으로 게스트를 인증하는 선례다.</p>
 *
 * <p>토큰의 수명은 집행 한 건과 같아 영속할 이유가 없다. 서버가 재기동되면 진행 중이던 집행은 어차피
 * 원장에서 복원되며(D-4), 그때 새 토큰을 발급한다.</p>
 */
@Slf4j
@Component
public class FirmwareImageTokenRegistry {

    private final Map<UUID, Path> issued = new ConcurrentHashMap<>();
    /** (게스트, 축) → 지금 유효한 토큰. 굽기가 끝나면 이 키로 회수한다 — 토큰만으로는 누구 것인지 모른다. */
    private final Map<String, UUID> byAxis = new ConcurrentHashMap<>();
    private final String baseUrl;

    public FirmwareImageTokenRegistry(@Value("${pxe.server.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /** 이 축의 굽기에 쓸 토큰을 발급한다. 같은 축을 다시 구우면 <b>앞의 토큰은 그 자리에서 죽는다.</b> */
    public UUID issue(UUID guestServerId, FirmwareAxis axis, Path imagePath) {
        UUID token = UUID.randomUUID();
        issued.put(token, imagePath);
        UUID previous = byAxis.put(key(guestServerId, axis), token);
        if (previous != null) {
            issued.remove(previous);   // 재시도로 다시 구울 때 옛 URL 이 살아 있지 않게
        }
        log.info("[flash] {} — {} 이미지 토큰 발급", guestServerId, axis.label());
        return token;
    }

    /**
     * 그 축의 굽기가 끝났다 — 토큰을 회수한다(CP5 F-3). URL 은 Redfish 요청 · BMC 로그 · 우리 접근
     * 로그에 평문으로 남으므로, 필요가 끝난 뒤에도 살아 있으면 그 파일이 계속 열려 있는 셈이다.
     */
    public void revoke(UUID guestServerId, FirmwareAxis axis) {
        UUID token = byAxis.remove(key(guestServerId, axis));
        if (token != null) {
            issued.remove(token);
            log.info("[flash] {} — {} 이미지 토큰 회수", guestServerId, axis.label());
        }
    }

    private static String key(UUID guestServerId, FirmwareAxis axis) {
        return guestServerId + ":" + axis.name();
    }

    /** 토큰이 가리키는 파일 — 없거나 이미 회수됐으면 비어 있다(위조 · 만료를 같은 응답으로 다룬다). */
    public Optional<Path> resolve(UUID token) {
        return Optional.ofNullable(issued.get(token));
    }

    /** 그 축의 집행이 끝나면 회수한다 — 파일이 필요 이상으로 열려 있지 않게. */
    public void revoke(UUID token) {
        issued.remove(token);
    }

    /**
     * BMC 가 당겨 갈 절대 URL — 끝에 실제 파일명을 붙인다. AMI BMC 는 URI 의 확장자로 이미지 형식을
     * 판별해, 없으면 다운로드 없이 Task 를 실패로 닫는다(2026-08-25 실기). 인증 · 조회는 토큰만 쓴다.
     */
    public String urlFor(UUID token) {
        Path path = issued.get(token);
        if (path == null) {
            // 미발급 · 회수된 토큰의 URL 요청 = 집행 흐름 버그 — 임의 파일명 흡수는 BMC 형식 오판(silent)이 된다.
            throw new IllegalStateException("발급되지 않았거나 회수된 토큰의 URL 요청. token=" + token);
        }
        String encoded = java.net.URLEncoder.encode(path.getFileName().toString(),
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return baseUrl + "/api/pxe/v1/firmware/" + token + "/" + encoded;
    }
}
