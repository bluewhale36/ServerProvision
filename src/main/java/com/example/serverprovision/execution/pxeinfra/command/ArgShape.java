package com.example.serverprovision.execution.pxeinfra.command;

import java.util.List;

/**
 * {@link AllowedCommand} 의 가변(caller) 인자 형태 — arity + 형태를 invariant if-throw 로 강제(도메인 예외 아닌
 * IllegalArgumentException). 상수별 정규화 로직을 method-per-constant 로 담아 분기문 없이 형태를 표현한다.
 */
public enum ArgShape {

    /** caller 인자 0개. */
    NONE {
        @Override
        public List<String> normalize(List<String> args) {
            if (!args.isEmpty()) throw new IllegalArgumentException("이 명령은 인자를 받지 않습니다: " + args);
            return List.of();
        }
    },

    /** caller 인자 1개 — 절대경로로 정규화(신뢰 config 출처지만 방어적 정규화). */
    ONE_PATH {
        @Override
        public List<String> normalize(List<String> args) {
            if (args.size() != 1) throw new IllegalArgumentException("정확히 경로 1개가 필요합니다: " + args);
            java.nio.file.Path p = java.nio.file.Path.of(args.get(0)).toAbsolutePath().normalize();
            return List.of(p.toString());
        }
    };

    public abstract List<String> normalize(List<String> args);
}
