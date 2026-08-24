package com.example.serverprovision.execution.engine.phase;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * phase 진입 준비도 등급(E2-1-b, 토론 D2) — 게스트가 그 phase 로 부팅해 들어오는 순간의 판정이다.
 * 2026-07-06 에 확정된 실행 의미론("일부 축 결손은 skip 후 진행 · 본체 재료 결손은 진행 불가")을
 * 등급 어휘로 옮긴 것이라, 화면이 경고한 것과 실행이 하는 행동이 같은 말을 쓰게 된다.
 */
@RequiredArgsConstructor
@Getter
public enum ReadinessGrade {

    /** 재료가 온전하다 — 정상 phase 스크립트. */
    READY("준비됨"),

    /** 일부 축이 결손이라 그 축만 건너뛰고 진행한다. */
    DEGRADED("일부 건너뜀"),

    /** 본체 재료가 결손이라 진입할 수 없다 — 대기 후 시한이 지나면 실패(D1 사다리). */
    BLOCKED("자원 결손");

    private final String description;
}
