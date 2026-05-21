package com.example.serverprovision.management.board.service;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MK2 WAVE 1 — BoardModel (메타 단독) nudge confirm 처리.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardModelNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final BoardModelService boardModelService;
	private final BoardModelRepository boardModelRepository;

	@Transactional
	public Long proceed(UUID nudgeId) {
		NudgeSession session = requireBoardSession(nudgeId);
		Long id = boardModelService.completePendingBoardFromNudge(session);
		consumeSession(nudgeId);
		log.info("[boardNudge] proceed 완료. nudgeId={}, newId={}", nudgeId, id);
		return id;
	}

	@Transactional
	public Long replace(UUID nudgeId, Long targetId) {
		NudgeSession session = requireBoardSession(nudgeId);
		if (targetId == null || !session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		BoardModel target = boardModelRepository.findById(targetId)
				.orElseThrow(() -> new BoardModelNotFoundException(targetId));
		boardModelService.purgeBoardForNudge(target);
		Long newId = boardModelService.completePendingBoardFromNudge(session);
		consumeSession(nudgeId);
		log.info(
				"[boardNudge] replace 완료. nudgeId={}, purgedId={}, newId={}",
				nudgeId, targetId, newId
		);
		return newId;
	}

	public void cancel(UUID nudgeId) {
		requireBoardSession(nudgeId);
		consumeSession(nudgeId);
		log.info("[boardNudge] cancel 완료. nudgeId={}", nudgeId);
	}

	private NudgeSession requireBoardSession(UUID nudgeId) {
		NudgeSession session = nudgeRegistry.require(nudgeId);
		if (session.resourceType() != NudgeResourceType.BOARD_MODEL) {
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
