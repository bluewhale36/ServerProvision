package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 디스크 묶음 규칙의 개수 축 — 개 · 개씩 · 개 이상(E3.5-7-a). 선언 순서 = 폼 select 순서, 첫 상수가 신규 행 기본.
 * <ul>
 * <li>{@code EXACT}(개) — 발견 순 첫 그룹에서 슬롯 순 n 장 한 묶음. 남은 디스크는 후행 규칙으로 흐른다.</li>
 * <li>{@code EACH}(개씩) — 그룹 크기가 n 의 배수면 n 개씩 전부(2026-09-01 배수 분할의 뜻).</li>
 * <li>{@code AT_LEAST}(개 이상) — 크기 ≥ n 이면 그룹 전체 한 볼륨(E19 — RAID5 3개 이상 + 6장 → 볼륨 1개).</li>
 * </ul>
 * 계획 소비는 {@code RaidPlanner}, 포섭(규칙 8)은 {@code DiskGroupRules.countCovers} 가 이 세 값으로 switch 한다.
 */
@RequiredArgsConstructor
@Getter
public enum DiskCountMode {
    EXACT("개"),
    EACH("개씩"),
    AT_LEAST("개 이상");

    private final String suffix;
}
