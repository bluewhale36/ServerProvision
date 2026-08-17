package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.global.lifecycle.LifecycleService;
import com.example.serverprovision.global.trash.service.TypedNameGuard;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.exception.DuplicateRaidCardException;
import com.example.serverprovision.management.raidcard.exception.IllegalRaidCardStateException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RAID 카드 lifecycle 상태 전이 (MA7). {@link LifecycleService} 구현.
 *
 * <p>부모 · 자식 자원이 없는 루트 메타 자원이라 cascade 가 전무하다 — BoardModel 의
 * {@code BoardScopedChildLifecycle} 순회에 해당하는 것이 없고, restore 의 cascade 옵션은
 * 자식 0 복구로 자연 처리한다.</p>
 *
 * <p>D7 유일성 어휘("살아 있는" = 비삭제 · 비 Deprecated)의 귀결로 <b>restore / undeprecate 가
 * 카드를 살아 있는 상태로 되돌리는 전이</b>이며, 그 시점에 동일키 살아 있는 카드가 이미 있으면
 * DB 생성 컬럼 유니크 인덱스가 거절한다. 서비스 가드가 먼저 같은 조건을 검사해 친절한 409 를
 * 만든다 — 두 가드는 같은 조건을 본다(DB 는 동시성 최종 판정).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RaidCardLifecycleService implements LifecycleService {

	private final RaidCardRepository raidCardRepository;

	@Override
	@Transactional
	public void toggleEnabled(Long id) {
		RaidCard card = RaidCardGuards.requireActiveCard(raidCardRepository, id);
		card.toggleEnabled();
		log.info("[lifecycle.toggle] resource=RAID_CARD#{} enabled={} outcome=toggled", id, card.isEnabled());
	}

	@Override
	@Transactional
	public void deprecate(Long id) {
		RaidCard card = RaidCardGuards.requireActiveCard(raidCardRepository, id);
		card.deprecate();
		log.info("[lifecycle.deprecate] resource=RAID_CARD#{} outcome=deprecated", id);
	}

	/**
	 * Deprecated 해제 — 카드가 다시 "살아 있는" 유일성 집합에 들어가는 전이.
	 * <p>nudge PROCEED 가 남긴 구버전을 해제하는 경우처럼 동일키 살아 있는 카드가 이미 있으면
	 * 유니크 인덱스 위반(500)이 되므로, 같은 조건의 서비스 가드로 먼저 409 를 만든다.</p>
	 */
	@Override
	@Transactional
	public void undeprecate(Long id) {
		RaidCard card = RaidCardGuards.requireActiveCard(raidCardRepository, id);
		if (raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
				card.getVendor(), card.getModelName())) {
			throw new IllegalRaidCardStateException(
					"같은 이름의 카드가 이미 살아 있어 Deprecated 를 해제할 수 없습니다. "
							+ "그 카드를 먼저 삭제하거나 Deprecated 로 표시해주세요. id=" + id);
		}
		card.undeprecate();
		log.info("[lifecycle.undeprecate] resource=RAID_CARD#{} outcome=undeprecated", id);
	}

	@Override
	@Transactional
	public void softDelete(Long id) {
		RaidCard card = RaidCardGuards.requireActiveCard(raidCardRepository, id);
		// 메타 자원 — 파일 trash 이동 없음, lifecycle 메타만 갱신 (BoardModel 선례).
		card.softDelete();
		card.markTrashed(null);
		log.info("[lifecycle.softDelete] resource=RAID_CARD#{} outcome=trashed", id);
	}

	@Override
	@Transactional
	public RestoreResponse restore(Long id, boolean cascade) {
		RaidCard card = raidCardRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalRaidCardStateException(
						"이미 활성 상태이거나 존재하지 않는 RAID 카드입니다. id=" + id));
		// 복구로 살아 있는 상태가 되는 경우(own_deprecated=false)만 동일키 충돌 검사 —
		// Deprecated 로 복귀하는 카드는 유일성 집합 밖이라 충돌하지 않는다 (D7).
		if (!card.isOwnDeprecated()
				&& raidCardRepository.existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(
						card.getVendor(), card.getModelName())) {
			throw new DuplicateRaidCardException(card.getVendor(), card.getModelName());
		}
		card.restore();
		card.clearTrashed();
		log.info("[lifecycle.restore] resource=RAID_CARD#{} outcome=restored", id);
		return RestoreResponse.none();   // 자식 없음 — cascade 무관 0 건
	}

	@Override
	@Transactional
	public void purgeWithTypedNameCheck(Long id, String typedName) {
		RaidCard card = requireTrashedCard(id);
		TypedNameGuard.verify(card, typedName);
		purge(card);
	}

	@Override
	@Transactional
	public void purge(Long id) {
		purge(requireTrashedCard(id));
	}

	/**
	 * 공통 hard-delete 본체 — 참조 검사 없음(MA7 D10). 정의서의 카드 참조는 JSON payload 안의
	 * 소프트참조라 구조적으로 FK 가 없고, 사라진 카드를 가리키는 정의서의 화면 처리는 U4-1-1 소관.
	 */
	private void purge(RaidCard card) {
		raidCardRepository.delete(card);
		log.info("[lifecycle.purge] resource=RAID_CARD#{} outcome=purged", card.getId());
	}

	private RaidCard requireTrashedCard(Long id) {
		return raidCardRepository.findByIdAndIsDeletedTrue(id)
				.orElseThrow(() -> new IllegalRaidCardStateException(
						"soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. id=" + id));
	}
}
