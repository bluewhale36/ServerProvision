package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 디스크 묶음 규칙의 디스크 종류 축 (U4-1-1).
 *
 * <p>{@code AUTO} 는 "종류를 정하지 않는다" 가 아니라 <b>"같은 스펙끼리 프로그램이 묶는다"</b> 는 뜻이다
 * (U4-1 토론 E4) — 어느 종류든 매칭하되 한 묶음 안의 디스크는 종류가 같아야 한다. 그 매칭은 실행(E)의
 * 몫이고 정의서는 의도만 담는다({@code BoardModelSelectionMode.AUTO} 와 같은 층).</p>
 */
@RequiredArgsConstructor
@Getter
public enum DiskTypeRequirement {
    SSD("SSD"),
    HDD("HDD"),
    AUTO("자동 탐지");

    private final String displayName;
}
