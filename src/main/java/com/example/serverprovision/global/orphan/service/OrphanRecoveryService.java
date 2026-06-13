package com.example.serverprovision.global.orphan.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.orphan.OrphanRecoveryContext;
import com.example.serverprovision.global.orphan.OrphanRecoverySpi;
import com.example.serverprovision.global.orphan.dto.OrphanRetryResponse;
import com.example.serverprovision.global.orphan.entity.OrphanQuarantine;
import com.example.serverprovision.global.orphan.exception.OrphanRecoveryAlreadyResolvedException;
import com.example.serverprovision.global.orphan.repository.OrphanQuarantineRepository;
import com.example.serverprovision.global.trash.TrashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 오펀 자원 격리 saga 의 <b>action-side</b> (도메인 무관). 사용자 결정에 따라 격리 자원을 재시도(복원+재등록)하거나
 * 폐기한다.
 *
 * <p>R1-4-4 핵심 (@Lazy 제거) : retry 는 격리 파일을 active 경로로 복원한 뒤 {@code Map<ResourceType, OrphanRecoverySpi>}
 * 에서 자원 종류에 맞는 SPI 를 찾아 재등록을 위임한다. launcher 의존은 SPI 구현체로 이동했으므로 본 서비스는
 * 도메인 launcher 를 직접 알지 않는다 — {@code Launcher→Runner→QuarantineService} 와 본 서비스가 분리되어 DAG.</p>
 */
@Slf4j
@Service
public class OrphanRecoveryService {

	private final OrphanQuarantineRepository repository;
	private final TrashService trashService;
	private final Map<ResourceType, OrphanRecoverySpi> spiByType;

	public OrphanRecoveryService(OrphanQuarantineRepository repository, TrashService trashService,
	                             List<OrphanRecoverySpi> spis) {
		this.repository = repository;
		this.trashService = trashService;
		this.spiByType = spis.stream()
				.collect(Collectors.toUnmodifiableMap(OrphanRecoverySpi::supportedType, s -> s));
	}

	/** 재시도 — 격리 파일을 active 경로로 복원하고 도메인 SPI 로 재등록을 위임한다. 행은 RECOVERED 로 소비. */
	@Transactional
	public OrphanRetryResponse retry(String recoveryId) {
		OrphanQuarantine row = repository.getByRecoveryIdOrThrow(recoveryId);
		ensurePending(row);

		// 멱등 복원 : 격리 파일이 아직 active 경로에 없을 때만 이동. 이전 retry 가 relocate 후 launcher 에서 실패해
		// tx 롤백된 경우 파일은 이미 active 에 있으므로(FS 는 롤백 안 됨) 재이동을 금지 — 복구 경로 내 FS-DB 비원자성 방어.
		if (row.getQuarantinePath() != null && !Files.exists(Path.of(row.getResolvedPath()))) {
			trashService.relocate(Path.of(row.getQuarantinePath()), Path.of(row.getResolvedPath()));
		}
		row.markRecovered();

		OrphanRecoveryContext ctx = new OrphanRecoveryContext(
				row.getParentId(), row.getResolvedPath(), row.getOriginalFilename(),
				!row.isRegisterExisting(), row.getPayload());
		OrphanRetryResponse resp = spiFor(row.getResourceType()).relaunch(ctx);
		log.info("[orphan] 재시도 — 새 등록 job 시작. recoveryId={}, type={}, newJobId={}",
				recoveryId, row.getResourceType(), resp.jobId());
		return resp;
	}

	/** 폐기 — 격리 파일 삭제 + 행 DISCARDED. typedName(파일명) 일치 확인(파괴적 작업 가드). */
	@Transactional
	public void discard(String recoveryId, String typedName) {
		OrphanQuarantine row = repository.getByRecoveryIdOrThrow(recoveryId);
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

	private OrphanRecoverySpi spiFor(ResourceType type) {
		OrphanRecoverySpi spi = spiByType.get(type);
		if (spi == null) {
			throw new IllegalStateException("OrphanRecoverySpi 미등록 자원 종류: " + type);
		}
		return spi;
	}

	private void ensurePending(OrphanQuarantine row) {
		if (!row.isPending()) {
			throw new OrphanRecoveryAlreadyResolvedException(row.getRecoveryId(), row.getState().name());
		}
	}

	private void deleteQuietly(Path p) {
		try {
			Files.deleteIfExists(p);
		} catch (IOException ignore) {
			// 폐기 정리 실패는 핵심 흐름을 막지 않는다.
		}
	}
}
