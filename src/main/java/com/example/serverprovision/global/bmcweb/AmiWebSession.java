package com.example.serverprovision.global.bmcweb;

import com.example.serverprovision.global.redfish.BmcCredentials;

/**
 * AMI 웹 API 세션 한 벌(E3-2 D-5) — {@code POST /api/session} 이 준 CSRF 토큰과 세션 쿠키. 만료({@code cc:7})
 * 시 같은 자격으로 다시 열어 갈아끼우므로 가변이다. 토큰 · 쿠키 · 비밀번호는 어디에도 남기지 않는다.
 */
public final class AmiWebSession {

    private final String bmcIp;
    private final BmcCredentials credentials;
    private String csrfToken;
    private String cookie;

    AmiWebSession(String bmcIp, BmcCredentials credentials, String csrfToken, String cookie) {
        this.bmcIp = bmcIp;
        this.credentials = credentials;
        this.csrfToken = csrfToken;
        this.cookie = cookie;
    }

    public String bmcIp() {
        return bmcIp;
    }

    BmcCredentials credentials() {
        return credentials;
    }

    String csrfToken() {
        return csrfToken;
    }

    String cookie() {
        return cookie;
    }

    void renew(String csrfToken, String cookie) {
        this.csrfToken = csrfToken;
        this.cookie = cookie;
    }

    @Override
    public String toString() {
        return "AmiWebSession[bmcIp=" + bmcIp + ", credentials=" + credentials.source() + ", token=*]";
    }
}
