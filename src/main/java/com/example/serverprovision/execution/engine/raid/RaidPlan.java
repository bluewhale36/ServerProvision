package com.example.serverprovision.execution.engine.raid;

import java.util.List;

/**
 * RAID 구성 계획 한 벌(E3.5-2) — 계열 CLI 어휘가 섞이지 않은 중립 명령 표현. 집행(E3.5-3)의 입력이자
 * 상세 화면 미리보기의 조회 모델. OS 영역 지정은 별도 필드가 아니라 {@code role == OS} 인 항목 최대 1개로 나타난다.
 *
 * @param osAbsenceReason OS 고정 규칙이 볼륨을 내지 못했을 때의 사유 — 그 외에는 null(§8 Q3 판정)
 */
public record RaidPlan(
        boolean deleteExistingFirst,
        List<PlannedVolume> volumes,
        List<PlannedPassthrough> passthroughs,
        List<UnassignedDisk> unassigned,
        List<RaidRuleOutcome> ruleOutcomes,
        String osAbsenceReason
) implements RaidPlanOutcome {

    public RaidPlan {
        volumes = volumes == null ? List.of() : List.copyOf(volumes);
        passthroughs = passthroughs == null ? List.of() : List.copyOf(passthroughs);
        unassigned = unassigned == null ? List.of() : List.copyOf(unassigned);
        ruleOutcomes = ruleOutcomes == null ? List.of() : List.copyOf(ruleOutcomes);
    }
}
