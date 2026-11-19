package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.global.lifecycle.dto.ResourceListQuery;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardCreateRequest;
import com.example.serverprovision.management.raidcard.dto.request.RaidCardUpdateRequest;
import com.example.serverprovision.management.raidcard.dto.response.RaidCardVendorGroupResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.exception.IllegalRaidCardStateException;
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
 * MA7 · HF30-1 — {@link RaidCardMetadataService} 단위 테스트 (조회 · 등록 · 수정).
 *
 * <p>등록은 같은 (제조사, 모델명) 이 어느 상태로든 있으면 상태별 안내로 거절하고(HF30-1 — 중복 확인 창 제거), 없으면 저장한다.
 * PCI 선택 입력의 null 왕복도 본다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RaidCardMetadataServiceTest {

	@Mock RaidCardRepository raidCardRepository;

	RaidCardMetadataService raidCardService;

	@BeforeEach
	void initService() {
		raidCardService = new RaidCardMetadataService(raidCardRepository);
	}

	// ==== helper =====================================================

	private static RaidCard savedCard(Long id, RaidCardVendor vendor, String modelName) {
		RaidCard card = RaidCard.builder()
				.id(id).vendor(vendor).modelName(modelName)
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1)))
				.cacheCapacity(CacheCapacity.NONE).chipFamily(com.example.serverprovision.management.raidcard.enums.RaidChipFamily.MEGARAID)
				.ownEnabled(true).ownDeprecated(false).isDeleted(false)
				.build();
		card.recomputeEffective();
		return card;
	}

	private static RaidCard cardIn(Long id, String modelName, boolean deprecated, boolean deleted) {
		RaidCard card = RaidCard.builder()
				.id(id).vendor(RaidCardVendor.GIGABYTE).modelName(modelName)
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0)))
				.cacheCapacity(CacheCapacity.NONE).chipFamily(com.example.serverprovision.management.raidcard.enums.RaidChipFamily.MEGARAID).ownEnabled(true).ownDeprecated(deprecated).isDeleted(deleted)
				.build();
		card.recomputeEffective();
		return card;
	}

	private static java.util.Optional<RaidCard> OptionalOf(RaidCard card) {
		return java.util.Optional.of(card);
	}

	private static RaidCardCreateRequest createRequest(String pci) {
		return new RaidCardCreateRequest(
				RaidCardVendor.GIGABYTE, "CRA3338", RaidChipFamily.MPT_IR,
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

		List<RaidCardVendorGroupResponse> groups = raidCardService.findAllGrouped(ResourceListQuery.unfiltered(false));

		assertThat(groups).hasSize(2);
		assertThat(groups.get(0).items().get(0).supportedRaidLevels())
				.containsExactly(RaidLevel.RAID0, RaidLevel.RAID1);
		assertThat(groups.get(0).items().get(0).supportedRaidLevelsDisplay()).isEqualTo("RAID0, RAID1");
	}

	// ==== create =====================================================

	@Test
	@DisplayName("create : 충돌 없음 + PCI 입력 → 저장 (VO 정규화 왕복)")
	void create_success_withPci() {
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(List.of());
		given(raidCardRepository.saveAndFlush(any(RaidCard.class)))
				.willReturn(savedCard(42L, RaidCardVendor.GIGABYTE, "CRA3338"));

		Long id = raidCardService.create(createRequest("0x1458:0x0011"));

		assertThat(id).isEqualTo(42L);
	}

	@Test
	@DisplayName("create : PCI 빈 문자열 → null(미확인)로 저장")
	void create_blankPci_storesNull() {
		given(raidCardRepository.findAllByVendorAndModelName(any(), any())).willReturn(List.of());
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
	@DisplayName("create : 살아 있는 동일키 → DuplicateRaidCardException '이미 등록된 RAID 카드입니다' (저장 안 함)")
	void create_liveDuplicate_throws409() {
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA3338"))
				.willReturn(List.of(cardIn(5L, "CRA3338", false, false)));

		assertThatThrownBy(() -> raidCardService.create(createRequest("")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessage("이미 등록된 RAID 카드입니다. GIGABYTE CRA3338");
		verify(raidCardRepository, never()).saveAndFlush(any(RaidCard.class));
	}

	@Test
	@DisplayName("create(HF30-1) : Deprecated 동일키 → 'Deprecated 를 해제' 안내 409 (종전에는 창이 떠 같은 이름 카드가 하나 더 생겼다)")
	void create_deprecatedDuplicate_guidesUndeprecate() {
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA3338"))
				.willReturn(List.of(cardIn(6L, "CRA3338", true, false)));

		assertThatThrownBy(() -> raidCardService.create(createRequest("")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessage("사용 중단 권고 상태인 같은 RAID 카드가 있습니다(GIGABYTE CRA3338). 목록에서 사용 중단 권고를 해제해야 합니다.");
		verify(raidCardRepository, never()).saveAndFlush(any(RaidCard.class));
	}

	@Test
	@DisplayName("create(HF30-1) : 휴지통 동일키 → '복구하거나 영구 삭제한 뒤 등록' 안내 409")
	void create_trashedDuplicate_guidesRestoreOrPurge() {
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA3338"))
				.willReturn(List.of(cardIn(9L, "CRA3338", false, true)));

		assertThatThrownBy(() -> raidCardService.create(createRequest("1458:0011")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessage("휴지통에 같은 RAID 카드가 있습니다(GIGABYTE CRA3338). 휴지통에서 복구하거나 영구 삭제한 뒤 등록해야 합니다.");
		verify(raidCardRepository, never()).saveAndFlush(any(RaidCard.class));
	}

	@Test
	@DisplayName("create(HF30-1) : UNIQUE 이전 옛 데이터의 다건(휴지통 + Deprecated) → 가장 살아 있는 Deprecated 기준 안내")
	void create_legacyMultipleRows_guidesByMostAlive() {
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA3338"))
				.willReturn(List.of(cardIn(9L, "CRA3338", false, true), cardIn(6L, "CRA3338", true, false)));

		assertThatThrownBy(() -> raidCardService.create(createRequest("")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessageStartingWith("사용 중단 권고 상태인 같은 RAID 카드가 있습니다");
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
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA3338"))
				.willReturn(List.of());   // 경합 상대가 아직 커밋 전 — 검사 통과
		given(raidCardRepository.saveAndFlush(any(RaidCard.class)))
				.willThrow(new DataIntegrityViolationException(
						"Duplicate entry 'GIGABYTE-CRA3338' for key 'uk_raid_card_vendor_model_name'"));

		assertThatThrownBy(() -> raidCardService.create(createRequest("")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessageContaining("이미 등록된 RAID 카드입니다");
	}

	// ==== update =====================================================

	@Test
	@DisplayName("update : 모델명 유지 → 동일키 재검증 없이 필드 갱신 (PCI 빈 값 → 미확인 환원)")
	void update_sameName_updatesFields() {
		RaidCard card = savedCard(7L, RaidCardVendor.GIGABYTE, "CRA3338");
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(OptionalOf(card));

		raidCardService.update(7L, new RaidCardUpdateRequest(
				"CRA3338", RaidChipFamily.MPT_IR, List.of(RaidLevel.RAID0, RaidLevel.RAID1, RaidLevel.RAID5), 2, "", "updated"));

		assertThat(card.getSupportedRaidLevels().supports(RaidLevel.RAID5)).isTrue();
		assertThat(card.getChipFamily()).isEqualTo(RaidChipFamily.MPT_IR);   // E3.5-6 — 계열 반영
		assertThat(card.getCacheCapacity()).isEqualTo(CacheCapacity.ofGigabytes(2));
		assertThat(card.hasCache()).isTrue();
		assertThat(card.getPciSubsystemId()).isNull();
		verify(raidCardRepository, never()).findAllByVendorAndModelName(any(), any());
	}

	@Test
	@DisplayName("update : 모델명 변경 + 살아 있는 동일키 존재 → DuplicateRaidCardException")
	void update_renameToOccupied_throws409() {
		RaidCard card = savedCard(7L, RaidCardVendor.GIGABYTE, "CRA3338");
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(OptionalOf(card));
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA4448"))
				.willReturn(List.of(cardIn(8L, "CRA4448", false, false)));

		assertThatThrownBy(() -> raidCardService.update(7L, new RaidCardUpdateRequest(
				"CRA4448", RaidChipFamily.MPT_IR, List.of(RaidLevel.RAID0), 0, "", "")))
				.isInstanceOf(DuplicateRaidCardException.class);
		assertThat(card.getModelName()).isEqualTo("CRA3338");   // 갱신 미수행
	}

	@Test
	@DisplayName("update(HF30-1) : 휴지통 카드의 이름으로 변경 → 이름 수정 안내 409 (종전에는 살아 있는 카드만 봐서 DB UNIQUE 500)")
	void update_renameToTrashed_guidesRename() {
		RaidCard card = savedCard(7L, RaidCardVendor.GIGABYTE, "CRA3338");
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(OptionalOf(card));
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA4448"))
				.willReturn(List.of(cardIn(8L, "CRA4448", false, true)));

		assertThatThrownBy(() -> raidCardService.update(7L, new RaidCardUpdateRequest(
				"CRA4448", RaidChipFamily.MPT_IR, List.of(RaidLevel.RAID0), 0, "", "")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessage("휴지통에 같은 이름의 RAID 카드가 있습니다(GIGABYTE CRA4448). 다른 이름을 쓰거나 휴지통의 카드를 영구 삭제해야 합니다.");
		assertThat(card.getModelName()).isEqualTo("CRA3338");
	}

	@Test
	@DisplayName("update(HF30-1 CP5 B12) : 두 카드를 동시에 같은 새 이름으로 바꾼 경합 → flush 의 DB UNIQUE 위반을 Duplicate(활성 문구)로 번역 — 500 으로 새지 않는다")
	void update_concurrentRenameRace_translatesToDuplicate() {
		RaidCard card = savedCard(5L, RaidCardVendor.GIGABYTE, "CRA7777");
		given(raidCardRepository.findByIdAndIsDeletedFalse(5L)).willReturn(OptionalOf(card));
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA6666"))
				.willReturn(List.of());   // 경합 상대가 아직 커밋 전 — 검사 통과
		org.mockito.BDDMockito.willThrow(new DataIntegrityViolationException(
						"Duplicate entry 'GIGABYTE-CRA6666' for key 'uk_raid_card_vendor_model_name'"))
				.given(raidCardRepository).flush();

		assertThatThrownBy(() -> raidCardService.update(5L, new RaidCardUpdateRequest(
				"CRA6666", RaidChipFamily.MPT_IR, List.of(RaidLevel.RAID0), 0, "", "")))
				.isInstanceOf(DuplicateRaidCardException.class)
				.hasMessage("이미 등록된 RAID 카드입니다. GIGABYTE CRA6666");
	}

	@Test
	@DisplayName("renameConflictReason(HF30-1) : 수정 폼 사전 판정 — 같은 이름 그대로면 null, 휴지통 이름이면 update 와 같은 문구")
	void renameConflictReason_sharesUpdateGuard() {
		RaidCard card = savedCard(7L, RaidCardVendor.GIGABYTE, "CRA3338");
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(OptionalOf(card));
		given(raidCardRepository.findAllByVendorAndModelName(RaidCardVendor.GIGABYTE, "CRA4448"))
				.willReturn(List.of(cardIn(8L, "CRA4448", false, true)));

		assertThat(raidCardService.renameConflictReason(7L, "CRA3338")).isNull();
		assertThat(raidCardService.renameConflictReason(7L, "CRA4448"))
				.isEqualTo("휴지통에 같은 이름의 RAID 카드가 있습니다(GIGABYTE CRA4448). 다른 이름을 쓰거나 휴지통의 카드를 영구 삭제해야 합니다.");
	}

	@Test
	@DisplayName("findAllGrouped(S8-3) : PCI Subsystem ID(표시형)로 찾는다 · 맞는 카드가 없는 제조사는 묶음이 없다")
	void findAllGrouped_searchByPciSubsystemId() {
		RaidCard giga = savedCard(1L, RaidCardVendor.GIGABYTE, "CRA3338");
		RaidCard avago = RaidCard.builder()
				.id(2L).vendor(RaidCardVendor.AVAGO).modelName("9361-8i")
				.pciSubsystemId(com.example.serverprovision.management.raidcard.vo.PciSubsystemId.parse("1000:9361"))
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0)))
				.cacheCapacity(CacheCapacity.NONE).chipFamily(com.example.serverprovision.management.raidcard.enums.RaidChipFamily.MEGARAID)
				.ownEnabled(true).ownDeprecated(false).isDeleted(false)
				.build();
		avago.recomputeEffective();
		given(raidCardRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc()).willReturn(List.of(giga, avago));

		List<RaidCardVendorGroupResponse> groups =
				raidCardService.findAllGrouped(new ResourceListQuery("1000:9361", null, null, false));

		assertThat(groups).hasSize(1);
		assertThat(groups.get(0).items()).extracting(r -> r.id()).containsExactly(2L);
	}
}
