package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;

/**
 * R6-3 — Subprogram 번들 무결성 검증 / 상태 조회 전담 service. R6-3 이전 {@code SubprogramService} 에 잔류하던
 * 무결성 책임(IN_TREE 마커 read + 서명/manifestHash 재계산 비교 + DB last snapshot 조회)을 본 service 로
 * 응집해 잔류 {@code SubprogramService} 가 read+update 코어에만 집중하게 한다
 * ({@code BmcIntegrityService} 선례 정합).
 *
 * <p>책임 3 진입점 :</p>
 * <ul>
 *   <li>{@link #findIntegrityStatus} — DB 의 마지막 검증 결과 + 시각 단순 조회(영속화 X).</li>
 *   <li>{@link #verifyIntegrity} — 동기 검증. 디스크 + 마커 + DB 의 3-way 비교 결과를 {@link IntegrityStatus}
 *       enum 으로 환원(영속화 X, 호출자가 결과 사용).</li>
 *   <li>{@link #verifyAndRecordIntegrity} — {@link #verifyIntegrity} 호출 후 Subprogram entity 의 last snapshot
 *       영속화. {@link SubprogramVerificationLauncher} 의 async 진입점이 호출.</li>
 * </ul>
 *
 * <p>메서드명·시그니처는 호출처 churn 최소화를 위해 분리 전 그대로 유지한다. Subprogram 은 FK 가 nullable
 * (공용 자원)이라 무결성 검증이 board scope 와 무관하며, 분리 전부터 1-arg {@code (subprogramId)} 다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubprogramIntegrityService {

	private final SubprogramRepository subprogramRepository;
	private final ProvisionMarkerService provisionMarkerService;
	private final BundleManifestService bundleManifestService;

	// ==== public 진입점 ================================================

	public IntegrityStatusResponse findIntegrityStatus(Long subprogramId) {
		Subprogram sp = SubprogramGuards.requireLive(subprogramRepository, subprogramId);
		return IntegrityStatusResponse.of(
				sp.getId(),
				sp.getLastIntegrityStatus() != null ? sp.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				sp.getLastVerifiedAt()
		);
	}

	public IntegrityStatus verifyIntegrity(Long subprogramId) {
		Subprogram sp = SubprogramGuards.requireLive(subprogramRepository, subprogramId);
		Path treeRoot = Path.of(sp.getTreeRootPath());
		MarkerContent marker;
		try {
			marker = provisionMarkerService.read(treeRoot, MarkerLayout.IN_TREE);
		} catch (MarkerMissingException e) {
			return IntegrityStatus.MARKER_MISSING;
		}
		if (!provisionMarkerService.verifySignature(marker)) {
			return IntegrityStatus.SIGNATURE_INVALID;
		}
		String recomputed = bundleManifestService.compute(treeRoot).manifestHash();
		if (!provisionMarkerService.verifyManifestHash(marker, recomputed)) {
			return IntegrityStatus.TAMPERED;
		}
		return IntegrityStatus.ORIGINAL;
	}

	@Transactional
	public IntegrityStatus verifyAndRecordIntegrity(Long subprogramId) {
		IntegrityStatus status = verifyIntegrity(subprogramId);
		SubprogramGuards.requireLive(subprogramRepository, subprogramId)
				.recordIntegritySnapshot(status, Instant.now());
		return status;
	}
}
