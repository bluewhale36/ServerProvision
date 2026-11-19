package com.example.serverprovision.execution.engine.osinstall;

import java.util.Optional;

/**
 * OS 설치 단계를 실패로 닫은 사유(E4-1-b-4-2). 원장 meta 에는 {@link #code()} 문자열을 적어 기존 행과 저장 형식이 같다(DDL 없음).
 * 운영자에게 그대로 보일 문구가 있는 사유는 {@link #screenMessage()} 로 내고, 원장이 그 문구를 meta 에 따로 적는다 — 화면은
 * 사유 코드를 해석하지 않고 적힌 문구를 읽는다.
 */
public interface OsInstallFailureReason {

    /** 원장 meta 의 {@code reason} 에 적는 값. */
    String code();

    /** 화면에 그대로 낼 문구 — 없으면 화면이 사유 코드로 정해진 문구를 고른다. */
    default Optional<String> screenMessage() {
        return Optional.empty();
    }
}
