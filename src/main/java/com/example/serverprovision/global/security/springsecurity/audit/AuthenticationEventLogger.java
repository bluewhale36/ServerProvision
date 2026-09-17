package com.example.serverprovision.global.security.springsecurity.audit;

import com.example.serverprovision.global.security.springsecurity.guest.authorization.ProvisioningLanPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 인증 · 인가 · 계정 사건의 로그(S20) — Spring Security 가 발행하는 이벤트를 한 자리에서 받아 한 줄씩 남긴다. 2026-09-16 첫 관리자
 * 로그인 불가 사고 때 저널에 인증 흔적이 한 줄도 없어 원인을 좁힐 수 없었던 것이 계기다. 이벤트를 듣는 이유: 로그인 필터 · 게스트
 * 체인 · Basic · 로그아웃 · 인가 어디에도 코드를 끼우지 않고, 체인이 늘어도 여기가 그대로다(Spring primitive 로 분기를 흡수).
 *
 * <p>태그와 수준 — 운영자가 {@code journalctl | grep '\[auth'} 로 훑는다.</p>
 * <ul>
 *   <li>{@code [auth.login]} INFO — 사람의 로그인(폼). {@code [auth.authenticated]} DEBUG — 요청마다 다시 인증되는 Basic · 게스트 토큰(매 요청이라 INFO 면 홍수).</li>
 *   <li>{@code [auth.login.failed]} WARN — 사유 코드(BAD_CREDENTIALS · DISABLED · LOCKED …)는 이벤트 클래스 이름에서 기계적으로 뽑는다. 사용자 부재는
 *       Spring Security 가 BAD_CREDENTIALS 로 감추므로(계정 열거 방지) 그대로 둔다.</li>
 *   <li>{@code [auth.logout]} INFO · {@code [authz.denied]} WARN(인증된 사용자 또는 프로비저닝 LAN 밖) / DEBUG(익명 — 로그인 유도가 정상 UX).</li>
 *   <li>{@code [auth.account.created]} · {@code [auth.password.changed]} INFO — {@link AccountAuditEvent}.</li>
 * </ul>
 * <p>비밀값은 절대 싣지 않는다 — 게스트 토큰은 {@code Authentication.getName()} 이 게스트 id 또는 "guest" 만 주고, 비밀번호 · secret 은 이벤트에 없다.
 * logback 의 {@code MASK_PATTERN} 은 그 위의 안전망이다.</p>
 */
@Slf4j
@Component
public class AuthenticationEventLogger {

    private static final String GUEST_CHANNEL_PREFIX = "/api/pxe/v1/";
    private static final String FACTOR_AUTHORITY_PREFIX = "FACTOR_";

    @EventListener
    public void onInteractiveLogin(InteractiveAuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.info("[auth.login] user={} roles={} channel={} ip={}",
                auth.getName(), roles(auth), channel(), ip(auth));
    }

    /** 요청마다 다시 인증되는 경로(Basic · 게스트 토큰 · PXE 부팅 계정) — 폼 로그인은 위 사건이 따로 오므로 여기서는 DEBUG 로만. */
    @EventListener
    public void onAuthenticated(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.debug("[auth.authenticated] user={} roles={} channel={} ip={}",
                auth.getName(), roles(auth), channel(), ip(auth));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        Authentication auth = event.getAuthentication();
        log.warn("[auth.login.failed] user={} reason={} channel={} ip={} detail=\"{}\"",
                auth == null ? "-" : auth.getName(), reasonOf(event), channel(), ip(auth), event.getException().getMessage());
    }

    @EventListener
    public void onLogout(LogoutSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.info("[auth.logout] user={} ip={}", auth.getName(), ip(auth));
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication auth = authenticationOf(event.getAuthentication());
        boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;
        boolean lanDenied = event.getAuthorizationResult() instanceof ProvisioningLanPolicy.Denied;
        String line = "[authz.denied] user={} {} channel={} ip={} result={}";
        Object[] args = {anonymous ? "anonymous" : auth.getName(), target(event.getObject()), channel(), ip(auth),
                event.getAuthorizationResult().getClass().getSimpleName()};
        if (anonymous && !lanDenied) {
            log.debug(line, args);   // 로그인 전 화면 진입 — entry point 가 로그인으로 보내는 정상 흐름
            return;
        }
        log.warn(line, args);
    }

    @EventListener
    public void onAccountEvent(AccountAuditEvent event) {
        switch (event.kind()) {
            case CREATED -> log.info("[auth.account.created] user={} by={} mode={} roles={} mustChangePassword={}",
                    event.username(), event.actor(), event.mode(), event.roles(), event.mustChangePassword());
            case PASSWORD_CHANGED -> log.info("[auth.password.changed] user={} by={} mode={}",
                    event.username(), event.actor(), event.mode());
        }
    }

    /** {@code AuthenticationFailureBadCredentialsEvent} → {@code BAD_CREDENTIALS} — 이름 규칙에서 뽑으므로 사유가 늘어도 표가 없다. */
    static String reasonOf(AbstractAuthenticationFailureEvent event) {
        String name = event.getClass().getSimpleName()
                .replaceFirst("^AuthenticationFailure", "")
                .replaceFirst("Event$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    /** 역할만 — Spring Security 7 이 성공 인증에 끼워 넣는 인증 요소 권한(FACTOR_PASSWORD …)은 감사 줄에서 소음이라 뺀다. */
    private static List<String> roles(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith(FACTOR_AUTHORITY_PREFIX))
                .sorted()
                .toList();
    }

    private static Authentication authenticationOf(Supplier<? extends Authentication> supplier) {
        try {
            return supplier == null ? null : supplier.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** AuthorizationFilter 는 요청 자체를 실어 보낸다(RequestAuthorizationContext 가 아니라) — 메서드 · 경로만 남긴다. */
    private static String target(Object object) {
        if (object instanceof HttpServletRequest request) {
            return "method=" + request.getMethod() + " path=" + request.getRequestURI();
        }
        return "target=" + (object == null ? "-" : object.getClass().getSimpleName());
    }

    /** 출발지 — 인증 details(폼 · Basic)가 들고 있으면 그것, 아니면 현재 요청(게스트 필터 · 인가), 둘 다 없으면 "-". */
    private static String ip(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof WebAuthenticationDetails details && details.getRemoteAddress() != null) {
            return details.getRemoteAddress();
        }
        HttpServletRequest request = currentRequest();
        return request == null ? "-" : request.getRemoteAddr();
    }

    private static String channel() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "-";
        }
        return request.getRequestURI().startsWith(GUEST_CHANNEL_PREFIX) ? "guest" : "web";
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
