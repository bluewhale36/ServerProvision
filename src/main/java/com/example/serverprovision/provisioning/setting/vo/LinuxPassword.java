package com.example.serverprovision.provisioning.setting.vo;

import java.util.function.UnaryOperator;

/**
 * 리눅스 계정 비밀번호(E4-1-b-2 D-18) — 입력으로 들어온 평문과 저장된 해시를 타입으로 가른다.
 * 서버는 저장 때 평문을 항상 해시하고, kickstart 는 {@link #toCrypt} 로 해시만 받는다.
 * 옛 저장본의 평문이 실행에 닿아도 그 자리에서 해시되므로 평문이 kickstart 로 나가지 않는다.
 */
public sealed interface LinuxPassword {

    /** crypt(3) 형식의 해시 — 이미 해시면 그대로, 평문이면 {@code hasher} 로 해시한다. */
    String toCrypt(UnaryOperator<String> hasher);

    /** 입력 평문(저장 전 · 옛 저장본). */
    record Plain(String value) implements LinuxPassword {
        @Override
        public String toCrypt(UnaryOperator<String> hasher) {
            return hasher.apply(value);
        }

        @Override
        public String toString() {
            return "Plain[****]";
        }
    }

    /** 저장된 SHA-512 crypt 해시. */
    record Hashed(String crypt) implements LinuxPassword {
        @Override
        public String toCrypt(UnaryOperator<String> hasher) {
            return crypt;
        }
    }
}
