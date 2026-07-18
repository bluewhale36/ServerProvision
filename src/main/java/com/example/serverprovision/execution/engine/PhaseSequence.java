package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.enums.ProvisioningPhase;

import java.util.Optional;
import java.util.Set;

/**
 * 다음 phase 결정 규칙(E1-0a) — DEC-8 · DEC-25 를 코드로 고정하는 순수 함수.
 * "할당 정의서가 보유한 phase 집합" 은 파라미터다 — 실제 공급자(할당 스냅샷)는 U3 가 만들고,
 * 판정 호출자는 E1-0b(체크인 dispatch) · E1-2(완주 판정)가 배선한다.
 */
public final class PhaseSequence {

    private PhaseSequence() {
    }

    /**
     * {@code current} 완주 후 진입할 phase.
     * <ul>
     *   <li>BOOTSTRAPPING 다음은 무조건 DIAGNOSE_LINUX — 진단은 정의서 payload 를 소비하지 않는
     *       유일한 실행 phase 라 보유 필터 대상이 아니다.</li>
     *   <li>그 이후는 {@link ProvisioningPhase} 선언 순서에서 보유 집합에 속하는 첫 phase (DEC-8 —
     *       SettingProcessType 선언 순 대안은 폼 표시 순서와의 취약 결합으로 탈락).</li>
     *   <li>없으면 empty = 종단 — 호출자가 {@code markCompleted} 를 기록한다
     *       (DEC-25 "보유 마지막 phase 완주 = 종단". E6 보류로 TESTING 은 보유 불가 — 계약 자체가 없다).</li>
     * </ul>
     */
    public static Optional<ProvisioningPhase> nextAfter(
            ProvisioningPhase current, Set<ProvisioningPhase> ownedPhases) {
        if (current == ProvisioningPhase.BOOTSTRAPPING) {
            return Optional.of(ProvisioningPhase.DIAGNOSE_LINUX);
        }
        ProvisioningPhase[] declared = ProvisioningPhase.values();
        for (int i = current.ordinal() + 1; i < declared.length; i++) {
            if (declared[i] == ProvisioningPhase.TESTING) {
                break;
            }
            if (ownedPhases.contains(declared[i])) {
                return Optional.of(declared[i]);
            }
        }
        return Optional.empty();
    }
}
