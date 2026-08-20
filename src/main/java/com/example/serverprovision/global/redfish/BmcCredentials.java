package com.example.serverprovision.global.redfish;

/** BMC 접속 자격증명 한 벌 — {@code source} 는 로그 · 메시지용 표기(비밀번호는 어디에도 남기지 않는다). */
public record BmcCredentials(String username, String password, String source) {
}
