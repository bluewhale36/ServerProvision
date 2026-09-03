package com.example.serverprovision.management.raidcard.vo;

import java.util.UUID;

/**
 * 게스트 한 대가 관측한 RAID 카드(E3.5-5-b D1) — 저장하지 않는 파생값. "이 카드를 지정한 활성 할당의 게스트" 가
 * 저장 인벤토리에 남긴 감지 Subsystem 을 {@code RaidCardObservationProvider} 가 읽어 만든다.
 *
 * @param pciSubsystemId 소문자 4자리 16진수 쌍({@code DetectedRaidCard.pciSubsystemId} 형식)
 */
public record RaidCardObservation(UUID guestServerId, String guestLabel, String pciSubsystemId) {

	public RaidCardObservation {
		if (guestServerId == null || pciSubsystemId == null || pciSubsystemId.isBlank()
				|| guestLabel == null || guestLabel.isBlank()) {
			throw new IllegalArgumentException("관측은 게스트 id · 라벨 · Subsystem 이 모두 있어야 합니다.");
		}
	}
}
