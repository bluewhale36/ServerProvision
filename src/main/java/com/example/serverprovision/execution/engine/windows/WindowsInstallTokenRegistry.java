package com.example.serverprovision.execution.engine.windows;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WinPE 부팅 번들의 일회용 URL 발급소(E4-1-a-3 D-3) — {@code FirmwareImageTokenRegistry} 와 같은 결.
 * 렌더본에 비밀값이 실리므로 정적 서빙을 열 수 없고, 게스트 토큰은 수명이 길어 URL · 접근 로그에 남기기에
 * 무겁다. 게스트당 토큰 하나이며 재발급하면 앞의 토큰은 그 자리에서 죽는다(재시도 뒤 옛 URL 이 살아 있지 않게).
 * 서버 재기동으로 사라지는 것은 의도다 — 다음 폴링이 새 토큰으로 다시 서빙한다.
 */
@Slf4j
@Component
public class WindowsInstallTokenRegistry {

    private final Map<UUID, WindowsInstallBundle> issued = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byGuest = new ConcurrentHashMap<>();
    /** 토큰 → 게스트 — 게스트 체인이 서빙 토큰으로 신원을 세울 때 쓴다(S19-1 D-3). */
    private final Map<UUID, UUID> guestByToken = new ConcurrentHashMap<>();
    private final String baseUrl;

    public WindowsInstallTokenRegistry(@Value("${pxe.server.base-url:}") String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public UUID issue(UUID guestServerId, WindowsInstallBundle bundle) {
        return issue(guestServerId, UUID.randomUUID(), bundle);
    }

    /** 미리 뽑은 토큰으로 발급 — 번들 안의 autounattend 가 자기 토큰 URL(드라이버 목록)을 품어야 해서(R15-2 D-3). */
    public UUID issue(UUID guestServerId, UUID token, WindowsInstallBundle bundle) {
        issued.put(token, bundle);
        guestByToken.put(token, guestServerId);
        UUID previous = byGuest.put(guestServerId, token);
        if (previous != null) {
            issued.remove(previous);
            guestByToken.remove(previous);
        }
        // 토큰 값만 남긴다(접근 로그의 token 세그먼트와 대조용) — 번들 내용은 비밀값이라 싣지 않는다.
        log.info("[wininstall] {} — 설치 번들 토큰 발급 : token={}", guestServerId, token);
        return token;
    }

    /** 토큰이 가리키는 번들 — 없거나 회수됐으면 empty(위조 · 만료를 같은 응답으로 다룬다). */
    public Optional<WindowsInstallBundle> resolve(UUID token) {
        return Optional.ofNullable(issued.get(token));
    }

    public void revoke(UUID guestServerId) {
        UUID token = byGuest.remove(guestServerId);
        if (token != null) {
            issued.remove(token);
            guestByToken.remove(token);
            log.info("[wininstall] {} — 설치 번들 토큰 회수", guestServerId);
        }
    }

    /** 토큰을 발급받은 게스트 — 없거나 회수됐으면 empty(게스트 체인의 서빙 토큰 인증 재료). */
    public Optional<UUID> guestOf(UUID token) {
        return Optional.ofNullable(guestByToken.get(token));
    }

    /** 앱 base URL(pxe.server.base-url, 끝 슬래시 제거) — 완료 보고 스크립트의 인자로도 쓰인다(E4-1-a-4). */
    public String baseUrl() {
        return baseUrl;
    }

    /** 이 토큰 번들의 URL 접두 — iPXE 스크립트가 파일명을 뒤에 붙인다. */
    public String bundleUrl(UUID token) {
        if (!issued.containsKey(token)) {
            throw new IllegalStateException("발급되지 않았거나 회수된 토큰의 URL 요청. token=" + token);
        }
        return bundleUrlOf(token);
    }

    /** 발급 여부와 무관한 URL 조립 — 번들 렌더 시점(발급 직전)에 쓴다. */
    public String bundleUrlOf(UUID token) {
        return baseUrl + "/api/pxe/v1/windows/" + token;
    }

    public String urlFor(UUID token, WindowsInstallFile file) {
        return bundleUrl(token) + "/" + file.fileName();
    }
}
