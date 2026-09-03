package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardObservationSummaryResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardResponse;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardObservationStatus;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.exception.RaidCardObservationConfirmRejectedException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.PciSubsystemId;
import com.example.serverprovision.management.raidcard.vo.RaidCardObservation;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * E3.5-5-b D3 · D4 — 관측 요약과 [관측값으로 확정]. 판정은 RaidCardObservationStatus 가 내고 서비스는 그 결과로 거른다.
 */
@ExtendWith(MockitoExtension.class)
class RaidCardObservationServiceTest {

	@Mock RaidCardRepository raidCardRepository;
	@Mock RaidCardObservationProvider observationProvider;
	@InjectMocks RaidCardObservationService service;

	private static RaidCard card(Long id, PciSubsystemId pci) {
		RaidCard card = RaidCard.builder()
				.id(id).vendor(RaidCardVendor.GIGABYTE).modelName("CRA3338")
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1)))
				.cacheCapacity(CacheCapacity.NONE).chipFamily(RaidChipFamily.MPT_IR).pciSubsystemId(pci)
				.ownEnabled(true).ownDeprecated(false).isDeleted(false)
				.build();
		card.recomputeEffective();
		return card;
	}

	private static RaidCardResponse response(Long id, boolean deleted, String pci) {
		return new RaidCardResponse(id, RaidCardVendor.GIGABYTE, "CRA3338",
				List.of(RaidLevel.RAID0), "RAID0", RaidChipFamily.MPT_IR, 0, "없음", false, pci, null,
				true, false, deleted, LifecycleStage.of(false, deleted));
	}

	private static RaidCardObservation obs(String value) {
		return new RaidCardObservation(UUID.randomUUID(), "G", value);
	}

	// ==== summariesByCard ================================================

	@Test
	@DisplayName("비삭제 카드만 provider 에 묻고 카드별 판정을 만든다 — 삭제 카드는 키가 없다")
	void summaries_activeCardsOnly() {
		List<RaidCardVendorGroupResponse> groups = List.of(RaidCardVendorGroupResponse.of(RaidCardVendor.GIGABYTE,
				List.of(response(1L, false, null), response(2L, true, null), response(3L, false, "1000:9361"))));
		given(observationProvider.observationsByCard(Set.of(1L, 3L)))
				.willReturn(Map.of(1L, List.of(obs("1000:9361")), 3L, List.of(obs("1000:00ce"))));

		Map<Long, RaidCardObservationSummaryResponse> summaries = service.summariesByCard(groups);

		assertThat(summaries).containsOnlyKeys(1L, 3L);
		assertThat(summaries.get(1L).status()).isEqualTo(RaidCardObservationStatus.AGREED_UNCONFIRMED);
		assertThat(summaries.get(1L).agreedValue()).isEqualTo("1000:9361");
		assertThat(summaries.get(3L).status()).isEqualTo(RaidCardObservationStatus.DIFFERS_FROM_CONFIRMED);
	}

	@Test
	@DisplayName("관측이 없는 카드는 NONE 요약 — 빈 목록이면 provider 를 부르지 않는다")
	void summaries_noneAndEmpty() {
		given(observationProvider.observationsByCard(Set.of(1L))).willReturn(Map.of());
		Map<Long, RaidCardObservationSummaryResponse> summaries = service.summariesByCard(List.of(
				RaidCardVendorGroupResponse.of(RaidCardVendor.GIGABYTE, List.of(response(1L, false, null)))));
		assertThat(summaries.get(1L).status()).isEqualTo(RaidCardObservationStatus.NONE);
		assertThat(summaries.get(1L).guestCount()).isZero();

		assertThat(service.summariesByCard(List.of())).isEmpty();
	}

	@Test
	@DisplayName("값별 묶음 — 같은 값을 관측한 게스트가 한 묶음으로 모이고 상충은 두 묶음")
	void summaries_groupsByValue() {
		given(observationProvider.observationsByCard(Set.of(1L)))
				.willReturn(Map.of(1L, List.of(obs("1000:9361"), obs("1000:9361"), obs("1000:00ce"))));
		RaidCardObservationSummaryResponse summary = service.summariesByCard(List.of(
				RaidCardVendorGroupResponse.of(RaidCardVendor.GIGABYTE, List.of(response(1L, false, null))))).get(1L);

		assertThat(summary.status()).isEqualTo(RaidCardObservationStatus.CONFLICTING);
		assertThat(summary.values()).hasSize(2);
		assertThat(summary.values().getFirst().guests()).hasSize(2);
		assertThat(summary.guestCount()).isEqualTo(3);
		assertThat(summary.confirmable()).isFalse();
		assertThat(summary.showsButton()).isTrue();
	}

	// ==== confirmObserved ================================================

	@Test
	@DisplayName("happy — 관측이 하나로 모인 미확인 카드는 관측값으로 채워진다")
	void confirm_fillsPci() {
		RaidCard card = card(7L, null);
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));
		given(observationProvider.observationsByCard(Set.of(7L))).willReturn(Map.of(7L, List.of(obs("1000:9361"), obs("1000:9361"))));

		service.confirmObserved(7L);

		assertThat(card.getPciSubsystemId()).isEqualTo(new PciSubsystemId(0x1000, 0x9361));
	}

	@Test
	@DisplayName("409 — 관측 없음(NONE) · 상충(CONFLICTING) 은 상태의 사유 문장으로 거절되고 카드는 그대로")
	void confirm_rejectsNoneAndConflicting() {
		RaidCard card = card(7L, null);
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));

		given(observationProvider.observationsByCard(Set.of(7L))).willReturn(Map.of());
		assertThatThrownBy(() -> service.confirmObserved(7L))
				.isInstanceOf(RaidCardObservationConfirmRejectedException.class)
				.hasMessage(RaidCardObservationStatus.NONE.blockReason());

		given(observationProvider.observationsByCard(Set.of(7L))).willReturn(Map.of(7L, List.of(obs("1000:9361"), obs("1000:00ce"))));
		assertThatThrownBy(() -> service.confirmObserved(7L))
				.isInstanceOf(RaidCardObservationConfirmRejectedException.class)
				.hasMessage(RaidCardObservationStatus.CONFLICTING.blockReason());

		assertThat(card.getPciSubsystemId()).isNull();
	}

	@Test
	@DisplayName("409 — 이미 확정된 카드는 관측이 같든(MATCHES) 다르든(DIFFERS) 거절 — 정정은 수정 폼")
	void confirm_rejectsAlreadyConfirmed() {
		RaidCard card = card(7L, new PciSubsystemId(0x1000, 0x9361));
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));

		given(observationProvider.observationsByCard(Set.of(7L))).willReturn(Map.of(7L, List.of(obs("1000:9361"))));
		assertThatThrownBy(() -> service.confirmObserved(7L))
				.isInstanceOf(RaidCardObservationConfirmRejectedException.class)
				.hasMessage(RaidCardObservationStatus.MATCHES_CONFIRMED.blockReason());

		given(observationProvider.observationsByCard(Set.of(7L))).willReturn(Map.of(7L, List.of(obs("1000:00ce"))));
		assertThatThrownBy(() -> service.confirmObserved(7L))
				.isInstanceOf(RaidCardObservationConfirmRejectedException.class)
				.hasMessage(RaidCardObservationStatus.DIFFERS_FROM_CONFIRMED.blockReason());

		assertThat(card.getPciSubsystemId().toDisplay()).isEqualTo("1000:9361");
	}

	@Test
	@DisplayName("404 — 없는 · 삭제된 카드는 provider 를 묻기 전에 끝난다")
	void confirm_notFound() {
		given(raidCardRepository.findByIdAndIsDeletedFalse(9L)).willReturn(Optional.empty());
		assertThatThrownBy(() -> service.confirmObserved(9L)).isInstanceOf(RaidCardNotFoundException.class);
		verifyNoInteractions(observationProvider);
	}
}
