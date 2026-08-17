package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;

/**
 * Metadata / Lifecycle 두 서비스가 공유하는 활성 카드 조회 가드 (BoardModelGuards 선례).
 */
final class RaidCardGuards {

	private RaidCardGuards() {
	}

	/** 활성(soft-deleted 아님) 카드를 조회하거나 없으면 404. */
	static RaidCard requireActiveCard(RaidCardRepository repository, Long id) {
		return repository.findByIdAndIsDeletedFalse(id)
				.orElseThrow(() -> new RaidCardNotFoundException(id));
	}
}
