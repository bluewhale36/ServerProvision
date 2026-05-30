package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.exception.ISOFileStorageException;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.IllegalOSMetadataStateException;
import com.example.serverprovision.management.os.exception.OSMetadataNotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * R1-4-3 — ISO 무결성 검증 / 상태 조회 전담 service. R1-4-3 이전 OSMetadataService 에 잔류하던
 * ISO 무결성 책임 (sidecar 마커 read + 서명/manifestHash 비교 + DB last snapshot 조회) 을 본 service
 * 로 응집해 OSMetadataService 가 부모 도메인에만 집중하게 한다.
 *
 * <p>책임 3 진입점 :</p>
 * <ul>
 *   <li>{@link #findStatus} — DB 의 마지막 검증 결과 + 시각 단순 조회 (영속화 X).</li>
 *   <li>{@link #verify} — 동기 검증. 디스크 + 마커 + DB 의 3-way 비교 결과를 {@link IntegrityStatus}
 *       enum 으로 환원. 영속화 X (호출자가 결과 사용).</li>
 *   <li>{@link #verifyAndRecord} — {@link #verify} 호출 후 ISO entity 의 last snapshot 영속화.
 *       {@link IsoVerificationLauncher} 의 async 진입점이 호출.</li>
 * </ul>
 *
 * <p>의존 그래프 — 단방향 :</p>
 * <ul>
 *   <li>본 service → {@link OSMetadataRepository} (부모 lookup), {@link ISORepository},
 *       {@link ProvisionMarkerService}.</li>
 *   <li>본 service ⇸ OSMetadataService (의존 없음). 부모 lookup 은 Repository 를 통해 직접.</li>
 * </ul>
 *
 * <p>R1-4-2 의 helper 사본 정책을 그대로 따라 {@code requireActiveImage} / {@code requireLiveISO}
 * 는 본 service 안에 사본 보유한다. over-abstraction 회피 — R1-4 묶음 책임 분리 후 cross-service
 * 추상이 도메인 의미를 약화시키는 비용 회피.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IsoIntegrityService {

	private final OSMetadataRepository osMetadataRepository;
	private final ISORepository isoRepository;
	private final ProvisionMarkerService markerService;

	// ==== public 진입점 ================================================

	public IntegrityStatusResponse findStatus(Long osMetadataId, Long isoId) {
		ISO iso = requireLiveISO(osMetadataId, isoId);
		return IntegrityStatusResponse.of(
				iso.getId(),
				iso.getLastIntegrityStatus() != null ? iso.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
				iso.getLastVerifiedAt()
		);
	}

	public IntegrityStatus verify(Long osMetadataId, Long isoId) {
		requireActiveImage(osMetadataId);
		ISO iso = isoRepository.findByIdAndOsMetadata_Id(isoId, osMetadataId)
				.orElseThrow(() -> new ISONotFoundException(osMetadataId, isoId));
		if (iso.isDeleted()) {
			throw new IllegalOSMetadataStateException("삭제된 ISO 는 검증할 수 없습니다. isoId=" + isoId);
		}
		Path target = Path.of(iso.getIsoPath());
		MarkerContent marker;
		try {
			marker = markerService.read(target, MarkerLayout.SIDECAR);
		} catch (MarkerMissingException e) {
			return IntegrityStatus.MARKER_MISSING;
		}
		if (!markerService.verifySignature(marker)) {
			return IntegrityStatus.SIGNATURE_INVALID;
		}
		if (!Files.isRegularFile(target)) {
			return IntegrityStatus.TAMPERED;
		}
		String recomputed = computeFileSha256(target);
		if (!markerService.verifyManifestHash(marker, recomputed)) {
			return IntegrityStatus.TAMPERED;
		}
		return IntegrityStatus.ORIGINAL;
	}

	@Transactional
	public IntegrityStatus verifyAndRecord(Long osMetadataId, Long isoId) {
		IntegrityStatus status = verify(osMetadataId, isoId);
		requireLiveISO(osMetadataId, isoId).recordIntegritySnapshot(status, Instant.now());
		return status;
	}

	// ==== 내부 헬퍼 ====================================================

	/**
	 * 디스크에 이미 있는 파일의 SHA-256. {@link #verify} 의 manifestHash 재계산 단계 전용 helper.
	 *
	 * <p>R1-4-2 등록 흐름의 동명 helper ({@link IsoRegistrationService}) 와 의미가 다르다 —
	 * 등록 시점은 client/server hash 비교, 검증 시점은 marker manifestHash 와 재계산값 비교. 의미가
	 * 분리되어 있으므로 over-abstraction 회피를 위해 사본 보유 (R1-4-3 plan §8 D-2).</p>
	 */
	private static String computeFileSha256(Path file) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(file);
			     DigestInputStream dis = new DigestInputStream(in, md)) {
				byte[] buf = new byte[8192];
				while (dis.read(buf) >= 0) { /* drain */ }
			}
			return HexFormat.of().formatHex(md.digest());
		} catch (IOException | NoSuchAlgorithmException e) {
			throw new ISOFileStorageException("ISO 파일 hash 계산 실패. path=" + file, e);
		}
	}

	private OSMetadata requireActiveImage(Long id) {
		return osMetadataRepository.findByIdAndIsDeletedFalse(id)
				.orElseThrow(() -> new OSMetadataNotFoundException(id));
	}

	private ISO requireLiveISO(Long osMetadataId, Long isoId) {
		requireActiveImage(osMetadataId);
		ISO iso = isoRepository.findByIdAndOsMetadata_Id(isoId, osMetadataId)
				.orElseThrow(() -> new ISONotFoundException(osMetadataId, isoId));
		if (iso.isDeleted()) {
			throw new IllegalOSMetadataStateException("삭제된 ISO 에는 수행할 수 없는 작업입니다. isoId=" + isoId);
		}
		return iso;
	}
}
