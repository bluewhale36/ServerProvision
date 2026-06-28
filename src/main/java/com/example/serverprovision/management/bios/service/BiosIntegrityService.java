package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;

/**
 * R4-3 — BIOS 번들 무결성 검증 / 상태 조회 전담 service. R4-3 이전 {@code BiosService} 에 잔류하던
 * 무결성 책임(IN_TREE 마커 read + 서명/manifestHash 재계산 비교 + DB last snapshot 조회)을 본 service 로
 * 응집해 잔류 {@code BiosService} 가 read+update 코어에만 집중하게 한다.
 *
 * <p>책임 3 진입점({@code IsoIntegrityService} 선례 정합) :</p>
 * <ul>
 *   <li>{@link #findIntegrityStatus} — DB 의 마지막 검증 결과 + 시각 단순 조회(영속화 X).</li>
 *   <li>{@link #verifyIntegrity} — 동기 검증. 디스크 + 마커 + DB 의 3-way 비교 결과를 {@link IntegrityStatus}
 *       enum 으로 환원(영속화 X, 호출자가 결과 사용).</li>
 *   <li>{@link #verifyAndRecordIntegrity} — {@link #verifyIntegrity} 호출 후 BIOS entity 의 last snapshot 영속화.
 *       {@link BiosVerificationLauncher} 의 async 진입점이 호출.</li>
 * </ul>
 *
 * <p>메서드명은 호출처 churn 최소화를 위해 분리 전 이름을 유지한다. {@link com.example.serverprovision.global.lifecycle.LifecycleService}
 * 와 무관하므로 2-arg {@code (boardId, biosId)} 시그니처 그대로 보존.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BiosIntegrityService {

	private final BiosRepository biosRepository;
	private final BoardModelRepository boardModelRepository;
	private final ProvisionMarkerService provisionMarkerService;
	private final BundleManifestService bundleManifestService;

	// ==== public 진입점 ================================================

	public IntegrityStatusResponse findIntegrityStatus(Long boardId, Long biosId) {
		BoardBIOS bios = BiosGuards.requireLiveBios(biosRepository, boardModelRepository, boardId, biosId);
		return IntegrityStatusResponse.of(
				bios.getId(),
				bios.getLastIntegrityStatus() != null ? bios.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				bios.getLastVerifiedAt()
		);
	}

	public IntegrityStatus verifyIntegrity(Long boardId, Long biosId) {
		BoardBIOS bios = BiosGuards.requireLiveBios(biosRepository, boardModelRepository, boardId, biosId);
		Path treeRoot = Path.of(bios.getTreeRootPath());
		MarkerContent marker;
		try {
			marker = provisionMarkerService.read(treeRoot, MarkerLayout.IN_TREE);
		} catch (MarkerMissingException e) {
			return IntegrityStatus.MARKER_MISSING;
		}
		if (!provisionMarkerService.verifySignature(marker)) {
			return IntegrityStatus.SIGNATURE_INVALID;
		}
		ManifestSummary recomputed = bundleManifestService.compute(treeRoot);
		if (!provisionMarkerService.verifyManifestHash(marker, recomputed.manifestHash())) {
			return IntegrityStatus.TAMPERED;
		}
		return IntegrityStatus.ORIGINAL;
	}

	@Transactional
	public IntegrityStatus verifyAndRecordIntegrity(Long boardId, Long biosId) {
		IntegrityStatus status = verifyIntegrity(boardId, biosId);
		BiosGuards.requireLiveBios(biosRepository, boardModelRepository, boardId, biosId)
				.recordIntegritySnapshot(status, Instant.now());
		return status;
	}
}
