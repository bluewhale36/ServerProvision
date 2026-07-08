package com.example.serverprovision.global.orphan.service;

import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.orphan.OrphanQuarantineRequest;
import com.example.serverprovision.global.orphan.dto.OrphanQuarantineResponse;
import com.example.serverprovision.global.orphan.entity.OrphanQuarantine;
import com.example.serverprovision.global.orphan.enums.OrphanRecoveryState;
import com.example.serverprovision.global.orphan.repository.OrphanQuarantineRepository;
import com.example.serverprovision.global.trash.TrashService;
import lombok.RequiredArgsConstructor;
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
 * 오펀 자원 격리 saga 의 <b>write-side</b> (도메인 무관). 인프라/일시 실패 시 파일을 active 트리 밖으로 격리하고
 * durable 레코드를 남기며(record), TTL 만료 격리를 자동 폐기하고(purgeExpired), 조회를 제공한다(listPending/get).
 *
 * <p>R1-4-4 핵심 (@Lazy 제거) : 본 서비스는 도메인 launcher 에 의존하지 않는다. 따라서 기존
 * {@code Launcher→Runner→OrphanIsoRecoveryService→Launcher} 순환의 한 변이 끊겨 {@code @Lazy} 가 사라진다.
 * 재등록(launcher 필요)은 {@link OrphanRecoveryService} 가 SPI 를 통해 담당한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanQuarantineService {

	private final OrphanQuarantineRepository repository;
	private final TrashService trashService;
	private final ProvisionMarkerService markerService;

	/** 격리 디렉토리 root. 운영은 PROVISION_ISO_QUARANTINE_ROOT 절대경로로 override. */
	@Value("${orphan.quarantine.root:/opt/provisioning/.iso-quarantine}")
	private String quarantineRoot;

	/** PENDING 격리 자동 폐기 TTL(일). trash(30일)보다 짧게. */
	@Value("${orphan.quarantine.ttl-days:7}")
	private long ttlDays;

	/**
	 * 오펀 자원을 격리하고 durable 레코드를 남긴다. 등록 후처리 실행기의 QUARANTINE 처분에서 호출.
	 *
	 * @return recoveryId — job fail marker / 복구 모달 / 복구 endpoint 의 키
	 */
	@Transactional
	public String record(OrphanQuarantineRequest req) {
		String recoveryId = UUID.randomUUID().toString();
		Path resolved = Path.of(req.resolvedPath());
		// originalFilename 폴백 SSOT — 이 값이 격리 dest 경로 + row.originalFilename + discard typed-name 가드의 단일 소스.
		String originalName = originalNameOf(req.originalFilename(), resolved);
		String quarantinePath = null;

		// '우리가 업로드한 파일' 이고 실제 존재할 때만 격리. in-place(운영자 자산)는 옮기지 않는다.
		if (req.uploadedFile() && Files.exists(resolved)) {
			try {
				Path dest = Path.of(quarantineRoot, recoveryId, originalName);
				Files.createDirectories(dest.getParent());
				trashService.relocate(resolved, dest); // 검증된 mv(3회 retry) 재사용
				quarantinePath = dest.toString();
				// SIDECAR 자원만 형제 마커가 따로 존재 → 제거. IN_TREE 는 트리 이동이 마커를 동반하므로 불요
				// (if 누적 대신 ResourceType→MarkerLayout 다형성으로 분기 흡수).
				if (req.resourceType().getDefaultLayout() == MarkerLayout.SIDECAR) {
					deleteQuietly(markerService.resolveMarkerFile(resolved, MarkerLayout.SIDECAR));
				}
			} catch (IOException | RuntimeException relocateError) {
				// 격리 이동 실패(디스크 풀 등) — 절대 삭제하지 않고 파일 원위치 유지(degraded). 재시도는 제자리에서.
				log.error("[orphan] 격리 이동 실패 — 파일 원위치 유지. recoveryId={}, path={}",
						recoveryId, req.resolvedPath(), relocateError);
			}
		}

		OrphanQuarantine row = OrphanQuarantine.builder()
				.recoveryId(recoveryId)
				.resourceType(req.resourceType())
				.parentId(req.parentId())
				.resolvedPath(req.resolvedPath())
				.quarantinePath(quarantinePath)
				.originalFilename(originalName)
				.registerExisting(!req.uploadedFile())
				.payload(req.payload())
				.failureClass(req.failureClass())
				.exceptionDetail(truncate(req.detail(), 2000))
				.state(OrphanRecoveryState.PENDING)
				.retryCount(0)
				.jobId(req.jobId())
				.build();
		repository.save(row);
		log.warn("[orphan] 격리 기록. recoveryId={}, type={}, failureClass={}, quarantined={}, path={}",
				recoveryId, req.resourceType(), req.failureClass(), quarantinePath != null, req.resolvedPath());
		return recoveryId;
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
		List<OrphanQuarantine> expired =
				repository.findByStateAndCreatedAtBefore(OrphanRecoveryState.PENDING, threshold);
		for (OrphanQuarantine row : expired) {
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

	@Transactional(readOnly = true)
	public List<OrphanQuarantineResponse> listPending() {
		return repository.findByStateOrderByCreatedAtDesc(OrphanRecoveryState.PENDING).stream()
				.map(OrphanQuarantineResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public OrphanQuarantineResponse get(String recoveryId) {
		return OrphanQuarantineResponse.from(repository.getByRecoveryIdOrThrow(recoveryId));
	}

	/**
	 * R9-4 — 복구 대기(PENDING) 격리 건수. 자원 무결성 점검 페이지가 안내 배너 노출 판단에 사용
	 * (해당 페이지 렌더에만 얹는 count — 전역 navbar 배지는 전 페이지 렌더 비용으로 기각).
	 */
	@Transactional(readOnly = true)
	public long countPending() {
		return repository.countByState(OrphanRecoveryState.PENDING);
	}

	private static String originalNameOf(String provided, Path resolved) {
		if (provided != null && !provided.isBlank()) {
			return provided;
		}
		// 폴백 (파일명·경로 모두 부재한 극단 경로) — R1-4-4 도메인 무관화로 구 ISO 한정 "unknown.iso" → "unknown".
		return resolved.getFileName() != null ? resolved.getFileName().toString() : "unknown";
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
