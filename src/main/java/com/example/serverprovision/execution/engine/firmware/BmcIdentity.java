package com.example.serverprovision.execution.engine.firmware;

/**
 * BMC 신원 확인 결과(E2-2 D-11) — 우리가 지금 두드리는 BMC 가 이 게스트의 것인가.
 *
 * <p>BMC 주소는 진단이 한 번 수집한 값이고 갱신 경로가 없다. 그런데 BMC 는 펌웨어를 구운 뒤 스스로
 * 재기동하며 한동안 사라졌다 돌아오고, DHCP 에 고정 예약이 없어 그때 다른 주소를 받을 수 있다.
 * 그 주소를 다른 게스트의 BMC 가 쓰고 있다면 <b>남의 장비를 굽거나 끄게 된다.</b></p>
 *
 * <p>{@link #MISMATCHED} 와 {@link #UNREACHABLE} 을 가르는 것이 이 enum 의 존재 이유다 —
 * 도달하지 못하는 것은 <b>상태</b>라 기다리면 풀릴 수 있지만, 다른 장비가 답하는 것은 <b>사건</b>이다.
 * 사건에는 즉시 멈춰야 한다. 계속 시도하면 그 장비를 계속 건드린다.</p>
 */
public enum BmcIdentity {

    /** 응답한 장비의 신원이 이 게스트와 일치한다 — 진행한다. */
    MATCHED,

    /** 다른 장비가 답했다 — 즉시 멈춘다. 자동 재시도는 그 장비를 계속 건드리는 일이다. */
    MISMATCHED,

    /** 응답이 없다 — 주소를 다시 찾아본 뒤, 그래도 없으면 시한까지 기다린다. */
    UNREACHABLE
}
