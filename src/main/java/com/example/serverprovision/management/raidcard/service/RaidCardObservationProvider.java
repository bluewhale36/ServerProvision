package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.management.raidcard.vo.RaidCardObservation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 카드별 관측 공급 SPI(E3.5-5-b D1) — 관리 영역이 선언하고 할당을 아는 provisioning 측이 구현한다
 * ({@code RaidConfigurationResolutionProvider} 와 같은 역전 구조, 방향만 반대).
 * 관측은 "이 카드를 지정한 활성 스냅샷의 게스트" 가 저장한 RAID 인벤토리에서 파생하며 저장하지 않는다.
 */
public interface RaidCardObservationProvider {

	/** 대상 카드 id 집합 → 카드별 관측 목록. 관측이 없는 카드는 키가 없다. */
	Map<Long, List<RaidCardObservation>> observationsByCard(Set<Long> raidCardIds);
}
