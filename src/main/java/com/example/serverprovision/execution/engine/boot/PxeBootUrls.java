package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.config.PxeBootProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 게스트가 첫 접촉 credential 로 받아야 하는 URL 의 유일한 조립처(S19-2 D-4) — {@code pxe.server.base-url} 에 PXE 부팅 계정의
 * userinfo 를 넣어 {@code http://pxe:<secret>@host:port/…} 를 만든다. 재진입 {@code chain} 과 진단 자산(커널 · initramfs ·
 * modloop · apkovl · apk 저장소)이 이것을 쓰고, 에이전트의 {@code provision_base} 는 토큰으로 인증하므로 credential 이 없다.
 * base-url 이 없는 인스턴스(관리 전용)에서는 재진입만 종전의 상대 경로로 남는다.
 */
@Component
public class PxeBootUrls {

    static final String BOOT_PATH = "/api/pxe/v1/boot";
    static final String ASSETS_PATH = "/api/pxe/v1/assets";

    private final String base;           // 정규화된 base-url · 미설정이면 null
    private final String baseWithAuth;   // userinfo 삽입본 · credential 미설정이면 base 와 같다

    public PxeBootUrls(@Value("${pxe.server.base-url:}") String baseUrl, PxeBootProperties properties) {
        this.base = normalize(baseUrl);
        this.baseWithAuth = base == null ? null : properties.userinfo().map(u -> insertUserinfo(base, u)).orElse(base);
    }

    /** iPXE 가 같은 principal 로 {@code /boot} 를 다시 묻는 chain 대상 — base-url 이 있으면 절대 credential URL, 없으면 상대 경로. */
    public String reentry(String query) {
        String target = base == null ? BOOT_PATH : baseWithAuth + BOOT_PATH;
        return (query == null || query.isBlank()) ? target : target + "?" + query;
    }

    /** 진단 자산 디렉토리의 절대 credential URL — 커널 인자(modloop · apkovl · alpine_repo)와 kernel · initrd 줄이 쓴다. */
    public String assetsBase() {
        return requireBase(baseWithAuth) + ASSETS_PATH;
    }

    /** 에이전트의 API 기준 URL — credential 없이 base-url 그대로(에이전트는 게스트 토큰으로 인증한다). */
    public String provisionBase() {
        return requireBase(base);
    }

    private static String requireBase(String value) {
        if (value == null) {
            throw new IllegalStateException("pxe.server.base-url 미설정 — 게스트에게 줄 절대 URL 을 만들 수 없다 (PXE_SERVER_BASE_URL).");
        }
        return value;
    }

    private static String normalize(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String insertUserinfo(String base, String userinfo) {
        try {
            URI u = URI.create(base);
            return new URI(u.getScheme(), userinfo, u.getHost(), u.getPort(), u.getPath(), u.getQuery(), u.getFragment()).toString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            throw new IllegalStateException("pxe.server.base-url 이 URL 이 아니다 (PXE_SERVER_BASE_URL) : " + base, ex);
        }
    }

    @Override
    public String toString() {
        return "PxeBootUrls[base=" + base + ", auth=" + (base != null && !baseWithAuth.equals(base) ? "userinfo" : "none") + "]";
    }
}
