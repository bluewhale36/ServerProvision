package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.global.security.springsecurity.guest.GuestCredential;
import com.example.serverprovision.global.security.springsecurity.guest.ServingTokenLookup;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OS 설치 서빙의 일회용 토큰 발급소(E4-1-a-3 D-3, E4-1-b-4-2 에서 계열 공통으로 옮김). 렌더본에 비밀값이 실리므로 정적 서빙을
 * 열 수 없고, 게스트 토큰은 수명이 길어 URL · 접근 로그에 남기기에 무겁다. 게스트당 토큰 하나이며 다시 발급하면 앞의 토큰은 그
 * 자리에서 죽는다(재시도 뒤 옛 URL 이 살아 있지 않게). 메모리에만 두므로 재기동하면 사라지고, 다음 서빙이 새 토큰을 낸다.
 *
 * @param <T> 토큰이 가리키는 서빙 내용(계열마다 다르다)
 */
@Slf4j
public abstract class ServingTokenRegistry<T> implements ServingTokenLookup {

    private final Map<UUID, T> issued = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byGuest = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> guestByToken = new ConcurrentHashMap<>();
    private final String baseUrl;

    protected ServingTokenRegistry(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    /** 로그 머리표 — 발급 · 회수 기록이 계열 로그와 같이 읽히게. */
    protected abstract String logTag();

    public UUID issue(UUID guestServerId, T content) {
        return issue(guestServerId, UUID.randomUUID(), content);
    }

    /** 미리 뽑은 토큰으로 발급 — 서빙 내용이 자기 토큰 URL 을 품어야 할 때 쓴다(R15-2 D-3). */
    public UUID issue(UUID guestServerId, UUID token, T content) {
        issued.put(token, content);
        guestByToken.put(token, guestServerId);
        UUID previous = byGuest.put(guestServerId, token);
        if (previous != null) {
            issued.remove(previous);
            guestByToken.remove(previous);
        }
        // 토큰 값만 남긴다(접근 로그의 token 세그먼트와 대조용) — 서빙 내용은 비밀값이라 싣지 않는다.
        log.info("[{}] {} — 서빙 토큰 발급 : token={}", logTag(), guestServerId, token);
        return token;
    }

    /** 토큰이 가리키는 서빙 내용 — 없거나 회수됐으면 empty(위조 · 만료를 같은 응답으로 다룬다). */
    public Optional<T> resolve(UUID token) {
        return Optional.ofNullable(issued.get(token));
    }

    /** 이 게스트에게 지금 살아 있는 서빙 내용 — 없으면 empty. */
    public Optional<T> currentOf(UUID guestServerId) {
        return Optional.ofNullable(byGuest.get(guestServerId)).map(issued::get);
    }

    public void revoke(UUID guestServerId) {
        UUID token = byGuest.remove(guestServerId);
        if (token != null) {
            issued.remove(token);
            guestByToken.remove(token);
            log.info("[{}] {} — 서빙 토큰 회수", logTag(), guestServerId);
        }
    }

    @Override
    public Optional<UUID> guestOf(UUID token) {
        return Optional.ofNullable(guestByToken.get(token));
    }

    /** 앱 base URL(pxe.server.base-url, 끝 슬래시 제거). */
    public String baseUrl() {
        return baseUrl;
    }

    /** 발급 여부와 무관한 URL 접두 — 서빙 내용을 렌더하는 시점(발급 직전)에도 쓴다. */
    public String urlOf(UUID token) {
        return baseUrl + "/api/pxe/v1/" + kind().segment() + "/" + token;
    }

    /** 발급된 토큰의 URL 접두 — 회수된 토큰이면 프로그램 오류다. */
    public String issuedUrlOf(UUID token) {
        if (!issued.containsKey(token)) {
            throw new IllegalStateException("발급되지 않았거나 회수된 토큰의 URL 요청. token=" + token);
        }
        return urlOf(token);
    }

    @Override
    public abstract GuestCredential.ServingKind kind();
}
