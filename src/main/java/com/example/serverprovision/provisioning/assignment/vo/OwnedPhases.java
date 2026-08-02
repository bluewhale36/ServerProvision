package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.execution.enums.ProvisioningPhase;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * 할당 스냅샷이 소유한 실행 phase 집합 — {@code SettingProcessType}→{@code ProvisioningPhase} 매핑의
 * derive-then-freeze 결과이자 phase 진행의 <b>유일 권위</b>(결정 D-G). {@code AssignedProcess.processType}
 * 행에서 재도출하지 않는다.
 *
 * <p>불변 · {@link ProvisioningPhase} 선언 순({@code ordinal}) 정렬 집합이다. 도메인은 <b>정의서가 기여할 수
 * 있는 실행 phase 만</b> — {@code BOOTSTRAPPING}(부트) · {@code DIAGNOSE_LINUX}(정의서 소비 없는 무조건 phase) ·
 * {@code TESTING}(E6 보류)은 소유 대상이 아니라 안전망 invariant 로 거절한다. 영속은
 * {@link OwnedPhasesConverter} 가 <b>안정 코드</b>(enum name 아님)로 왕복한다(불멸 행 하위호환, 결정 D-F).</p>
 */
public record OwnedPhases(SortedSet<ProvisioningPhase> phases) {

    /** 정의서가 소유할 수 없는 phase — 소유는 실행 가능 phase 로 한정한다. */
    private static final Set<ProvisioningPhase> NON_ASSIGNABLE = Set.of(
            ProvisioningPhase.BOOTSTRAPPING,
            ProvisioningPhase.DIAGNOSE_LINUX,
            ProvisioningPhase.TESTING);

    public OwnedPhases {
        if (phases == null) {
            throw new IllegalArgumentException("ownedPhases 는 null 일 수 없습니다.");
        }
        SortedSet<ProvisioningPhase> sorted = new TreeSet<>(phases);   // 선언 순(ordinal) 정렬
        for (ProvisioningPhase phase : sorted) {
            if (NON_ASSIGNABLE.contains(phase)) {
                throw new IllegalArgumentException("정의서가 소유할 수 없는 phase 입니다: " + phase);
            }
        }
        phases = Collections.unmodifiableSortedSet(sorted);
    }

    public static OwnedPhases of(Collection<ProvisioningPhase> phases) {
        return new OwnedPhases(new TreeSet<>(phases));
    }

    public static OwnedPhases empty() {
        return new OwnedPhases(new TreeSet<>());
    }

    /** 불변 정렬 집합(선언 순). provider 가 엔진에 그대로 넘긴다. */
    public Set<ProvisioningPhase> asSet() {
        return phases;
    }

    public boolean isEmpty() {
        return phases.isEmpty();
    }

    public boolean contains(ProvisioningPhase phase) {
        return phases.contains(phase);
    }
}
