package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.exception.OSImageNotFoundException;
import com.example.serverprovision.management.os.repository.OSImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MK2 WAVE 1 — OS_IMAGE (메타 단독) nudge confirm 처리.
 *
 * <p>{@link OsNudgeService} 가 OS_ISO (file payload + hash) 흐름 전용이라, 메타 단독인 OSImage 는
 * 본 별도 service 로 분리 — resourceType switch 분기 회피. 동일 confirm 패턴
 * (proceed / replace / cancel) 을 메타 단독 형태로 구현한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OSImageNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final OSImageService osImageService;
	private final OSImageRepository osImageRepository;

	@Transactional
	public Long proceed(UUID nudgeId) {
		NudgeSession session = requireOSImageSession(nudgeId);
		Long id = osImageService.completePendingOSImageFromNudge(session);
		consumeSession(nudgeId);
		log.info("[osImageNudge] proceed 완료. nudgeId={}, newId={}", nudgeId, id);
		return id;
	}

	@Transactional
	public Long replace(UUID nudgeId, Long targetId) {
		NudgeSession session = requireOSImageSession(nudgeId);
		if (targetId == null || !session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		OSImage target = osImageRepository.findById(targetId)
				.orElseThrow(() -> new OSImageNotFoundException(targetId));
		osImageService.purgeOSImageForNudge(target);
		Long newId = osImageService.completePendingOSImageFromNudge(session);
		consumeSession(nudgeId);
		log.info(
				"[osImageNudge] replace 완료. nudgeId={}, purgedId={}, newId={}",
				nudgeId, targetId, newId
		);
		return newId;
	}

	public void cancel(UUID nudgeId) {
		// 메타 단독 — 정리할 임시 파일 없음. 세션만 회수.
		requireOSImageSession(nudgeId);
		consumeSession(nudgeId);
		log.info("[osImageNudge] cancel 완료. nudgeId={}", nudgeId);
	}

	// ---- 내부 헬퍼 -----------------------------------------------------

	private NudgeSession requireOSImageSession(UUID nudgeId) {
		NudgeSession session = nudgeRegistry.require(nudgeId);
		if (session.resourceType() != NudgeResourceType.OS_IMAGE) {
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
