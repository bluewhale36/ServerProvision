package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.common.nudge.exception.InvalidReplaceTargetException;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.os.dto.request.OSMetadataCreateRequest;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.DuplicateOSMetadataException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.service.OSNudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MK2 WAVE 1 — OS_IMAGE (메타 단독) nudge confirm 처리.
 *
 * <p>{@link OSNudgeService} 가 OS_ISO (file payload + hash) 흐름 전용이라, 메타 단독인 OSMetadata 는
 * 본 별도 service 로 분리 — resourceType switch 분기 회피. 동일 confirm 패턴
 * (proceed / replace / cancel) 을 메타 단독 형태로 구현한다.</p>
 *
 * <p>R1-6 — OSMetadata 의 nudge 영속화 4 메서드 (completePendingFromNudge / purgeForNudge /
 * buildNudgePayload / persistNew) 를 본 service 로 흡수. 흡수 전에는 nudge 서비스가
 * {@link OSMetadataService} 를 역호출했으나, 흡수 후 {@link OSMetadataRepository} + {@link NudgeRegistry}
 * 직접 주입으로 self-contained 가 된다. 의존 그래프는 {@link OSMetadataService} → 본 service 단방향
 * (cycle 없음 — 본 service 는 부모 service 를 더 이상 모른다).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OSMetadataNudgeService {

	private final NudgeRegistry nudgeRegistry;
	private final OSMetadataRepository osMetadataRepository;
	// R1-6 — osMetadataService 역의존 제거. 부모 lookup 은 osMetadataRepository 직접.

	@Transactional
	public Long proceed(UUID nudgeId) {
		NudgeSession session = requireOSMetadataSession(nudgeId);
		Long id = completePendingFromNudge(session);   // R1-6 — 역호출 → 자기 호출
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
		purgeForNudge(target);                         // R1-6 — 자기 호출
		Long newId = completePendingFromNudge(session);
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

	// ---- R1-6 흡수 — nudge 영속화 (옛 OSMetadataService 에서 이동) ----------

	/**
	 * MK2 WAVE 1 — PROCEED — 충돌 후보 보존, 신규 자원만 ACTIVE 등록.
	 * 외부 endpoint 와 직접 묶이지 않은 내부 진입점. 호출자가 LifecycleStage 검증을 마쳤다고 가정.
	 */
	@Transactional
	public Long completePendingFromNudge(NudgeSession session) {
		if (!(session.payload() instanceof IntentMetaNudgePayload payload)) {
			throw new IllegalOSMetadataStateException(
					"OSMetadata nudge 세션은 IntentMetaNudgePayload 만 허용합니다. nudgeId=" + session.nudgeId());
		}
		OSName osName = OSName.valueOf(payload.attributes().get("osName"));
		String osVersion = payload.attributes().get("osVersion");
		// PROCEED — 활성 (osName, osVersion) 가 새로 생기면 안 된다 (race). 재검사.
		if (osMetadataRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(osName, osVersion)) {
			// race — 다른 트랜잭션이 같은 메타로 활성 자원을 만든 경우. 명시적 fail.
			throw new DuplicateOSMetadataException(osName, osVersion);
		}
		return persistNew(osName, osVersion, payload.attributes().get("description"));
	}

	/**
	 * MK2 WAVE 1 — REPLACE 흐름의 target purge. 본 메서드는 OSMetadata 자체의 soft-deleted row 만 hard-delete
	 * 한다 (자식 ISO 도 cascade — DB FK ON DELETE CASCADE 가 처리).
	 */
	@Transactional
	public void purgeForNudge(OSMetadata target) {
		if (!target.isDeleted() && !target.isDeprecated()) {
			throw new IllegalOSMetadataStateException(
					"활성 자원은 nudge replace 대상이 될 수 없습니다. id=" + target.getId());
		}
		osMetadataRepository.delete(target);
		log.info(
				"[osMetadata] purge for nudge replace : id={}, osName={}, osVersion={}",
				target.getId(), target.getOsName(), target.getOsVersion()
		);
	}

	/**
	 * R1-6 — create() 의 nudge 발화 시 세션 등록 + payload 조립. nudgeRegistry 직접 사용.
	 */
	public NudgeRequiredResponse buildNudgePayload(OSMetadataCreateRequest request, List<OSMetadata> candidates) {
		NudgeSession session = nudgeRegistry.register(
				NudgeResourceType.OS_IMAGE,
				null,
				candidates.stream().map(OSMetadata::getId).toList(),
				new IntentMetaNudgePayload(
						Map.of(
								"osName", request.osName().name(),
								"osVersion", request.osVersion(),
								"description", request.description() != null ? request.description() : ""
						)
				)
		);
		List<NudgeConflictEntry> entries = candidates.stream()
				.map(c -> new NudgeConflictEntry(
						c.getId(),
						LifecycleStage.of(c.isDeprecated(), c.isDeleted()),
						null,
						c.getOsName().name(),
						c.getOsVersion(),
						Instant.now()
				))
				.toList();
		log.info(
				"[osMetadata] nudge required : osName={}, osVersion={}, candidates={}",
				request.osName(), request.osVersion(), candidates.size()
		);
		return NudgeRequiredResponse.of(session.nudgeId(), entries, session.expiresAt());
	}

	/**
	 * R1-6 — 신규 OSMetadata row save. create() happy-path + completePendingFromNudge 두 곳이 공유 (중복 0).
	 */
	public Long persistNew(OSName osName, String osVersion, String description) {
		OSMetadata saved = osMetadataRepository.save(
				OSMetadata.builder()
						.osName(osName)
						.osVersion(osVersion)
						.description(description)
						.build()
		);
		return saved.getId();
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
