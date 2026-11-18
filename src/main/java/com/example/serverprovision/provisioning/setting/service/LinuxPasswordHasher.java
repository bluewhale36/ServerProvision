package com.example.serverprovision.provisioning.setting.service;

import org.apache.commons.codec.digest.Sha2Crypt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.function.UnaryOperator;

/**
 * 리눅스 비밀번호 해시기(E4-1-b-2 D-18) — SHA-512 crypt({@code $6$} + 무작위 salt). kickstart {@code --iscrypted}
 * 와 /etc/shadow 가 그대로 읽는 형식이다. salt 가 무작위라 같은 평문도 매번 다른 값이 나온다.
 */
@Component
public class LinuxPasswordHasher implements UnaryOperator<String> {

    @Override
    public String apply(String plain) {
        return Sha2Crypt.sha512Crypt(plain.getBytes(StandardCharsets.UTF_8));
    }

    /** {@code crypt} 가 {@code plain} 의 해시인가 — 해시 안의 salt 로 다시 계산해 비교한다. */
    public static boolean matches(String plain, String crypt) {
        return crypt != null && crypt.equals(Sha2Crypt.sha512Crypt(plain.getBytes(StandardCharsets.UTF_8), crypt));
    }
}
