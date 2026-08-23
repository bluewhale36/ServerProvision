package com.example.serverprovision.execution.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 진행 국면 <b>안</b>의 운동 양태(ES-2, 토론 D4) — 거시 국면(미개시 · 실패 · 종단)을 뜻하는 값은
 * 의도적으로 없다. 그런 값이 있으면 stale 값이 3 timestamp 신호와 모순을 표현할 수 있게 되기 때문에,
 * 어휘 자체를 실행 창 안(started_at ≠ NULL ∧ failed_at = NULL ∧ completed_at = NULL)으로 제한하고
 * 실행 창 밖에서는 컬럼을 NULL 로 강제한다(도메인 메서드 + 수동 DDL CHECK 이중 방어).
 *
 * <p>금지 전이 {@code STEP_RUNNING → HOLD} — 착수한 뒤의 자원 결손은 대기가 아니라 실패다(토론 D1).
 * HOLD 는 이번 슬라이스에서 값 · CHECK 만 선언하며, 진입 메서드(작성자)와 함께 이 금지 전이 가드는
 * 준비도 훅을 만드는 E2-1 이 데려온다 — 작성자 없는 진입 메서드는 소비자 없는 추상이다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum ProvisioningMotion {

    /** 게스트가 다음 step 을 향해 재부팅 · 폴링 중 — 원장에 열린 RUNNING 행이 없는 구간. */
    AWAITING_BOOT("부팅 대기"),

    /** 게스트가 step 을 열어 실행 중 — 원장 RUNNING 행의 같은 트랜잭션 투영(사실의 원본은 원장). */
    STEP_RUNNING("작업 실행 중"),

    /** 자원 결손 대기(TTL 가동) — 값만 선언. 진입 로직은 E2-1 준비도 훅 소관. */
    HOLD("자원 결손 대기");

    private final String description;
}
