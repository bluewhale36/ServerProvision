package com.example.serverprovision.management.raidcard.dto.response;

import com.example.serverprovision.management.raidcard.enums.RaidCardObservationStatus;
import com.example.serverprovision.management.raidcard.vo.RaidCardObservation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카드 한 장의 관측 요약(E3.5-5-b D3) — 목록 C3 의 관측 블록이 그대로 그린다. 판정은 {@link RaidCardObservationStatus}.
 *
 * @param agreedValue 관측이 하나로 모였을 때 그 값, 아니면 null
 * @param values      값별 관측 묶음(선언 순서 = 처음 관측된 순)
 */
public record RaidCardObservationSummaryResponse(
		RaidCardObservationStatus status,
		String agreedValue,
		List<String> distinctValues,
		List<ObservedValue> values,
		int guestCount,
		boolean confirmable,
		boolean showsButton,
		String badgeClass,
		String blockReason
) {

	/** 같은 값을 관측한 게스트 묶음. */
	public record ObservedValue(String value, List<RaidCardObservation> guests) {
	}

	public static RaidCardObservationSummaryResponse of(String confirmedDisplay, List<RaidCardObservation> observations) {
		Map<String, List<RaidCardObservation>> byValue = new LinkedHashMap<>();
		for (RaidCardObservation observation : observations) {
			byValue.computeIfAbsent(observation.pciSubsystemId().toLowerCase(), k -> new ArrayList<>()).add(observation);
		}
		List<String> distinct = List.copyOf(byValue.keySet());
		RaidCardObservationStatus status = RaidCardObservationStatus.of(confirmedDisplay, byValue.keySet());
		return new RaidCardObservationSummaryResponse(
				status,
				distinct.size() == 1 ? distinct.getFirst() : null,
				distinct,
				byValue.entrySet().stream().map(e -> new ObservedValue(e.getKey(), List.copyOf(e.getValue()))).toList(),
				observations.size(),
				status.confirmable(),
				status.showsButton(confirmedDisplay != null),
				status.badgeClass(),
				status.blockReason()
		);
	}
}
