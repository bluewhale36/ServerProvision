package com.example.serverprovision.execution.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * PXE 부팅 계정(S19-2 D-1) — 토큰이 생기기 전의 첫 접촉({@code /boot} · {@code /assets/**})을 여는 Config.
 * credential 은 URL userinfo({@code http://pxe:<secret>@…})로 배포되므로 퍼센트 인코딩이 필요 없는 문자만 받는다.
 * 자산을 서빙하는 인스턴스({@code pxe.assets.root} 설정)가 secret 없이 뜨면 첫 접촉이 무인증으로 열리므로 기동을 막는다
 * ({@link PxeAssetsProperties} 선례). 자산 루트가 없는 관리 전용 인스턴스는 secret 없이 기동하되 PXE 채널은 닫힌 채다.
 */
@Component
public class PxeBootProperties {

    /** RFC 3986 unreserved — boot.ipxe 의 chain URL 과 커널 인자에 인코딩 없이 실린다. */
    private static final Pattern URL_SAFE = Pattern.compile("[A-Za-z0-9._~-]+");

    private final String username;
    private final String secret;   // null = 미설정(채널 닫힘)

    public PxeBootProperties(
			@Value("${pxe.boot.username:pxe}") String username,
			@Value("${pxe.boot.secret:}") String secret,
			@Value("${pxe.assets.root:}") String assetsRoot
	) {
        if (username == null || !URL_SAFE.matcher(username).matches()) {
            throw new IllegalStateException("pxe.boot.username 은 영문 · 숫자 · . _ ~ - 만 허용한다 (PXE_BOOT_USERNAME) : " + username);
        }
        boolean configured = secret != null && !secret.isBlank();
        boolean servesAssets = assetsRoot != null && !assetsRoot.isBlank();
        if (!configured && servesAssets) {
            throw new IllegalStateException(
                    "pxe.assets.root 가 설정되면 pxe.boot.secret 도 필수다 — 자산을 서빙하는 인스턴스의 첫 접촉(/boot · /assets)이 무인증으로 열리지 않게 한다 (PXE_BOOT_SECRET)."
			);
        }
        if (configured && !URL_SAFE.matcher(secret).matches()) {
            throw new IllegalStateException(
                    "pxe.boot.secret 은 URL 에 그대로 실리는 값이라 영문 · 숫자 · . _ ~ - 만 허용한다 (PXE_BOOT_SECRET)."
			);
        }
        this.username = username;
        this.secret = configured ? secret : null;
    }

    public String username() {
        return username;
    }

    /** 미설정이면 null — provider 는 이 경우 어떤 credential 도 받지 않는다. */
    public String secret() {
        return secret;
    }

    public boolean configured() {
        return secret != null;
    }

    /** URL userinfo({@code user:secret}) — 미설정이면 비어 있다. */
    public Optional<String> userinfo() {
        return configured() ? Optional.of(username + ":" + secret) : Optional.empty();
    }

    @Override
    public String toString() {
        return "PxeBootProperties[username=" + username + ", secret=" + (configured() ? "***" : "(unset)") + "]";
    }
}
