package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.management.os.dto.response.OrphanIsoQuarantineResponse;
import com.example.serverprovision.management.os.dto.response.OrphanRetryResponse;
import com.example.serverprovision.management.os.entity.OrphanIsoQuarantine;
import com.example.serverprovision.management.os.enums.OrphanFailureClass;
import com.example.serverprovision.management.os.enums.OrphanRecoveryState;
import com.example.serverprovision.management.os.exception.OrphanRecoveryAlreadyResolvedException;
import com.example.serverprovision.management.os.exception.OrphanRecoveryNotFoundException;
import com.example.serverprovision.management.os.repository.OrphanIsoQuarantineRepository;
import com.example.serverprovision.management.os.service.iso.IsoRegistrationService.PreparedIsoRegistration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 인프라/일시 실패로 좌절된 ISO 등록의 recoverable-saga 처리.
 * <p>대용량 업로드를 silently 잃지 않도록, 인프라 실패 시 파일을 삭제하지 않고 active 트리 밖으로 <b>격리</b>하고
 * durable 레코드({@link OrphanIsoQuarantine})를 남겨 사용자가 <b>재시도/폐기</b> 를 결정하게 한다.</p>
 *
 * <p>콘텐츠/영구 실패(해시 불일치·중복)는 본 경로로 오지 않는다 — finalize 가 이미 파일을 정리한다.</p>
 */
@Slf4j
@Service
public class OrphanIsoRecoveryService {

	private final OrphanIsoQuarantineRepository repository;
	private final TrashService trashService;
	// @Lazy : IsoRegistrationLauncher → IsoRegistrationRunner → OrphanIsoRecoveryService → Launcher
	// 생성자-주입 순환 차단. retry() 만 launcher.startRegistration 을 호출하므로(recordOrphan/discard 불요)
	// 첫 호출까지 빈 해석을 지연한다. (BoardModelService / SoftDeleteIntentService 와 동일 패턴.)
	private final IsoRegistrationLauncher launcher;
	private final ProvisionMarkerService markerService;

	/** 격리 디렉토리 root. 운영은 PROVISION_ISO_QUARANTINE_ROOT 절대경로로 override. */
	@Value("${iso.quarantine.root:/opt/provisioning/.iso-quarantine}")
	private String quarantineRoot;

	/** PENDING 격리 자동 폐기 TTL(일). trash(30일)보다 짧게. */
	@Value("${iso.quarantine.ttl-days:7}")
	private long ttlDays;

	// 명시 생성자 — launcher 만 @Lazy 로 주입해 위 순환을 끊는다(lombok.config 부재로 필드 @Lazy 전파 불가).
	public OrphanIsoRecoveryService(
			OrphanIsoQuarantineRepository repository,
			TrashService trashService,
			@org.springframework.context.annotation.Lazy IsoRegistrationLauncher launcher,
			ProvisionMarkerService markerService
	) {
		this.repository = repository;
		this.trashService = trashService;
		this.launcher = launcher;
		this.markerService = markerService;
	}

	/**
	 * 오펀 ISO 를 격리하고 durable 레코드를 남긴다. async runner 의 INFRA/TRANSIENT catch 에서 호출.
	 *
	 * @return recoveryId — job fail 메시지 marker / 모달 / 복구 endpoint 의 키
	 */
	@Transactional
	public String recordOrphan(PreparedIsoRegistration prepared, OrphanFailureClass failureClass,
	                           String exceptionDetail, String jobId) {
		String recoveryId = UUID.randomUUID().toString();
		Path resolved = Path.of(prepared.resolvedPath());
		String originalName = originalNameOf(prepared, resolved);
		String quarantinePath = null;

		// '우리가 업로드한 파일' 이고 실제 존재할 때만 격리. in-place(운영자 자산)는 옮기지 않는다.
		if (prepared.uploadedFile() && Files.exists(resolved)) {
			try {
				Path dest = Path.of(quarantineRoot, recoveryId, originalName);
				Files.createDirectories(dest.getParent());
				trashService.relocate(resolved, dest); // 검증된 mv(3회 retry) 재사용
				quarantinePath = dest.toString();
				// 반쯤 쓰인 sidecar(롤백된 isoId 참조)는 무의미 — 제거.
				deleteQuietly(markerService.resolveMarkerFile(resolved, MarkerLayout.SIDECAR));
			} catch (IOException | RuntimeException relocateError) {
				// 격리 이동 실패(디스크 풀 등) — 절대 삭제하지 않고 파일은 원위치 유지(degraded). 재시도는 제자리에서.
				log.error("[orphan] 격리 이동 실패 — 파일 원위치 유지. recoveryId={}, path={}",
						recoveryId, prepared.resolvedPath(), relocateError);
			}
		}

		OrphanIsoQuarantine row = OrphanIsoQuarantine.builder()
				.recoveryId(recoveryId)
				.osMetadataId(prepared.osMetadataId())
				.resolvedPath(prepared.resolvedPath())
				.quarantinePath(quarantinePath)
				.originalFilename(originalName)
				.description(prepared.description())
				.clientHash(prepared.clientHash())
				.registerExisting(!prepared.uploadedFile())
				.failureClass(failureClass)
				.exceptionDetail(truncate(exceptionDetail, 2000))
				.state(OrphanRecoveryState.PENDING)
				.retryCount(0)
				.jobId(jobId)
				.build();
		repository.save(row);
		log.warn("[orphan] 격리 기록. recoveryId={}, failureClass={}, quarantined={}, path={}",
				recoveryId, failureClass, quarantinePath != null, prepared.resolvedPath());
		return recoveryId;
	}

	/** 재시도 — 격리 파일을 active 경로로 복원하고 새 등록 job 을 시작한다. 행은 RECOVERED 로 소비. */
	@Transactional
	public OrphanRetryResponse retry(String recoveryId) {
		OrphanIsoQuarantine row = require(recoveryId);
		ensurePending(row);
		if (row.getQuarantinePath() != null) {
			trashService.relocate(Path.of(row.getQuarantinePath()), Path.of(row.getResolvedPath()));
		}
		PreparedIsoRegistration prepared = new PreparedIsoRegistration(
				row.getOsMetadataId(), row.getResolvedPath(), row.getDescription(),
				row.getOriginalFilename(), !row.isRegisterExisting(), row.getClientHash());
		row.markRecovered();
		String jobId = launcher.startRegistration(prepared);
		log.info("[orphan] 재시도 — 새 등록 job 시작. recoveryId={}, newJobId={}", recoveryId, jobId);
		return new OrphanRetryResponse(jobId, "/management/os?selectId=" + row.getOsMetadataId());
	}

	/** 폐기 — 격리 파일 삭제 + 행 DISCARDED. typedName(파일명) 일치 확인(파괴적 작업 가드). */
	@Transactional
	public void discard(String recoveryId, String typedName) {
		OrphanIsoQuarantine row = require(recoveryId);
		ensurePending(row);
		if (typedName == null || !typedName.equals(row.getOriginalFilename())) {
			throw new TypedNameMismatchException(row.getOriginalFilename(), typedName);
		}
		// 격리 파일만 삭제. registerExisting(운영자 기존 파일)은 격리하지 않았으므로 보존.
		if (row.getQuarantinePath() != null) {
			deleteQuietly(Path.of(row.getQuarantinePath()));
		}
		row.markDiscarded();
		log.info("[orphan] 폐기. recoveryId={}", recoveryId);
	}

	@Transactional(readOnly = true)
	public List<OrphanIsoQuarantineResponse> listPending() {
		return repository.findByStateOrderByCreatedAtDesc(OrphanRecoveryState.PENDING).stream()
				.map(OrphanIsoQuarantineResponse::from)
				.toList();
	}

	/** 단일 격리 레코드 조회 — 복구 모달이 recoveryId 로 상세를 채울 때 사용. */
	@Transactional(readOnly = true)
	public OrphanIsoQuarantineResponse get(String recoveryId) {
		return OrphanIsoQuarantineResponse.from(require(recoveryId));
	}

	/**
	 * TTL 만료된 PENDING 격리를 자동 폐기한다(파일 삭제 + DISCARDED). reaper(@Scheduled) 가 호출.
	 * 사용자 폐기와 달리 typed-name 가드는 없다 — 자동화 경로이며 만료가 의도 검증을 대신한다.
	 *
	 * @return 폐기한 건수
	 */
	@Transactional
	public int purgeExpired() {
		LocalDateTime threshold = LocalDateTime.now().minusDays(ttlDays);
		List<OrphanIsoQuarantine> expired =
				repository.findByStateAndCreatedAtBefore(OrphanRecoveryState.PENDING, threshold);
		for (OrphanIsoQuarantine row : expired) {
			if (row.getQuarantinePath() != null) {
				deleteQuietly(Path.of(row.getQuarantinePath()));
			}
			row.markDiscarded();
		}
		if (!expired.isEmpty()) {
			log.info("[orphan] TTL 만료 격리 {} 건 자동 폐기. thresholdDays={}", expired.size(), ttlDays);
		}
		return expired.size();
	}

	private OrphanIsoQuarantine require(String recoveryId) {
		return repository.findByRecoveryId(recoveryId)
				.orElseThrow(() -> new OrphanRecoveryNotFoundException(recoveryId));
	}

	private void ensurePending(OrphanIsoQuarantine row) {
		if (!row.isPending()) {
			throw new OrphanRecoveryAlreadyResolvedException(row.getRecoveryId(), row.getState().name());
		}
	}

	private static String originalNameOf(PreparedIsoRegistration prepared, Path resolved) {
		if (prepared.originalFilename() != null && !prepared.originalFilename().isBlank()) {
			return prepared.originalFilename();
		}
		return resolved.getFileName() != null ? resolved.getFileName().toString() : "unknown.iso";
	}

	private static String truncate(String s, int max) {
		return (s == null || s.length() <= max) ? s : s.substring(0, max);
	}

	private void deleteQuietly(Path p) {
		try {
			Files.deleteIfExists(p);
		} catch (IOException ignore) {
			// 격리/폐기 정리 실패는 핵심 흐름을 막지 않는다.
		}
	}
}
