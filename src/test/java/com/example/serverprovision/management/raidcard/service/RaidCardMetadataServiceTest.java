package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardCreateRequest;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardUpdateRequest;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.exception.IllegalRaidCardStateException;
import com.example.serverprovision.management.raidcard.exception.RaidCardNudgeRequiredException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MA7 — {@link RaidCardMetadataService} 단위 테스트 (조회 · 등록 · 수정 · nudge 완결).
 *
 * <p>등록의 3 분기(살아 있는 동일키 fail-fast / 휴지통 · Deprecated 후보 nudge / 정상 저장)와
 * PCI 선택 입력의 null 왕복이 핵심이다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RaidCardMetadataServiceTest {

	@Mock RaidCardRepository raidCardRepository;
	@Mock NudgeRegistry nudgeRegistry;

	RaidCardMetadataService raidCardService;

	@BeforeEach
	void initService() {
		raidCardService = new RaidCardMetadataService(raidCardRepository, nudgeRegistry);
	}

	// ==== helper =====================================================

	private static RaidCard savedCard(Long id, RaidCardVendor vendor, String modelName) {
		RaidCard card = RaidCard.builder()
				.id(id).vendor(vendor).modelName(modelName)
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1)))
				.cacheCapacity(CacheCapacity.NONE)
				.ownEnabled(true).ownDeprecated(false).isDeleted(false)
				.build();
		card.recomputeEffective();
		return card;
	}

	private static RaidCardCreateRequest createRequest(String pci) {
		return new RaidCardCreateRequest(
				RaidCardVendor.GIGABYTE, "CRA3338",
				List.of(RaidLevel.RAID0, RaidLevel.RAID1), 0, pci, "desc");
	}

	// ==== 조회 =======================================================

	@Test
	@DisplayName("findAllGrouped : 제조사별 그룹핑 + 응답에 지원 레벨 동봉 (U4-1-1 계약)")
	void findAllGrouped_groupsByVendor() {
		given(raidCardRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
				.willReturn(List.of(
						savedCard(1L, RaidCardVendor.GIGABYTE, "CRA3338"),
						savedCard(2L, RaidCardVendor.AVAGO, "9361-8i")));

		List<RaidCardVendorGroupResponse> groups = raidCardService.findAllGrouped(false);

		assertThat(groups).hasSize(2);
		assertThat(groups.get(0).items().get(0).supportedRaidLevels())
				.containsExactly(RaidLevel.RAID0, RaidLevel.RAID1);
		assertThat(groups.get(0).items().get(0).supportedRaidLevelsDisplay()).isEqualTo("RAID0 · RAID1");
	}

	// ==== create =====================================================

	@Test
	@DisplayName("create : 충돌 없음 + PCI 입력 → 저장 (VO 정규화 왕복)")
	void create_success_withPci() {
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(false);
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeletedTrue(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(List.of());
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(List.of());
		given(raidCardRepository.saveAndFlush(any(RaidCard.class)))
				.willReturn(savedCard(42L, RaidCardVendor.GIGABYTE, "CRA3338"));

		Long id = raidCardService.create(createRequest("0x1458:0x0011"));

		assertThat(id).isEqualTo(42L);
	}

	@Test
	@DisplayName("create : PCI 빈 문자열 → null(미확인)로 저장")
	void create_blankPci_storesNull() {
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				any(), any())).willReturn(false);
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeletedTrue(any(), any()))
				.willReturn(List.of());
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(any(), any()))
				.willReturn(List.of());
		given(raidCardRepository.saveAndFlush(any(RaidCard.class)))
				.willAnswer(inv -> {
					RaidCard toSave = inv.getArgument(0);
					assertThat(toSave.getPciSubsystemId()).isNull();   // 미확인 계약
					return savedCard(43L, toSave.getVendor(), toSave.getModelName());
				});

		Long id = raidCardService.create(createRequest(""));

		assertThat(id).isEqualTo(43L);
	}

	@Test
	@DisplayName("create : 살아 있는 동일키 존재 → DuplicateRaidCardException (fail-fast, nudge 후보 미조회)")
	void create_liveDuplicate_throws409() {
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(true);

		assertThatThrownBy(() -> raidCardService.create(createRequest("")))
				.isInstanceOf(DuplicateRaidCardException.class);
		verify(raidCardRepository, never()).findAllByVendorAndModelNameAndIsDeletedTrue(any(), any());
		verify(raidCardRepository, never()).saveAndFlush(any(RaidCard.class));
	}

	@Test
	@DisplayName("create : 휴지통 동일키 존재 → RaidCardNudgeRequiredException + 세션 발급 (저장 안 함)")
	void create_trashedCandidate_throwsNudgeRequired() {
		RaidCard trashed = RaidCard.builder()
				.id(9L).vendor(RaidCardVendor.GIGABYTE).modelName("CRA3338")
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0)))
				.cacheCapacity(CacheCapacity.NONE).ownEnabled(true).ownDeprecated(false).isDeleted(true)
				.build();
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(false);
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeletedTrue(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(List.of(trashed));
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(List.of());
		given(nudgeRegistry.register(
				org.mockito.ArgumentMatchers.eq(NudgeResourceType.RAID_CARD), isNull(), anyList(), any()))
				.willReturn(session(Map.of()));

		assertThatThrownBy(() -> raidCardService.create(createRequest("1458:0011")))
				.isInstanceOf(RaidCardNudgeRequiredException.class);
		verify(raidCardRepository, never()).saveAndFlush(any(RaidCard.class));
	}

	/**
	 * CP5 발견 결함(D1)의 회귀 가드 — 등록 버튼 연타로 두 요청이 서비스 검사를 함께 통과하면, 뒤늦은
	 * 쪽이 DB 유니크 인덱스에 걸려 {@link DataIntegrityViolationException} 이 난다. 그것이 처리되지 않은
	 * 500 으로 새면 전역 폼 가로채기가 삼켜 사용자 화면에는 성공으로 보인다(실측된 silent-500).
	 * 순차 등록과 같은 {@link DuplicateRaidCardException}(409 + modelName 필드 오류)으로 번역되어야 한다.
	 */
	@Test
	@DisplayName("create : 동시 등록 경합으로 DB 유일성 위반 → DuplicateRaidCardException 으로 번역 (D1 회귀 가드)")
	void create_concurrentRace_translatesToDuplicate() {
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(false);   // 경합 상대가 아직 커밋 전 — 검사 통과
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeletedTrue(any(), any()))
				.willReturn(List.of());
		given(raidCardRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(any(), any()))
				.willReturn(List.of());
		given(raidCardRepository.saveAndFlush(any(RaidCard.class)))
				.willThrow(new DataIntegrityViolationException(
						"Duplicate entry 'GIGABYTE:CRA3338' for key 'uk_raid_card_active_identity'"));

		assertThatThrownBy(() -> raidCardService.create(createRequest("")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessageContaining("이미 등록된 RAID 카드입니다");
	}

	@Test
	@DisplayName("completePendingCardFromNudge : 경합으로 DB 유일성 위반 → DuplicateRaidCardException (같은 번역 경로 공유)")
	void completeFromNudge_concurrentRace_translatesToDuplicate() {
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(false);
		given(raidCardRepository.saveAndFlush(any(RaidCard.class)))
				.willThrow(new DataIntegrityViolationException("Duplicate entry"));

		assertThatThrownBy(() -> raidCardService.completePendingCardFromNudge(session(Map.of(
				"vendor", "GIGABYTE", "modelName", "CRA3338",
				"supportedRaidLevels", "RAID0", "cacheCapacityGb", "0",
				"pciSubsystemId", "", "description", ""))))
				.isInstanceOf(DuplicateRaidCardException.class);
	}

	// ==== update =====================================================

	@Test
	@DisplayName("update : 모델명 유지 → 동일키 재검증 없이 필드 갱신 (PCI 빈 값 → 미확인 환원)")
	void update_sameName_updatesFields() {
		RaidCard card = savedCard(7L, RaidCardVendor.GIGABYTE, "CRA3338");
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(OptionalOf(card));

		raidCardService.update(7L, new RaidCardUpdateRequest(
				"CRA3338", List.of(RaidLevel.RAID0, RaidLevel.RAID1, RaidLevel.RAID5), 2, "", "updated"));

		assertThat(card.getSupportedRaidLevels().supports(RaidLevel.RAID5)).isTrue();
		assertThat(card.getCacheCapacity()).isEqualTo(CacheCapacity.ofGigabytes(2));
		assertThat(card.hasCache()).isTrue();
		assertThat(card.getPciSubsystemId()).isNull();
		verify(raidCardRepository, never())
				.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(any(), any());
	}

	@Test
	@DisplayName("update : 모델명 변경 + 살아 있는 동일키 존재 → DuplicateRaidCardException")
	void update_renameToOccupied_throws409() {
		RaidCard card = savedCard(7L, RaidCardVendor.GIGABYTE, "CRA3338");
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(OptionalOf(card));
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA4448")).willReturn(true);

		assertThatThrownBy(() -> raidCardService.update(7L, new RaidCardUpdateRequest(
				"CRA4448", List.of(RaidLevel.RAID0), 0, "", "")))
				.isInstanceOf(DuplicateRaidCardException.class);
		assertThat(card.getModelName()).isEqualTo("CRA3338");   // 갱신 미수행
	}

	// ==== nudge confirm 위임 =========================================

	@Test
	@DisplayName("completePendingCardFromNudge : attributes 왕복 → 살아 있는 카드로 저장")
	void completeFromNudge_persistsFromAttributes() {
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(false);
		given(raidCardRepository.saveAndFlush(any(RaidCard.class)))
				.willAnswer(inv -> {
					RaidCard toSave = inv.getArgument(0);
					assertThat(toSave.getSupportedRaidLevels().supports(RaidLevel.RAID1)).isTrue();
					assertThat(toSave.getCacheCapacity()).isEqualTo(CacheCapacity.ofGigabytes(2));
					assertThat(toSave.getPciSubsystemId().toDisplay()).isEqualTo("1458:0011");
					return savedCard(50L, toSave.getVendor(), toSave.getModelName());
				});

		Long id = raidCardService.completePendingCardFromNudge(session(Map.of(
				"vendor", "GIGABYTE", "modelName", "CRA3338",
				"supportedRaidLevels", "RAID0,RAID1", "cacheCapacityGb", "2",
				"pciSubsystemId", "1458:0011", "description", "d")));

		assertThat(id).isEqualTo(50L);
	}

	@Test
	@DisplayName("completePendingCardFromNudge : race 로 살아 있는 동일키 생김 → DuplicateRaidCardException")
	void completeFromNudge_race_throws409() {
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(true);

		assertThatThrownBy(() -> raidCardService.completePendingCardFromNudge(session(Map.of(
				"vendor", "GIGABYTE", "modelName", "CRA3338",
				"supportedRaidLevels", "RAID0", "cacheCapacityGb", "0",
				"pciSubsystemId", "", "description", ""))))
				.isInstanceOf(DuplicateRaidCardException.class);
	}

	@Test
	@DisplayName("purgeCardForNudge : 살아 있는 자원은 replace 대상 불가 → IllegalRaidCardStateException")
	void purgeForNudge_liveTarget_throws409() {
		RaidCard live = savedCard(9L, RaidCardVendor.GIGABYTE, "CRA3338");

		assertThatThrownBy(() -> raidCardService.purgeCardForNudge(live))
				.isInstanceOf(IllegalRaidCardStateException.class);
		verify(raidCardRepository, never()).delete(any(RaidCard.class));
	}

	// ==== fixture ====================================================

	private static NudgeSession session(Map<String, String> attributes) {
		return new NudgeSession(
				UUID.randomUUID(), NudgeResourceType.RAID_CARD, null, List.of(9L),
				new IntentMetaNudgePayload(attributes),
				Instant.now(), Instant.now().plusSeconds(300));
	}

	private static java.util.Optional<RaidCard> OptionalOf(RaidCard card) {
		return java.util.Optional.of(card);
	}
}
