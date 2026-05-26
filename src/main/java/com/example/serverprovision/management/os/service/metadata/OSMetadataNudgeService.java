package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.management.os.service.OSNudgeService;

import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * MK2 WAVE 1 — OS_IMAGE (메타 단독) nudge confirm 처리.
 *
 * <p>{@link OSNudgeService} 가 OS_ISO (file payload + hash) 흐름 전용이라, 메타 단독인 OSMetadata 는
 * 본 별도 service 로 분리 — resourceType switch 분기 회피. 동일 confirm 패턴
 * (proceed / replace / cancel) 을 메타 단독 형태로 구현한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OSMetadataNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final OSMetadataService osMetadataService;
	private final OSMetadataRepository osMetadataRepository;

	@Transactional
	public Long proceed(UUID nudgeId) {
		NudgeSession session = requireOSMetadataSession(nudgeId);
		Long id = osMetadataService.completePendingOSMetadataFromNudge(session);
		consumeSession(nudgeId);
		log.info("[osMetadataNudge] proceed 완료. nudgeId={}, newId={}", nudgeId, id);
		return id;
	}

	@Transactional
	public Long replace(UUID nudgeId, Long targetId) {
		NudgeSession session = requireOSMetadataSession(nudgeId);
		if (targetId == null || !session.conflictTargetIds().contains(targetId)) {
			throw new InvalidReplaceTargetException(targetId);
		}
		OSMetadata target = osMetadataRepository.findById(targetId)
				.orElseThrow(() -> new OSMetadataNotFoundException(targetId));
		osMetadataService.purgeOSMetadataForNudge(target);
		Long newId = osMetadataService.completePendingOSMetadataFromNudge(session);
		consumeSession(nudgeId);
		log.info(
				"[osMetadataNudge] replace 완료. nudgeId={}, purgedId={}, newId={}",
				nudgeId, targetId, newId
		);
		return newId;
	}

	public void cancel(UUID nudgeId) {
		// 메타 단독 — 정리할 임시 파일 없음. 세션만 회수.
		requireOSMetadataSession(nudgeId);
		consumeSession(nudgeId);
		log.info("[osMetadataNudge] cancel 완료. nudgeId={}", nudgeId);
	}

	// ---- 내부 헬퍼 -----------------------------------------------------

	private NudgeSession requireOSMetadataSession(UUID nudgeId) {
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
