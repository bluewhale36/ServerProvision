package com.example.serverprovision.execution.engine.worker;

/**
 * 워커 주도 phase 의 상태 기계 한 행(E3-1 D-1 — E2-2 {@code FlashStep} 에서 추출) — <b>판정과 수행을 한자리에</b> 둔다.
 * 행마다 빈을 하나 두고 registry 가 순서대로 물어 처음 맞는 것을 실행하므로, 새 행 지원 = 빈 등록이다.
 *
 * @param <C> 그 phase 의 주기 컨텍스트 — {@link #matches} 는 이것만 보고 답해야 한다(외부 호출 없는 순수 판정).
 */
public interface WorkerStep<C> {

    /** 우선순위 — 작을수록 먼저 묻는다. 값이 곧 진리표의 행 번호다. */
    int order();

    /** 이번 주기에 이 행이 맞는가. 순수 판정이어야 한다. */
    boolean matches(C context);

    /** 맞으면 수행한다. 상태 전이와 원장 기록은 호출자의 트랜잭션 안에서 일어난다. */
    void execute(C context);
}
