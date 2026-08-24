package com.example.serverprovision.execution.engine.firmware.step;

/**
 * 집행 상태 기계의 한 행(E2-2 §5) — <b>판정과 수행을 한자리에 둔다.</b>
 *
 * <p>행을 값(action)으로 뽑아 두고 소비처가 그 종류로 분기하면, 행이 늘 때마다 그 분기가 함께 자란다.
 * 대신 행마다 빈을 하나 두고 registry 가 순서대로 물어 처음 맞는 것을 실행하면 <b>새 행 지원 =
 * 빈 등록</b>이 된다. {@code PhaseExecutorRegistry} 가 phase 실행기를 수집하는 것과 같은 형태이며,
 * 덤으로 <b>호출 흐름이 파일 목록으로 읽힌다</b>. 진리표는 여덟 행이고 구현은 일곱인데,
 * 6 · 7행(전원 투입과 복귀 대기)이 같은 상황의 두 얼굴이라 한 클래스가 겸한다.</p>
 *
 * <p>{@link #matches} 는 외부 호출 없이 {@link FlashContext} 만 보고 답해야 한다. 그래야 "어느 행이
 * 이기는가" 를 수행 없이 시험할 수 있다.</p>
 */
public interface FlashStep {

    /**
     * 우선순위 — 작을수록 먼저 묻는다. 값이 곧 §5 진리표의 행 번호이고, 그 순서 자체가 설계다.
     * Task 폴링이 가장 위인 이유는 한 축의 Task 가 떠 있는데 다음 축을 동시에 걸지 않기 위함이고,
     * 준비도 판정을 착수 전 조건과 묶은 이유는 집행 도중 자원이 무너져도 굽는 중인 Task 를 계속
     * 보기 위함이다.
     */
    int order();

    /** 이번 주기에 이 행이 맞는가. 순수 판정이어야 한다. */
    boolean matches(FlashContext context);

    /** 맞으면 수행한다. 상태 전이와 원장 기록은 호출자의 트랜잭션 안에서 일어난다. */
    void execute(FlashContext context);
}
