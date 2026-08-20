package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 디스크 묶음 규칙의 역할 축 (U4-1-2). 규칙이 만들어 낼 볼륨을 어느 영역에 둘지 정한다.
 *
 * <p>묶음은 볼륨 하나가 아니라 규칙이라(E18) 한 규칙이 볼륨을 여럿 낳을 수 있다. 그래서 "OS / Data" 둘만으로는
 * OS 유일성(E7)이 성립하지 않고, 우선순위에 맡기는 값({@link #BY_PRIORITY})과 어느 영역에도 붙이지 않는
 * 값({@link #NONE} — RAID 구성만 하고 마운트하지 않는다, 사용자 확정 2026-08-19)이 함께 있어야 한다.
 * OS 고정({@link #OS})은 정의서당 최대 1 규칙 — {@code DiskGroupRules} 7 번 규칙.</p>
 */
@RequiredArgsConstructor
@Getter
public enum DiskGroupRole {
    BY_PRIORITY("우선순위에 따름"),
    OS("OS 영역"),
    DATA("Data 영역"),
    NONE("영역 할당 없음");

    private final String displayName;

    /** OS 영역이 될 수 있는가 — 고정이거나 우선순위 판정 대상. {@code DATA} · {@code NONE} 은 후보 밖. */
    public boolean canBeOs() {
        return this == OS || this == BY_PRIORITY;
    }
}
