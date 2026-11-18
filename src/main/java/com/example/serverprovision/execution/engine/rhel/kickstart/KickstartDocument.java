package com.example.serverprovision.execution.engine.rhel.kickstart;

/** 렌더된 kickstart. 비밀번호 해시가 들어 있어 메모리에만 두고 로그 · 원장에 싣지 않는다. */
public record KickstartDocument(String text) {

    @Override
    public String toString() {
        return "KickstartDocument[****]";
    }
}
