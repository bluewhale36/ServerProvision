package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.os.dto.request.ISOUpdateRequest;
import com.example.serverprovision.management.os.dto.request.OSMetadataCreateRequest;
import com.example.serverprovision.management.os.dto.request.OSMetadataUpdateRequest;
import com.example.serverprovision.management.os.dto.response.*;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSEnvironment;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.entity.OSPackageGroup;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.*;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSMetadataRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A1 페이지의 OSMetadata 부모 도메인 로직 총괄. 조회 + 등록 충돌 invariant 판단을 책임진다.
 * A1-1 에서 환경·패키지 그룹 응답 조립까지 책임을 확장한다.
 *
 * <p>R1-6 — {@link #create(OSMetadataCreateRequest)} 의 등록 충돌 invariant 판단 (ACTIVE 단독 → Duplicate /
 * DEPRECATED|SOFT_DELETED → Nudge / row 0 → save) 만 잔류하고, nudge payload 조립 + 신규 row 영속화는
 * {@link OSMetadataNudgeService} 로 위임한다 (부모 → nudge 단방향). nudge confirm (proceed / replace) 의
 * 영속화 역시 {@link OSMetadataNudgeService} 가 self-contained 로 처리한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OSMetadataService {

	private final OSMetadataRepository osMetadataRepository;
	private final ISORepository isoRepository;
	private final OSEnvironmentRepository envRepository;
	private final OSPackageGroupRepository grpRepository;
	private final BackgroundJobService backgroundJobService;
	private final PathPolicyService pathPolicyService;
	// R1-6 — nudge 발화 payload 조립 + 신규 row 영속화는 OSMetadataNudgeService 로 위임 (부모 → nudge 단방향).
	private final OSMetadataNudgeService osMetadataNudgeService;
	// R1-4-2 — fileSystemHardener / postCreationTaskStrategies / isoLifecycleService 의존은
	//          IsoRegistrationService 로 이동.
	// R1-4-3 — markerService 의존은 IsoIntegrityService 로 이동. 본 service 는 OSMetadata 부모 도메인 만 담당.
	// R1-6 — nudgeRegistry 의존은 OSMetadataNudgeService 로 이동. 본 service 는 등록 충돌 invariant 판단만 담당.

	// ==== 조회 ========================================================

	public OSMetadataResponse findById(Long id) {
		OSMetadata image = requireActiveImage(id);
		return OSMetadataResponse.ofAssembled(
				image,
				assembleIsoResponses(image, false),
				buildEnvResponses(image),
				buildGroupResponses(image)
		);
	}

	public ISOResponse findISO(Long osMetadataId, Long isoId) {
		return ISOResponse.from(requireLiveISO(osMetadataId, isoId));
	}

	public List<OSGroupResponse> findAllGrouped(boolean includeDeleted) {
		List<OSMetadata> images = includeDeleted
				? osMetadataRepository.findAllByOrderByOsNameAscCreatedAtDesc()
				: osMetadataRepository.findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc();

		Map<OSName, List<OSMetadata>> byName = images.stream().collect(
				Collectors.groupingBy(OSMetadata::getOsName, LinkedHashMap::new, Collectors.toList())
		);

		return byName.entrySet().stream()
				.map(entry -> OSGroupResponse.of(
						entry.getKey(),
						entry.getValue().stream()
								.map(img -> OSMetadataResponse.ofAssembled(
										img,
										assembleIsoResponses(img, includeDeleted),
										buildEnvResponses(img),
										buildGroupResponses(img)
								))
								.toList()
				))
				.toList();
	}

	// ==== OS 메타데이터 쓰기 연산 ==========================================

	/**
	 * R1-1 — 동일 (OSName, osVersion) row 를 단일 쿼리로 전부 조회 후 {@link LifecycleStage} 로 그룹핑.
	 * 옛 흐름의 4 round-trip (exists + activeDeprecated + softDeleted + deprecated 재조회) 을 1 round-trip 으로 환원.
	 *
	 * <p>분기 정의 (안 B — Duplicate ⇔ 순수 ACTIVE 단독) :
	 * <ul>
	 *   <li>ACTIVE 만 존재 (DEPRECATED 0 + SOFT_DELETED 0) → 활성 단순 충돌 fail-fast (Duplicate)</li>
	 *   <li>DEPRECATED 또는 SOFT_DELETED 회수 후보 ≥ 1 → nudge 분기 (사용자에게 회수 시그널 노출)</li>
	 *   <li>row 0 → save</li>
	 * </ul>
	 * 옛 흐름 대비 의도 변경 : ACTIVE + SOFT_DELETED 혼합 케이스가 Duplicate 가 아닌 Nudge 로 환원.
	 * 사용자가 같은 키로 새 등록을 시도할 때 "휴지통에 같은 자원이 있다" 는 회수 시그널을 받도록 한다.
	 */
	@Transactional
	public Long create(OSMetadataCreateRequest request) {
		Map<LifecycleStage, List<OSMetadata>> byStage = osMetadataRepository
				.findAllByOsNameAndOsVersion(request.osName(), request.osVersion())
				.stream()
				.collect(Collectors.groupingBy(
						m -> LifecycleStage.of(m.isDeprecated(), m.isDeleted()),
						() -> new EnumMap<>(LifecycleStage.class),
						Collectors.toList()
				));

		boolean hasActive = byStage.containsKey(LifecycleStage.ACTIVE);
		boolean hasDeprecated = byStage.containsKey(LifecycleStage.DEPRECATED);
		boolean hasSoftDeleted = byStage.containsKey(LifecycleStage.SOFT_DELETED);

		if (hasActive && !hasDeprecated && !hasSoftDeleted) {
			throw new DuplicateOSMetadataException(request.osName(), request.osVersion());
		}
		if (hasDeprecated || hasSoftDeleted) {
			List<OSMetadata> deprecated = byStage.getOrDefault(LifecycleStage.DEPRECATED, List.of());
			List<OSMetadata> softDeleted = byStage.getOrDefault(LifecycleStage.SOFT_DELETED, List.of());
			List<OSMetadata> candidates = new ArrayList<>(deprecated.size() + softDeleted.size());
			candidates.addAll(deprecated);
			candidates.addAll(softDeleted);
			// R1-6 — payload 조립 + 세션 등록은 nudge 서비스로 위임 (부모 → nudge 정방향).
			throw new OSMetadataNudgeRequiredException(
					osMetadataNudgeService.buildNudgePayload(request, candidates));
		}
		// R1-6 — 정상 등록도 nudge 서비스의 persistNew 를 위임 (create happy-path 와 nudge proceed 가 persist 로직 공유).
		return osMetadataNudgeService.persistNew(
				request.osName(), request.osVersion(), request.description());
	}

	/**
	 * R1-2 — osName / osVersion 이 불변이므로 충돌 검사 자체가 발생할 수 없다.
	 * 본 메서드는 단순 description 갱신으로 환원. 옛 흐름의 분기 + exists 호출 + DuplicateOSMetadataException
	 * 발화는 도메인 정책 흡수로 자연 소멸.
	 */
	@Transactional
	public void update(Long id, OSMetadataUpdateRequest request) {
		OSMetadata image = requireActiveImage(id);
		image.update(request.description());
	}

	// R1-6 — buildOSMetadataNudgePayload / persistNewOSMetadata / completePendingOSMetadataFromNudge /
	//        purgeOSMetadataForNudge 는 {@link OSMetadataNudgeService} 로 이동 (nudge 영속화 흡수).
	//        create() 는 invariant 판단만 잔류하고 발화·persist 를 nudge 서비스로 위임.
	// R1-3 — toggleEnabled / softDelete / restore (×2) / deprecate / undeprecate / purgeWithTypedNameCheck / purge
	//        는 {@link OSMetadataLifecycleService} 로 이동.
	// R1-4-1 — purgeIsoWithTypedNameCheck / purgeIso 도 {@link IsoLifecycleService} 로 이동. ISO lifecycle 책임 전부 분리.
	// R1-4-2 — addISO / prepareIsoRegistration (×2) / finalizePreparedIsoRegistration / completePendingIsoFromNudge /
	//          purgeIsoForNudge / registerIsoNudgeSession / triggerPostCreationTask / finalizeMarker /
	//          computeFileSha256 / ensureParentDirectory / storeUploadedFile / startJobStage / deleteQuietly + PreparedIsoRegistration record
	//          는 {@link com.example.serverprovision.management.os.service.iso.IsoRegistrationService} 로 이동. ISO 등록 책임 전부 분리.

	@Transactional
	public void updateISO(Long osMetadataId, Long isoId, ISOUpdateRequest request) {
		// S4.x — isoPath 를 PathPolicyService 로 검증해 ".." 등 traversal 입력이 DB 에 silent 저장되는 사고 차단.
		Path validated = pathPolicyService.assertWritablePath(request.isoPath());
		requireLiveISO(osMetadataId, isoId).update(validated.toString(), request.description());
	}

	// R1-4-1 — toggleIsoEnabled / softDeleteISO / softDeleteISOWithIntent / restoreISO /
	// deprecateIso / undeprecateIso 는 {@link IsoLifecycleService} 로 이동. 본 service 의 책임은
	// OSMetadata 자체와 ISO 의 등록/조회/finalize 흐름에 한정.

	// R1-4-1 — purgeIso 는 {@link IsoLifecycleService#purge} 로 이동.

	// R1-4-3 — verifyIntegrity / verifyAndRecordIntegrity / findIntegrityStatus / computeFileSha256
	//          는 {@link com.example.serverprovision.management.os.service.iso.IsoIntegrityService} 로 이동.
	//          본 service 에서 무결성 책임 전부 분리.

	// ==== 환경·그룹 응답 조립 (A1-1) =====================================

	private List<OSEnvironmentResponse> buildEnvResponses(OSMetadata image) {
		List<OSEnvironment> envs = envRepository
				.findAllByOsMetadata_IdOrderByEnvironmentCode_ValueAsc(image.getId());
		if (envs.isEmpty()) return List.of();

		Map<Long, List<IsoProvisionView>> envProviders = new HashMap<>();
		for (ISO iso : image.getIsos()) {
			if (iso.isDeleted()) continue;
			for (OSEnvironment e : iso.getProvidedEnvironments()) {
				envProviders
						.computeIfAbsent(e.getId(), k -> new ArrayList<>())
						.add(new IsoProvisionView(iso.getId(), image.getOsVersion(), iso.getIsoPath()));
			}
		}

		return envs.stream()
				.filter(e -> envProviders.containsKey(e.getId()))
				.map(e -> new OSEnvironmentResponse(
						e.getId(),
						e.getEnvironmentCode().getValue(),
						e.getDisplayName(),
						e.getDescription(),
						e.isDefault(),
						e.getGroups().stream().map(g -> g.getGroupCode().getValue()).toList(),
						envProviders.get(e.getId())
				))
				.toList();
	}

	private List<OSPackageGroupResponse> buildGroupResponses(OSMetadata image) {
		List<OSPackageGroup> grps = grpRepository
				.findAllByOsMetadata_IdOrderByGroupCode_ValueAsc(image.getId());
		if (grps.isEmpty()) return List.of();

		Map<Long, List<IsoProvisionView>> grpProviders = new HashMap<>();
		for (ISO iso : image.getIsos()) {
			if (iso.isDeleted()) continue;
			for (OSPackageGroup g : iso.getProvidedPackageGroups()) {
				grpProviders
						.computeIfAbsent(g.getId(), k -> new ArrayList<>())
						.add(new IsoProvisionView(iso.getId(), image.getOsVersion(), iso.getIsoPath()));
			}
		}

		return grps.stream()
				.filter(g -> grpProviders.containsKey(g.getId()))
				.map(g -> new OSPackageGroupResponse(
						g.getId(),
						g.getGroupCode().getValue(),
						g.getDisplayName(),
						g.getDescription(),
						g.isDefault(),
						grpProviders.get(g.getId())
				))
				.toList();
	}

	// ==== 내부 헬퍼 ====================================================

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

	private static List<ISO> visibleISOs(List<ISO> all, boolean includeDeleted) {
		return includeDeleted ? all : all.stream().filter(iso -> !iso.isDeleted()).toList();
	}

	/**
	 * S5-12 — ISO entity 목록 + 진행 중 ISO_REGISTRATION background job placeholder 를 합성한 응답.
	 *
	 * <p>사용자가 ISO 등록 폼 제출 후 background job 이 PERSIST_METADATA 완료 전인 짧은 구간 동안 DB 에 ISO 가
	 * 영속되지 않은 상태로 OS 목록 화면이 노출된다. 사용자 혼란 ("등록한 자원이 안 보임") 을 막기 위해
	 * 진행 중 job 의 metadata 에서 osId 매칭, subtitle (= isoPath) 로 placeholder ISOResponse 합성.</p>
	 *
	 * <p>중복 회피 : PERSIST_METADATA 직후의 race 구간에서 DB 영속된 ISO + active job 이 동시에 보일 수 있으므로
	 * 같은 isoPath 의 placeholder 는 추가하지 않는다 (existingPaths 검사).</p>
	 */
	private List<ISOResponse> assembleIsoResponses(OSMetadata image, boolean includeDeleted) {
		var snapshot = backgroundJobService.snapshot();

		// S5-12 hotfix — 동일 isoPath 의 active COMPS_EXTRACTION job 이 있으면 그 ISO 의 추출 버튼 비활성화.
		// RHEL 자동 추출 (S5-7) + 사용자 수동 추출 클릭의 중복 트리거 방지.
		Set<String> extractionInProgressPaths = snapshot.stream()
				.filter(j -> j.getType() == com.example.serverprovision.global.job.enums.JobType.COMPS_EXTRACTION)
				.filter(j -> j.getStatus().isActive())
				.map(com.example.serverprovision.global.job.BackgroundJob::getSubtitle)
				.collect(java.util.stream.Collectors.toSet());

		List<ISOResponse> base = visibleISOs(image.getIsos(), includeDeleted).stream()
				.map(ISOResponse::from)
				.map(r -> extractionInProgressPaths.contains(r.isoPath())
						? r.withExtractionInProgress(true)
						: r)
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));

		Set<String> existingPaths = base.stream()
				.map(ISOResponse::isoPath)
				.collect(java.util.stream.Collectors.toSet());

		String osIdString = String.valueOf(image.getId());
		snapshot.stream()
				.filter(j -> j.getType() == com.example.serverprovision.global.job.enums.JobType.ISO_REGISTRATION)
				.filter(j -> j.getStatus().isActive())
				.filter(j -> osIdString.equals(j.getMetadata().get("osId")))
				.filter(j -> !existingPaths.contains(j.getSubtitle()))
				.forEach(j -> base.add(ISOResponse.inProgress(j.getSubtitle())));

		return base;
	}
}
