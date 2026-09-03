package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.management.raidcard.dto.response.RaidCardObservationSummaryResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.exception.RaidCardObservationConfirmRejectedException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.PciSubsystemId;
import com.example.serverprovision.management.raidcard.vo.RaidCardObservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RAID 카드 관측 요약 · [관측값으로 확정](E3.5-5-b D3 · D4). 관측은 {@link RaidCardObservationProvider} 가 파생하고
 * 이 서비스는 판정({@code RaidCardObservationStatus})을 얹어 화면과 가드에 같은 값을 준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaidCardObservationService {

	private final RaidCardRepository raidCardRepository;
	private final RaidCardObservationProvider observationProvider;

	/** 목록의 비삭제 카드마다 관측 요약 — provider 는 한 번만 부른다. 삭제된 카드는 키가 없다(관측 블록을 그리지 않는다). */
	public Map<Long, RaidCardObservationSummaryResponse> summariesByCard(List<RaidCardVendorGroupResponse> groups) {
		Map<Long, String> confirmedById = new LinkedHashMap<>();
		groups.stream().flatMap(g -> g.items().stream())
				.filter(card -> !card.isDeleted())
				.forEach(card -> confirmedById.put(card.id(), card.pciSubsystemIdDisplay()));
		if (confirmedById.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<RaidCardObservation>> observations = observationProvider.observationsByCard(confirmedById.keySet());
		Map<Long, RaidCardObservationSummaryResponse> summaries = new HashMap<>();
		confirmedById.forEach((id, confirmed) -> summaries.put(id,
				RaidCardObservationSummaryResponse.of(confirmed, observations.getOrDefault(id, List.of()))));
		return summaries;
	}

	/** 관측이 하나로 모인 미확인 카드에만 허용 — 화면과 같은 판정으로 거르고, 엔티티 가드가 안전망이다. */
	@Transactional
	public void confirmObserved(Long id) {
		RaidCard card = RaidCardGuards.requireActiveCard(raidCardRepository, id);
		List<RaidCardObservation> observations = observationProvider.observationsByCard(Set.of(id)).getOrDefault(id, List.of());
		RaidCardObservationSummaryResponse summary = RaidCardObservationSummaryResponse.of(
				card.getPciSubsystemId() == null ? null : card.getPciSubsystemId().toDisplay(), observations);
		if (!summary.confirmable()) {
			throw new RaidCardObservationConfirmRejectedException(summary.blockReason());
		}
		card.confirmObservedPciSubsystemId(PciSubsystemId.parse(summary.agreedValue()));
		log.info("[raidCard] 관측값으로 확정 : id={}, pciSubsystemId={}, guests={}",
				id, summary.agreedValue(), observations.stream().map(RaidCardObservation::guestServerId).toList());
	}
}
