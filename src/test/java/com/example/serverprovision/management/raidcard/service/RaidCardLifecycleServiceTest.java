package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.exception.IllegalRaidCardStateException;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * MA7 — {@link RaidCardLifecycleService} 단위 테스트.
 *
 * <p>부모 · 자식 없는 루트 메타 자원이라 cascade 검증이 없다. 핵심은 D7 유일성 어휘("살아 있는" =
 * 비삭제 · 비 Deprecated)의 귀결 — <b>restore / undeprecate 가 카드를 살아 있는 집합에 되돌리는
 * 전이</b>이며 그 시점의 동일키 충돌 가드가 BoardModel 에 없는 신설 분기다.</p>
 */
@ExtendWith(MockitoExtension.class)
class RaidCardLifecycleServiceTest {

	@Mock RaidCardRepository raidCardRepository;

	RaidCardLifecycleService raidCardService;

	@BeforeEach
	void initService() {
		raidCardService = new RaidCardLifecycleService(raidCardRepository);
	}

	// ==== helper =====================================================

	private RaidCard activeCard() {
		RaidCard card = RaidCard.builder()
				.id(7L).vendor(RaidCardVendor.GIGABYTE).modelName("CRA3338")
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1)))
				.cacheCapacity(CacheCapacity.NONE)
				.ownEnabled(true).ownDeprecated(false).isDeleted(false)
				.build();
		card.recomputeEffective();
		return card;
	}

	private RaidCard deletedCard(boolean ownDeprecated) {
		return RaidCard.builder()
				.id(7L).vendor(RaidCardVendor.GIGABYTE).modelName("CRA3338")
				.supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1)))
				.cacheCapacity(CacheCapacity.NONE)
				.ownEnabled(true).ownDeprecated(ownDeprecated).isDeleted(true)
				.build();
	}

	// ==== toggle =====================================================

	@Test
	@DisplayName("toggleEnabled : 활성 카드 own flip (true→false)")
	void toggleEnabled_flipsOwn() {
		RaidCard card = activeCard();
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));

		raidCardService.toggleEnabled(7L);

		assertThat(card.isEnabled()).isFalse();
	}

	@Test
	@DisplayName("toggleEnabled : 활성 카드 없음 → RaidCardNotFoundException (404)")
	void toggleEnabled_missing_throws404() {
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> raidCardService.toggleEnabled(7L))
				.isInstanceOf(RaidCardNotFoundException.class);
	}

	// ==== deprecate / undeprecate ====================================

	@Test
	@DisplayName("deprecate : 활성 카드 deprecated 전이 (deprecatedAt 기록)")
	void deprecate_marksDeprecated() {
		RaidCard card = activeCard();
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));

		raidCardService.deprecate(7L);

		assertThat(card.isDeprecated()).isTrue();
		assertThat(card.getDeprecatedAt()).isNotNull();
	}

	@Test
	@DisplayName("undeprecate : 동일키 살아 있는 카드 없음 → 해제 성공")
	void undeprecate_noConflict_succeeds() {
		RaidCard card = activeCard();
		card.deprecate();
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(false);

		raidCardService.undeprecate(7L);

		assertThat(card.isDeprecated()).isFalse();
	}

	/**
	 * D7 신설 가드 — Deprecated 해제는 카드를 유일성 집합에 되돌리는 전이. nudge PROCEED 가 남긴
	 * 구버전을 해제하려는데 같은 이름의 살아 있는 카드가 있으면, DB 유니크 위반(500)이 되기 전에
	 * 서비스가 친절한 409 를 만든다.
	 */
	@Test
	@DisplayName("undeprecate : 동일키 살아 있는 카드 존재 → IllegalRaidCardStateException (409, DB 유니크 선차단)")
	void undeprecate_conflict_throws409() {
		RaidCard card = activeCard();
		card.deprecate();
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(true);

		assertThatThrownBy(() -> raidCardService.undeprecate(7L))
				.isInstanceOf(IllegalRaidCardStateException.class)
				.hasMessageContaining("이미 살아 있어");
		assertThat(card.isDeprecated()).isTrue();   // 전이 미수행
	}

	// ==== softDelete =================================================

	@Test
	@DisplayName("softDelete : 삭제 전이 + trashedAt 기록 (메타 자원 — trashedPath 는 null)")
	void softDelete_markTrashed() {
		RaidCard card = activeCard();
		given(raidCardRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(card));

		raidCardService.softDelete(7L);

		assertThat(card.isDeleted()).isTrue();
		assertThat(card.getTrashedAt()).isNotNull();
		assertThat(card.getTrashedPath()).isNull();
	}

	// ==== restore ====================================================

	@Test
	@DisplayName("restore : 동일키 충돌 없음 → 복구 + trash 메타 초기화, cascade 무관 0건")
	void restore_succeeds() {
		RaidCard card = deletedCard(false);
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(card));
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(false);

		RestoreResponse response = raidCardService.restore(7L, true);

		assertThat(card.isDeleted()).isFalse();
		assertThat(card.getTrashedAt()).isNull();
		assertThat(response).isEqualTo(RestoreResponse.none());
	}

	@Test
	@DisplayName("restore : 삭제 상태 아님/부재 → IllegalRaidCardStateException (409)")
	void restore_notTrashed_throws409() {
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> raidCardService.restore(7L, false))
				.isInstanceOf(IllegalRaidCardStateException.class);
	}

	@Test
	@DisplayName("restore : 살아 있는 동일키 존재 + 비 Deprecated 복귀 → DuplicateRaidCardException (409)")
	void restore_conflict_throwsDuplicate() {
		RaidCard card = deletedCard(false);
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(card));
		given(raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				RaidCardVendor.GIGABYTE, "CRA3338")).willReturn(true);

		assertThatThrownBy(() -> raidCardService.restore(7L, false))
				.isInstanceOf(DuplicateRaidCardException.class);
		assertThat(card.isDeleted()).isTrue();   // 전이 미수행
	}

	/**
	 * D7 정합 — own_deprecated=true 로 복귀하는 카드는 유일성 집합 밖(active_identity=NULL)이라
	 * 동일키 살아 있는 카드가 있어도 충돌하지 않는다. 충돌 검사 자체를 생략한다(short-circuit).
	 */
	@Test
	@DisplayName("restore : Deprecated 로 복귀(own_deprecated=true) → 동일키 검사 생략하고 복구 성공")
	void restore_deprecatedComeback_skipsConflictCheck() {
		RaidCard card = deletedCard(true);
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(card));

		raidCardService.restore(7L, false);

		assertThat(card.isDeleted()).isFalse();
		assertThat(card.isDeprecated()).isTrue();   // own 보존 → effective deprecated 복귀
		verify(raidCardRepository, never())
				.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
						RaidCardVendor.GIGABYTE, "CRA3338");
	}

	// ==== purge ======================================================

	@Test
	@DisplayName("purge : soft-deleted 카드 → row 삭제 (참조 검사 없음 — D10 소프트참조 구조 확정)")
	void purge_deletesRow() {
		RaidCard card = deletedCard(false);
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(card));

		raidCardService.purge(7L);

		verify(raidCardRepository).delete(card);
	}

	@Test
	@DisplayName("purge : soft-deleted 상태 아님 → IllegalRaidCardStateException (409)")
	void purge_notTrashed_throws409() {
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> raidCardService.purge(7L))
				.isInstanceOf(IllegalRaidCardStateException.class);
		verify(raidCardRepository, never()).delete(org.mockito.ArgumentMatchers.any(RaidCard.class));
	}

	// ==== purgeWithTypedNameCheck ====================================

	@Test
	@DisplayName("purgeWithTypedNameCheck : typedName = displayName 일치 → 삭제")
	void purgeTypedName_match_deletes() {
		RaidCard card = deletedCard(false);
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(card));

		raidCardService.purgeWithTypedNameCheck(7L, "GIGABYTE CRA3338");

		verify(raidCardRepository).delete(card);
	}

	@Test
	@DisplayName("purgeWithTypedNameCheck : typedName 불일치 → TypedNameMismatchException (400), 삭제 미수행")
	void purgeTypedName_mismatch_throws400() {
		RaidCard card = deletedCard(false);
		given(raidCardRepository.findByIdAndIsDeletedTrue(7L)).willReturn(Optional.of(card));

		assertThatThrownBy(() -> raidCardService.purgeWithTypedNameCheck(7L, "GIGABYTE 9361-8i"))
				.isInstanceOf(TypedNameMismatchException.class);
		verify(raidCardRepository, never()).delete(org.mockito.ArgumentMatchers.any(RaidCard.class));
	}
}
