package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * HF4-5 — "자원 중복 존재"(RESOURCE_DUPLICATED) 의 택일 해소. 사용자가 모달에서 남길 쪽(survivor)을
 * 선택하면 나머지를 파일시스템에서 삭제한다 (사용자 결정 ②③④).
 *
 * <p>사용자 입력을 동반하는 해결이라 표준 [적용]({@code DriftResolution} bean 디스패치) 계약이 아닌
 * 전용 서비스다 — HASH_MISMATCH 의 {@link HashAcceptService} 선례. 동기 실행이다 (plan D6) —
 * 승격 갈래의 지문 재계산(검수 반려 반영)이 대용량 자원에서 수십 초 걸릴 수 있음을 감수한다
 * (승격은 운영자 확인을 거친 드문 액션 — 빈도가 늘면 BackgroundJob 비동기 전환이 후속 후보).</p>
 *
 * <ul>
 *   <li><b>ORIGINAL(원본 유지)</b> — 복제본 트리/파일+마커 삭제. DB 무변경.</li>
 *   <li><b>DUPLICATE(복제본 유지)</b> — 승격 가드 통과 시 {@code applyDriftedPath} (PATH_DRIFT 해결 로직
 *       재사용)로 DB 경로 갱신 후 원본 삭제. 마커는 위치 독립(attributes 에 절대 경로 없음 — plan D5 실측)
 *       이라 재발급하지 않는다.</li>
 *   <li><b>순서가 안전장치다</b> (HashAcceptService 선례) — DB 변이(경로 갱신·카드 제거)를 먼저 flush 해
 *       동시성 충돌을 파일 조작 전에 표면화하고, 파일 삭제는 마지막. 삭제 실패 시 트랜잭션 롤백으로
 *       카드 유지 + DB 경로 원복 + 오류 응답 (plan D7).</li>
 * </ul>
 */
@Slf4j
@Service
public class DuplicateResolveService {

	private final Map<ResourceType, MarkableScanner> scanners;
	private final ProvisionMarkerService markerService;
	private final DriftRepository driftRepository;
	private final PathReconciliationService reconciliationService;

	public DuplicateResolveService(
			List<MarkableScanner> scanners,
			ProvisionMarkerService markerService,
			DriftRepository driftRepository,
			PathReconciliationService reconciliationService
	) {
		this.scanners = scanners.stream()
				.collect(Collectors.toUnmodifiableMap(MarkableScanner::supportedType, s -> s));
		this.markerService = markerService;
		this.driftRepository = driftRepository;
		this.reconciliationService = reconciliationService;
	}

	@Transactional
	public void resolve(Long driftId, DuplicateSurvivor survivor) {
		// 전면 차단 마스터의 지배 — 정상 흐름은 UI 가 disabled+tooltip 으로 1차 차단하므로 direct POST 안전망.
		if (!reconciliationService.isResolutionEnabled()) {
			throw DriftResolutionNotAllowedException.globalOff();
		}
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		if (drift.getKind() != DriftKind.RESOURCE_DUPLICATED) {
			throw DriftResolutionNotAllowedException.notApplicable(drift.getKind());
		}
		MarkableScanner scanner = scannerFor(drift.getResourceType());
		Markable resource = scanner.findActiveMarkableById(drift.getResourceId())
				.orElseThrow(DriftResolutionNotAllowedException::staleState);

		Path originalPath = Path.of(drift.getOldPath()).toAbsolutePath().normalize();
		Path duplicatePath = Path.of(drift.getNewPath()).toAbsolutePath().normalize();
		MarkerLayout layout = resource.getMarkerLayout();

		// stale 가드 — 다른 중복 카드의 승격 등으로 DB 경로가 이미 바뀌었으면 이 보고로는 처리 불가 (plan D2).
		// 다중 사본에서 하나를 승격한 뒤 남은 카드들이 낡은 원본 경로를 들고 오는 것을 막는다.
		if (!resource.getResourcePath().toAbsolutePath().normalize().equals(originalPath)) {
			throw DriftResolutionNotAllowedException.staleState();
		}
		// 삭제 대상과 생존 대상이 같은 위치면 진행 불가 — 감지 로직상 불가능하지만 direct POST 방어 invariant.
		if (duplicatePath.equals(originalPath)) {
			throw DriftResolutionNotAllowedException.staleState();
		}
		// 실행 직전 재검증 — 그 자리의 마커가 여전히 같은 신분인가 (OrphanMarkerQuarantineResolution 선례).
		MarkerContent duplicateMarker;
		try {
			duplicateMarker = markerService.read(duplicatePath, layout);
		} catch (RuntimeException e) {
			throw DriftResolutionNotAllowedException.staleState();
		}
		if (!drift.getResourceType().name().equals(duplicateMarker.resourceType())
				|| !drift.getResourceId().equals(duplicateMarker.resourceId())) {
			throw DriftResolutionNotAllowedException.staleState();
		}

		if (survivor.promotesDuplicate()) {
			promoteDuplicate(drift, scanner, resource, duplicateMarker, originalPath, duplicatePath, layout);
		} else {
			keepOriginal(drift, resource, originalPath, duplicatePath, layout);
		}
	}

	/**
	 * 원본 유지 — 복제본만 삭제한다. DB 는 손대지 않는다.
	 * 지문 재계산도 하지 않는다 — 어차피 삭제할 사본의 내용 진위는 결과에 영향이 없어 검증 비용이 무의미.
	 */
	private void keepOriginal(
			Drift drift, Markable resource, Path originalPath, Path duplicatePath, MarkerLayout layout
	) {
		Long driftId = drift.getId();
		drift.getReport().removeDrift(drift);
		driftRepository.flush();
		deleteResourceFiles(duplicatePath, layout);
		log.warn("[AUDIT] 자원 중복 해소 — {}#{} ({}) 원본 유지 {} · 복제본 삭제 {} (drift {} 사용자 확인 경유)",
				drift.getResourceType(), drift.getResourceId(), resource.displayName(),
				originalPath, duplicatePath, driftId);
	}

	/**
	 * 복제본 유지(정본 승격) — 가드 통과 시 DB 경로 갱신 후 원본을 삭제한다.
	 */
	private void promoteDuplicate(
			Drift drift, MarkableScanner scanner, Markable resource, MarkerContent duplicateMarker,
			Path originalPath, Path duplicatePath, MarkerLayout layout
	) {
		if (!markerService.verifySignature(duplicateMarker)) {
			throw DriftResolutionNotAllowedException.duplicateNotPromotable(
					"복제본 마커의 서명이 유효하지 않습니다");
		}
		// 낡은 사본 승격 차단 (O-2 위험의 본질) — 원본 재업로드 전에 떠 둔 구버전 사본은 지문이 다르다.
		// 그 내용을 정본으로 삼는 올바른 경로는 재업로드(검증 재통과) 또는 HASH_MISMATCH 수용 흐름이다.
		if (resource.getManifestHash() != null
				&& !resource.getManifestHash().equals(duplicateMarker.manifestHash())) {
			throw DriftResolutionNotAllowedException.duplicateNotPromotable(
					"복제본의 내용 지문이 현재 정본 기록과 다릅니다 — 낡은 사본일 수 있습니다. "
							+ "그 내용을 쓰려면 재업로드가 올바른 경로입니다");
		}
		if (!layout.resourceBodyExists(duplicatePath)) {
			throw DriftResolutionNotAllowedException.duplicateNotPromotable(
					"복제본 위치에 본체 파일이 없습니다");
		}
		// 포함 관계 방어 — 사본이 원본 트리 내부(또는 그 반대)면 원본 삭제가 승격된 사본까지 지운다.
		if (duplicatePath.startsWith(originalPath) || originalPath.startsWith(duplicatePath)) {
			throw DriftResolutionNotAllowedException.duplicateNotPromotable(
					"원본과 복제본 경로가 포함 관계라 안전하게 정리할 수 없습니다");
		}
		// HF4-5 검수 반려 반영 — 위의 마커 기록값 대조만으로는 "마커는 최신 정상본 그대로 두고 페이로드
		// 바이트만 변조한 사본"이 통과한다 (샌드박스 실측 재현). 승격은 원본 삭제 + DB 갱신이 걸린 비가역
		// 액션이라, 사본 실제 바이트의 지문을 재계산해 정본 기록과 최종 대조한다. 계산은 정밀 점검(deep
		// scan)이 쓰는 scanner.recomputeManifestHash 경로를 그대로 재사용한다 (해시 규칙 중복 구현 금지 —
		// 사본 경로는 읽기 전용 위임 뷰 DuplicatePathView 로 주입). 동기 수행이다 : 승격은 운영자 확인을
		// 거친 드문 액션이고, 대용량 ISO 재계산은 수십 초가 걸릴 수 있음을 감수한다 (빈도가 늘면
		// HASH_ACCEPT 선례의 BackgroundJob 비동기 전환이 후속 후보). 비용이 가장 큰 가드라 저비용 가드
		// (서명·기록 지문·본체·포함 관계)를 모두 통과한 뒤 마지막에 둔다.
		Optional<String> recomputed = scanner.recomputeManifestHash(
				new DuplicatePathView(resource, duplicatePath));
		if (recomputed.isEmpty()) {
			throw DriftResolutionNotAllowedException.duplicateNotPromotable(
					"복제본 내용의 지문을 계산할 수 없습니다 — 파일 상태를 확인하세요");
		}
		if (resource.getManifestHash() != null && !recomputed.get().equals(resource.getManifestHash())) {
			throw DriftResolutionNotAllowedException.duplicateNotPromotable(
					"복제본 실제 내용의 지문이 현재 정본 기록과 다릅니다 — 마커만 정상이고 내용이 변조된 "
							+ "사본일 수 있습니다. 그 내용을 쓰려면 재업로드가 올바른 경로입니다");
		}
		Long driftId = drift.getId();
		scanner.applyDriftedPath(drift.getResourceId(), duplicatePath); // PATH_DRIFT 해결 로직 재사용
		drift.getReport().removeDrift(drift);
		driftRepository.flush();
		deleteResourceFiles(originalPath, layout);
		log.warn("[AUDIT] 자원 중복 해소 — {}#{} ({}) 복제본 승격 {} · DB 경로 갱신 · 원본 삭제 {} (drift {} 사용자 확인 경유)",
				drift.getResourceType(), drift.getResourceId(), resource.displayName(),
				duplicatePath, originalPath, driftId);
	}

	/**
	 * layout 별 삭제 — IN_TREE 는 트리 통째(마커 내장), SIDECAR 는 본체 → 마커 순 2파일.
	 * 본체를 먼저 지우는 이유 : 마커 삭제가 실패해 남더라도 신원이 남은 잔재는 다음 점검이
	 * 표면화할 수 있다 (마커 없는 본체 잔재는 어떤 점검도 볼 수 없는 침묵 잔재가 된다).
	 * 실패는 IllegalStateException 전파 → 트랜잭션 롤백 (카드 유지 — plan D7).
	 */
	private void deleteResourceFiles(Path resourcePath, MarkerLayout layout) {
		try {
			if (layout == MarkerLayout.IN_TREE) {
				FileSystemUtils.deleteRecursively(resourcePath);
			} else {
				Files.deleteIfExists(resourcePath);
				Path markerFile = markerService.resolveMarkerFile(resourcePath, layout);
				if (markerFile != null) {
					Files.deleteIfExists(markerFile);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("파일 삭제 실패 : " + resourcePath + " — " + e.getMessage(), e);
		}
	}

	private MarkableScanner scannerFor(ResourceType type) {
		MarkableScanner scanner = scanners.get(type);
		if (scanner == null) {
			throw new IllegalStateException("지원하지 않는 자원 종류 : " + type);
		}
		return scanner;
	}

	/**
	 * HF4-5 검수 반려 반영 — 사본 경로에 대한 지문 재계산용 읽기 전용 {@link Markable} 위임 뷰.
	 * 모든 scanner 의 {@code recomputeManifestHash} 는 {@code getResourcePath()} 기준으로 계산하므로
	 * (실측 : ISO 는 파일 SHA-256, 번들류는 BundleManifestService 트리 해시), 경로 하나만 사본으로
	 * 바꿔 끼우면 정밀 점검과 동일한 해시 경로를 재사용할 수 있다 — SPI 시그니처 확장(6 구현 동반 수정)
	 * 없이 중복 구현 0. 상태 변이 메서드는 전부 즉시 실패 — 재계산 경로가 실수로 엔티티를 변이시키는
	 * 사고를 시끄럽게 차단한다.
	 */
	private record DuplicatePathView(Markable delegate, Path duplicatePath) implements Markable {

		@Override
		public Long getResourceId() {
			return delegate.getResourceId();
		}

		@Override
		public ResourceType getResourceType() {
			return delegate.getResourceType();
		}

		@Override
		public Path getResourcePath() {
			return duplicatePath;
		}

		@Override
		public MarkerLayout getMarkerLayout() {
			return delegate.getMarkerLayout();
		}

		@Override
		public String getManifestHash() {
			return delegate.getManifestHash();
		}

		@Override
		public String getMarkerSignature() {
			return delegate.getMarkerSignature();
		}

		@Override
		public String displayName() {
			return delegate.displayName();
		}

		@Override
		public boolean isDeprecated() {
			return delegate.isDeprecated();
		}

		@Override
		public boolean isDeleted() {
			return delegate.isDeleted();
		}

		@Override
		public void reissueMarker(String manifestHash, String markerSignature) {
			throw new IllegalStateException("읽기 전용 뷰 — 지문 재계산 경로에서 엔티티 변이는 허용되지 않는다");
		}

		@Override
		public void deprecate() {
			throw new IllegalStateException("읽기 전용 뷰 — 지문 재계산 경로에서 엔티티 변이는 허용되지 않는다");
		}

		@Override
		public void undeprecate() {
			throw new IllegalStateException("읽기 전용 뷰 — 지문 재계산 경로에서 엔티티 변이는 허용되지 않는다");
		}

		@Override
		public void softDelete() {
			throw new IllegalStateException("읽기 전용 뷰 — 지문 재계산 경로에서 엔티티 변이는 허용되지 않는다");
		}

		@Override
		public void restore() {
			throw new IllegalStateException("읽기 전용 뷰 — 지문 재계산 경로에서 엔티티 변이는 허용되지 않는다");
		}
	}
}
