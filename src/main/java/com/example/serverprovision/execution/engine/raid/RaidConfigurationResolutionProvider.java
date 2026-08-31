package com.example.serverprovision.execution.engine.raid;

import java.util.Optional;
import java.util.UUID;

/**
 * RAID 구성 목표 공급 SPI(E3.5-1) — 구현은 provisioning 측(할당 스냅샷을 아는 곳)이 한다.
 * {@code BiosSettingResolutionProvider} 와 같은 역전 구조. empty = 활성 할당이 없거나 정의서에
 * RAID 구성 단계가 없다(창 밖). 카드 미지정(raidCardId null)은 empty 가 아니라 target 으로 나른다 —
 * "단계는 있으나 카드 전제 없음(대조 생략)" 과 "단계 자체가 없음" 은 다른 사실이다.
 */
public interface RaidConfigurationResolutionProvider {

    Optional<RaidConfigurationTarget> resolveFor(UUID guestServerId);

    /**
     * 활성 할당의 규칙으로 계획을 산출한다(E3.5-2) — empty = 창 밖(resolveFor 와 같은 의미).
     * 계획은 저장하지 않는 파생물이라 호출 때마다 재산출된다(plan 결정 3).
     */
    Optional<RaidPlanOutcome> planFor(UUID guestServerId, RaidInventory inventory, RaidExistingConfigPolicy policy);
}
