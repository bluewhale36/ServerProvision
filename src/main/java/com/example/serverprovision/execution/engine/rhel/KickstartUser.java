package com.example.serverprovision.execution.engine.rhel;

import java.util.Objects;
import java.util.Optional;

/**
 * kickstart {@code user} 한 줄의 재료. crypt 는 SHA-512 crypt 값이며 toString 에 싣지 않는다. 비밀번호 없이 저장된
 * 사용자(정의서 저장은 막지 않는다)는 crypt 가 비고 잠긴 계정으로 만든다.
 */
public record KickstartUser(String name, Optional<String> crypt, boolean sudoer) {

    public KickstartUser {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(crypt, "crypt");
    }

    @Override
    public String toString() {
        return "KickstartUser[name=" + name + ", crypt=" + (crypt.isPresent() ? "****" : "locked") + ", sudoer=" + sudoer + "]";
    }
}
