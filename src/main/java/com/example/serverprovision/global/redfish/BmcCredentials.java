package com.example.serverprovision.global.redfish;

/** BMC 접속 자격증명 한 벌 — {@code source} 는 로그 · 메시지용 표기(비밀번호는 어디에도 남기지 않는다). */
public record BmcCredentials(String username, String password, String source) {

    /** record 자동 생성 toString 이 비밀번호를 노출하므로 마스킹으로 덮는다(E1.6 C 계열 — 평문 노출 0). */
    @Override
    public String toString() {
        return "BmcCredentials[username=" + username + ", password=*, source=" + source + "]";
    }
}
