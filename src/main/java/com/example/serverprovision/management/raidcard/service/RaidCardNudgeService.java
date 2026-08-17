package com.example.serverprovision.management.raidcard.service;

import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RAID 카드 (메타 단독) nudge confirm 처리 (BoardModelNudgeService 선례).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RaidCardNudgeService {

	private final NudgeRegistry nudgeRegistry;
	// nudge confirm 의 실제 생성/교체는 Metadata 로 단방향 위임.
	private final RaidCardMetadataService raidCardMetadataService;
	private final RaidCardRepository raidCardRepository;

	@Transactional
	public Long proceed(UUID nudgeId) {
		NudgeSession session = requireRaidCardSession(nudgeId);
		Long id = raidCardMetadataService.completePendingCardFromNudge(session);
		consumeSession(nudgeId);
		log.info("[raidCardNudge] proceed 완료. nudgeId={}, newId={}", nudgeId, id);
		return id;
	}

	@Transactional
	public Long replace(UUID nudgeId, Long targetId) {
		NudgeSession session = requireRaidCardSession(nudgeId);
		if (targetId == null || !session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		RaidCard target = raidCardRepository.findById(targetId)
				.orElseThrow(() -> new RaidCardNotFoundException(targetId));
		raidCardMetadataService.purgeCardForNudge(target);
		Long newId = raidCardMetadataService.completePendingCardFromNudge(session);
		consumeSession(nudgeId);
		log.info(
				"[raidCardNudge] replace 완료. nudgeId={}, purgedId={}, newId={}",
				nudgeId, targetId, newId
		);
		return newId;
	}

	public void cancel(UUID nudgeId) {
		requireRaidCardSession(nudgeId);
		consumeSession(nudgeId);
		log.info("[raidCardNudge] cancel 완료. nudgeId={}", nudgeId);
	}

	private NudgeSession requireRaidCardSession(UUID nudgeId) {
		NudgeSession session = nudgeRegistry.require(nudgeId);
		if (session.resourceType() != NudgeResourceType.RAID_CARD) {
			throw new NudgeAlreadyResolvedException(nudgeId);
		}
		return session;
	}

	private void consumeSession(UUID nudgeId) {
		if (!nudgeRegistry.remove(nudgeId)) {
			throw new NudgeAlreadyResolvedException(nudgeId);
		}
	}
}
