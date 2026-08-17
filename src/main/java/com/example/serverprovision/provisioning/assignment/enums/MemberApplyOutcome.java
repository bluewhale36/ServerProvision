package com.example.serverprovision.provisioning.assignment.enums;

import lombok.Getter;

/**
 * 그룹 일괄 할당에서 멤버 하나가 어떻게 처리되는가 (U3-5-c).
 *
 * <p><b>{@code AssignmentBlockKind} 에 합치지 않는 이유</b>(DEC-G) — 그쪽은 "이 정의서를 이 서버에 붙일 수
 * 있는가" 만 답한다. 단건 화면에서 <b>이미 할당됨은 차단이 아니라 재할당으로 가는 갈림길</b>이므로, 기존
 * 할당 축을 거기 넣으면 {@code AssignmentEligibility} 가 바뀌고 U3-5-a · U3-5-b 의 판정이 함께 흔들린다.
 * 이 분류는 <b>그룹 문맥에서만</b> 뜻이 있다.</p>
 *
 * <p>새 처리 방식이 생기면 상수를 더한다 — 표시명과 배지를 상수가 답하므로 화면에 분기가 늘지 않는다.</p>
 */
@Getter
public enum MemberApplyOutcome {

    /** 붙는다. */
    WILL_ASSIGN("할당됨", "n-badge-green"),

    /**
     * 이미 활성 할당이 있어 건너뛴다. 일괄 재할당은 하지 않는다(DEC-C) — 재할당은 기존 스냅샷을
     * supersede 하는 파괴적 조작이라 여러 대를 한 번에 갈아엎게 된다.
     */
    ALREADY_ASSIGNED("이미 있음", "n-badge-gray"),

    /** 회수됐거나 하드웨어가 맞지 않아 붙일 수 없다. 사유는 {@code AssignmentBlockKind} 가 만든다. */
    BLOCKED("막힘", "n-badge-red");

    private final String displayName;
    private final String badgeClass;

    MemberApplyOutcome(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

	/** 이 결과가 실제 할당으로 이어지는가 — 화면 집계와 실행 판정이 같은 술어를 쓰게 한다. */
    public boolean assigns() {
        return this == WILL_ASSIGN;
    }
}
