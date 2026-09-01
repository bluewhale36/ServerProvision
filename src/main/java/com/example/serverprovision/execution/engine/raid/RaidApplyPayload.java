package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;

import java.util.List;

/**
 * RAID_APPLY 지시에 동봉하는 집행 축약 payload(E3.5-3 결정 1) — 이 직렬화가 agent.sh 계열 어댑터와의
 * SSOT 계약이다. {@code PLANNED} 동결 행의 statusMeta 에도 같은 형태로 저장되어 검증(결정 4)의 대조
 * 기준이 된다. 볼륨 이름은 서버(계획 산출)가 만든 값을 나른다 — 어댑터는 소비만 한다(plan Q1 확정).
 */
public record RaidApplyPayload(
        boolean deleteExisting,
        List<VolumeSpec> volumes,
        List<String> jbod
) {

    public RaidApplyPayload {
        volumes = volumes == null ? List.of() : List.copyOf(volumes);
        jbod = jbod == null ? List.of() : List.copyOf(jbod);
    }

    /** 볼륨 생성 중립 명령 1개 — {@code spvR{규칙번호}V{순번}} 이름(하이픈 미지원 카드 대비 영숫자만). */
    public record VolumeSpec(String name, RaidLevel level, List<String> slots) {

        public VolumeSpec {
            slots = slots == null ? List.of() : List.copyOf(slots);
        }
    }

    /** 계획(E3.5-2 산출)에서 집행에 필요한 것만 추린다 — 미배정 · 규칙 소비 내역은 싣지 않는다. */
    public static RaidApplyPayload from(RaidPlan plan) {
        return new RaidApplyPayload(
                plan.deleteExistingFirst(),
                plan.volumes().stream()
                        .map(v -> new VolumeSpec(v.name(), v.level(), v.memberSlots()))
                        .toList(),
                plan.passthroughs().stream().map(PlannedPassthrough::slot).toList());
    }
}
