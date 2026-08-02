package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.enums.ProvisioningPhase;

import java.util.Set;
import java.util.UUID;

/**
 * 게스트별 {@code ownedPhases} 공급 계약(SPI) — <b>소비자(엔진)가 소유하는 인터페이스</b>다(dependency
 * inversion, {@code MarkableScanner} 선례). 구현은 할당 도메인({@code provisioning.assignment})이 제공하며
 * {@code execution} 은 {@code provisioning} 을 import 하지 않는다.
 *
 * <p>{@link com.example.serverprovision.execution.engine.PhaseSequence#nextAfter} 의 {@code ownedPhases}
 * 파라미터를 채우는 실공급자다. 활성 스냅샷(할당)이 없는 게스트는 <b>immutable 빈 Set</b> 을 받는다 —
 * 빈 Set → {@code nextAfter} 가 {@code DIAGNOSE_LINUX} 후 empty → 진단 완주 종단이라는 현 동작을
 * 분기 추가 0 으로 재현한다. 판정 호출부({@code Set.of()} 하드코딩)의 배선은 ES-1 이 담당한다(U3 는 공급까지).</p>
 */
public interface OwnedPhasesProvider {

    /** 활성 할당 스냅샷의 소유 phase. 활성 할당이 없으면 {@code Set.of()}. */
    Set<ProvisioningPhase> ownedPhasesOf(UUID guestId);
}
