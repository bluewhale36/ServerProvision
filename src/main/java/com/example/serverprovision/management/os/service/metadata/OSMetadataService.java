package com.example.serverprovision.management.os.service.metadata;

import com.example.serverprovision.global.lifecycle.dto.ResourceListQuery;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A1 페이지의 OSMetadata 부모 도메인 로직 총괄. 조회 + 등록 충돌 invariant 판단을 책임진다.
 * A1-1 에서 환경·패키지 그룹 응답 조립까지 책임을 확장한다.
 *
 * <p>HF30-1 — {@link #create(OSMetadataCreateRequest)} 는 같은 (OSName, osVersion) 이 어느 상태로든 있으면 거절하고 상태별
 * 다음 행동을 안내한다(메인보드 HF26 과 같은 규칙). 종전 중복 확인 창(OSMetadataNudgeService)은 창 조건과 [그래도 등록]
 * 조건이 어긋나 Deprecated 가 끼면 항상 실패했다 — OS 버전은 키가 곧 정체라 '같은 것을 하나 더' 가 성립하지 않아 창을 없앴다.</p>
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
	// R1-4-2 — fileSystemHardener / postCreationTaskStrategies / isoLifecycleService 의존은
	//          IsoRegistrationService 로 이동.
	// R1-4-3 — markerService 의존은 IsoIntegrityService 로 이동. 본 service 는 OSMetadata 부모 도메인 만 담당.

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

	/**
	 * 목록 묶음(S8-3 — 조회 조건 반영). 조건은 OS 버전(C2 항목)에 걸고 묶기 전에 거른다 — 맞는 버전이 없는 OS 이름은
	 * 묶음 자체가 생기지 않는다. 검색 대상 = OS 이름 표시명 · 버전 · 설명(ISO 는 한 단계 안쪽이라 대상이 아니다).
	 */
	public List<OSGroupResponse> findAllGrouped(ResourceListQuery query) {
		boolean includeDeleted = query.includeDeleted();
		List<OSMetadata> images = (includeDeleted
				? osMetadataRepository.findAllByOrderByOsNameAscCreatedAtDesc()
				: osMetadataRepository.findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc()).stream()
				.filter(img -> query.matches(img, img.getOsName().getDisplayName(), img.getOsVersion(), img.getDescription()))
				.toList();

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
	 * 등록 — 같은 (OSName, osVersion) 이 활성 · Deprecated · 휴지통 어느 상태로든 있으면 거절하고 상태별로 다음 행동을 안내한다(HF30-1).
	 * DB UNIQUE({@code uk_os_metadata_name_version})가 같은 규칙의 안전망이다. UNIQUE 이전의 옛 데이터에 같은 키가 여럿 남아 있으면
	 * 가장 살아 있는 상태(활성 → Deprecated → 휴지통)를 기준으로 안내한다.
	 */
	@Transactional
	public Long create(OSMetadataCreateRequest request) {
		osMetadataRepository.findAllByOsNameAndOsVersion(request.osName(), request.osVersion()).stream()
				.min(Comparator.comparing(m -> LifecycleStage.of(m.isDeprecated(), m.isDeleted())))
				.ifPresent(existing -> {
					throw DuplicateOSMetadataException.againstRegistration(existing);
				});
		OSMetadata toSave = OSMetadata.builder()
				.osName(request.osName())
				.osVersion(request.osVersion())
				.description(request.description())
				.build();
		try {
			// flush 로 UNIQUE 위반을 이 자리에서 드러낸다 — 커밋 시점까지 미루면 번역할 곳 없이 500 으로 샌다.
			return osMetadataRepository.saveAndFlush(toSave).getId();
		} catch (DataIntegrityViolationException e) {
			// 이중 제출 · 두 탭의 동시 등록 — 위 검사를 함께 통과한 뒤 DB UNIQUE 가 뒤늦은 쪽을 거절했다.
			// 경합 상대는 방금 생긴 활성 행이라 활성 문구가 맞다.
			log.info("[osMetadata] 동시 등록 경합 — DB 유일성 인덱스가 거절. osName={}, osVersion={}",
					request.osName(), request.osVersion());
			throw new DuplicateOSMetadataException(request.osName(), request.osVersion());
		}
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
