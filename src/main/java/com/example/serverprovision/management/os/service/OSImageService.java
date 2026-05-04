package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.os.dto.request.ISOCreateRequest;
import com.example.serverprovision.management.os.dto.request.ISOUpdateRequest;
import com.example.serverprovision.management.os.dto.request.OSImageCreateRequest;
import com.example.serverprovision.management.os.dto.request.OSImageUpdateRequest;
import com.example.serverprovision.management.os.dto.response.ISOResponse;
import com.example.serverprovision.management.os.dto.response.IsoProvisionView;
import com.example.serverprovision.management.os.dto.response.OSEnvironmentResponse;
import com.example.serverprovision.management.os.dto.response.OSGroupResponse;
import com.example.serverprovision.management.os.dto.response.OSImageResponse;
import com.example.serverprovision.management.os.dto.response.OSPackageGroupResponse;
import com.example.serverprovision.management.common.dto.response.IntegrityStatusResponse;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
import com.example.serverprovision.management.common.nudge.dto.NudgeConflictEntry;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSEnvironment;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.entity.OSPackageGroup;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.exception.DirectoryMissingException;
import com.example.serverprovision.management.os.exception.DuplicateISOContentException;
import com.example.serverprovision.management.os.exception.DuplicateOSImageException;
import com.example.serverprovision.management.os.exception.ISOFileStorageException;
import com.example.serverprovision.management.os.exception.InvalidIsoPathException;
import com.example.serverprovision.management.os.exception.DuplicateFilenameException;
import com.example.serverprovision.management.os.exception.ISONotFoundException;
import com.example.serverprovision.management.os.exception.IllegalOSImageStateException;
import com.example.serverprovision.management.os.exception.IsoNudgeRequiredException;
import com.example.serverprovision.management.os.exception.IsoUploadIntentConflictException;
import com.example.serverprovision.management.os.exception.OSImageNotFoundException;
import com.example.serverprovision.management.os.exception.OSImageNudgeRequiredException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSImageRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.management.os.util.IsoPathResolver;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.exception.SidecarConflictException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.PathPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A1 페이지의 도메인 로직 총괄. A1-1 에서 환경·패키지 그룹 응답 조립까지 책임을 확장한다.
 *
 * <p>MK2 — lifecycle 상태 전이 (deprecate / undeprecate / restore / purge) 를 OS / ISO 양쪽에 추가.
 * 업로드 단계 B 에서 soft-deleted/deprecated 자원과 manifestHash 가 충돌하면 자동 purge 대신
 * {@link NudgeRegistry} 세션을 등록해 사용자 confirm 을 요구하는 nudge 흐름으로 분기한다.
 * 활성 자원과의 해시 충돌은 그대로 fail-fast ({@link DuplicateISOContentException}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OSImageService {

    private final OSImageRepository osImageRepository;
    private final ISORepository isoRepository;
    private final OSEnvironmentRepository envRepository;
    private final OSPackageGroupRepository grpRepository;
    private final ProvisionMarkerService markerService;
    private final BackgroundJobService backgroundJobService;
    private final PathPolicyService pathPolicyService;
    private final FileSystemHardener fileSystemHardener;
    private final NudgeRegistry nudgeRegistry;

    // ==== 조회 ========================================================

    public OSImageResponse findById(Long id) {
        OSImage image = requireActiveImage(id);
        return OSImageResponse.of(
                image,
                visibleISOs(image.getIsos(), false),
                buildEnvResponses(image),
                buildGroupResponses(image)
        );
    }

    public ISOResponse findISO(Long osImageId, Long isoId) {
        return ISOResponse.from(requireLiveISO(osImageId, isoId));
    }

    public IntegrityStatusResponse findIntegrityStatus(Long osImageId, Long isoId) {
        ISO iso = requireLiveISO(osImageId, isoId);
        return IntegrityStatusResponse.of(
                iso.getId(),
                iso.getLastIntegrityStatus() != null ? iso.getLastIntegrityStatus() : IntegrityStatus.NOT_VERIFIED,
                iso.getLastVerifiedAt()
        );
    }

    public List<OSGroupResponse> findAllGrouped(boolean includeDeleted) {
        List<OSImage> images = includeDeleted
                ? osImageRepository.findAllByOrderByOsNameAscCreatedAtDesc()
                : osImageRepository.findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc();

        Map<OSName, List<OSImage>> byName = images.stream().collect(
                Collectors.groupingBy(OSImage::getOsName, LinkedHashMap::new, Collectors.toList())
        );

        return byName.entrySet().stream()
                .map(entry -> OSGroupResponse.of(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(img -> OSImageResponse.of(
                                        img,
                                        visibleISOs(img.getIsos(), includeDeleted),
                                        buildEnvResponses(img),
                                        buildGroupResponses(img)
                                ))
                                .toList()
                ))
                .toList();
    }

    // ==== OS 이미지 쓰기 연산 ==========================================

    @Transactional
    public Long create(OSImageCreateRequest request) {
        // 1) 활성 + 동일 (OSName, osVersion) → 즉시 fail-fast (409 + DuplicateOSImageException).
        if (osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(request.osName(), request.osVersion())) {
            // 활성 단순 충돌 vs Deprecated 충돌 — 활성 deprecated 도 nudge 후보로 회수해야 한다.
            List<OSImage> activeDeprecated =
                    osImageRepository.findAllByOsNameAndOsVersionAndIsDeprecatedTrueAndIsDeletedFalse(
                            request.osName(), request.osVersion());
            if (activeDeprecated.isEmpty()) {
                // 진짜 활성 자원과의 충돌 — fail-fast.
                throw new DuplicateOSImageException(request.osName(), request.osVersion());
            }
            // Deprecated 활성 자원은 nudge 후보 — 아래 로직과 합류.
        }

        // 2) MK2 WAVE 1 — soft-deleted / deprecated 후보 탐지 → nudge 세션 발급 + 409.
        List<OSImage> candidates = collectMetaNudgeCandidates(request.osName(), request.osVersion());
        if (!candidates.isEmpty()) {
            throw new OSImageNudgeRequiredException(buildOSImageNudgePayload(request, candidates));
        }

        // 3) 충돌 후보 0 → 그대로 영속화.
        return persistNewOSImage(request.osName(), request.osVersion(), request.description());
    }

    /**
     * MK2 WAVE 1 — 메타 nudge 후보 (soft-deleted ∪ active+deprecated).
     */
    private List<OSImage> collectMetaNudgeCandidates(OSName osName, String osVersion) {
        List<OSImage> softDeleted = osImageRepository.findAllByOsNameAndOsVersionAndIsDeletedTrue(osName, osVersion);
        List<OSImage> deprecated = osImageRepository.findAllByOsNameAndOsVersionAndIsDeprecatedTrueAndIsDeletedFalse(osName, osVersion);
        List<OSImage> merged = new ArrayList<>(softDeleted.size() + deprecated.size());
        merged.addAll(softDeleted);
        merged.addAll(deprecated);
        return merged;
    }

    private NudgeRequiredResponse buildOSImageNudgePayload(OSImageCreateRequest request, List<OSImage> candidates) {
        NudgeSession session = nudgeRegistry.register(
                NudgeResourceType.OS_IMAGE,
                null,
                candidates.stream().map(OSImage::getId).toList(),
                new com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload(
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
                        Instant.now()))
                .toList();
        log.info("[osImage] nudge required : osName={}, osVersion={}, candidates={}",
                request.osName(), request.osVersion(), candidates.size());
        return NudgeRequiredResponse.of(session.nudgeId(), entries, session.expiresAt());
    }

    private Long persistNewOSImage(OSName osName, String osVersion, String description) {
        OSImage saved = osImageRepository.save(OSImage.builder()
                .osName(osName)
                .osVersion(osVersion)
                .description(description)
                .build());
        return saved.getId();
    }

    /**
     * MK2 WAVE 1 — OSImageNudgeService PROCEED — 충돌 후보 보존, 신규 자원만 ACTIVE 등록.
     * 외부 endpoint 와 직접 묶이지 않은 내부 진입점. 호출자가 LifecycleStage 검증을 마쳤다고 가정.
     */
    @Transactional
    public Long completePendingOSImageFromNudge(NudgeSession session) {
        if (!(session.payload() instanceof com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload payload)) {
            throw new IllegalOSImageStateException(
                    "OSImage nudge 세션은 IntentMetaNudgePayload 만 허용합니다. nudgeId=" + session.nudgeId());
        }
        OSName osName = OSName.valueOf(payload.attributes().get("osName"));
        String osVersion = payload.attributes().get("osVersion");
        // PROCEED — 활성 (osName, osVersion) 가 새로 생기면 안 된다 (race). 재검사.
        if (osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(osName, osVersion)) {
            // race — 다른 트랜잭션이 같은 메타로 활성 자원을 만든 경우. 명시적 fail.
            throw new DuplicateOSImageException(osName, osVersion);
        }
        return persistNewOSImage(osName, osVersion, payload.attributes().get("description"));
    }

    /**
     * MK2 WAVE 1 — REPLACE 흐름의 target purge. 본 메서드는 OSImage 자체의 soft-deleted row 만 hard-delete
     * 한다 (자식 ISO 도 cascade — DB FK ON DELETE CASCADE 가 처리).
     */
    @Transactional
    public void purgeOSImageForNudge(OSImage target) {
        if (!target.isDeleted() && !target.isDeprecated()) {
            throw new IllegalOSImageStateException(
                    "활성 자원은 nudge replace 대상이 될 수 없습니다. id=" + target.getId());
        }
        osImageRepository.delete(target);
        log.info("[osImage] purge for nudge replace : id={}, osName={}, osVersion={}",
                target.getId(), target.getOsName(), target.getOsVersion());
    }

    @Transactional
    public void update(Long id, OSImageUpdateRequest request) {
        OSImage image = requireActiveImage(id);
        if (!image.getOsVersion().equals(request.osVersion())
                && osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(image.getOsName(), request.osVersion())) {
            throw new DuplicateOSImageException(image.getOsName(), request.osVersion());
        }
        image.update(request.osVersion(), request.description());
    }

    @Transactional
    public void toggleEnabled(Long id) {
        requireActiveImage(id).toggleEnabled();
    }

    /**
     * OS 이미지 soft delete. OSImage 엔티티의 override 가 자식 활성 ISO 도 함께 soft delete 한다.
     */
    @Transactional
    public void softDelete(Long id) {
        requireActiveImage(id).softDelete();
    }

    @Transactional
    public void restore(Long id) {
        OSImage image = osImageRepository.findByIdAndIsDeletedTrue(id)
                .orElseThrow(() -> new IllegalOSImageStateException(
                        "이미 활성 상태이거나 존재하지 않는 OS 버전입니다. id=" + id));
        if (osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(image.getOsName(), image.getOsVersion())) {
            throw new DuplicateOSImageException(image.getOsName(), image.getOsVersion());
        }
        image.restore();
    }

    // ---- MK2 OSImage lifecycle ----------------------------------------

    @Transactional
    public void deprecateImage(Long id) {
        requireActiveImage(id).deprecate();
    }

    @Transactional
    public void undeprecateImage(Long id) {
        requireActiveImage(id).undeprecate();
    }

    /**
     * OSImage 영구 삭제. soft-deleted 상태에서만 호출 가능.
     * <p>자식 ISO 들의 sidecar 파일 정리 후 row 삭제. cascade 가 ISO 행을 자동 제거하지만, sidecar 파일은
     * 어플리케이션이 직접 정리해야 한다.</p>
     */
    @Transactional
    public void purgeImage(Long id) {
        OSImage image = osImageRepository.findByIdAndIsDeletedTrue(id)
                .orElseThrow(() -> new IllegalOSImageStateException(
                        "soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. id=" + id));
        // 자식 ISO 들의 sidecar 파일 정리 (DB row 는 cascade 로 제거됨).
        for (ISO iso : image.getIsos()) {
            cleanupIsoArtifacts(iso);
        }
        osImageRepository.delete(image);
        log.info("[purgeImage] OS 이미지 영구 삭제 완료. id={}", id);
    }

    // ==== ISO 쓰기 연산 ================================================

    @Transactional
    public Long addISO(Long osImageId, ISOCreateRequest request, MultipartFile uploadedFile) {
        PreparedIsoRegistration prepared = prepareIsoRegistration(osImageId, request, uploadedFile);
        return finalizePreparedIsoRegistration(null, prepared);
    }

    @Transactional
    public PreparedIsoRegistration prepareIsoRegistration(Long osImageId,
                                                          ISOCreateRequest request,
                                                          MultipartFile uploadedFile) {
        requireActiveImage(osImageId);

        boolean hasFile = uploadedFile != null && !uploadedFile.isEmpty();
        String originalFilename = hasFile ? uploadedFile.getOriginalFilename() : null;
        String resolvedPath = IsoPathResolver.resolve(
                request.isoPath(),
                originalFilename,
                path -> new InvalidIsoPathException("경로가 '/' 로 끝나면 업로드할 파일이 필요합니다 : " + path));

        log.info("[prepareIsoRegistration] osImageId={}, raw={}, resolved={}, allowCreateDirectory={}, hasFile={}",
                osImageId, request.isoPath(), resolvedPath, request.allowCreateDirectory(), hasFile);

        ensureParentDirectory(resolvedPath, request.allowCreateDirectory());
        // S3 — allowlist 검증
        Path target = pathPolicyService.assertWritablePath(resolvedPath);

        isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(osImageId, resolvedPath)
                .ifPresent(existing -> {
                    throw new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다 : "
                            + existing.getIsoPath());
                });

        Path sidecar = markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR);
        if (Files.exists(sidecar)) {
            throw new SidecarConflictException(sidecar.toString());
        }

        if (hasFile) {
            if (Files.exists(target) && !Files.isDirectory(target)) {
                throw new DuplicateFilenameException(resolvedPath);
            }
            storeUploadedFile(uploadedFile, resolvedPath);
            // S3.1 (B1) — 저장 직후 POSIX 권한 0644 강제 (의도치 않은 실행 권한 차단).
            fileSystemHardener.applyDefaultPermissionsForFile(target);
        } else if (!Files.isRegularFile(target)) {
            throw new InvalidIsoPathException("ISO 파일이 해당 경로에 존재하지 않습니다 : " + resolvedPath);
        }

        String originalName = hasFile
                ? originalFilename
                : target.getFileName().toString();
        return new PreparedIsoRegistration(
                osImageId,
                resolvedPath,
                request.description(),
                originalName != null ? originalName : "",
                hasFile
        );
    }

    @Transactional
    public Long finalizePreparedIsoRegistration(String jobId, PreparedIsoRegistration prepared) {
        // S3 — finalize 직전 다시 한 번 allowlist 검증 (이중 가드)
        pathPolicyService.assertWritablePath(prepared.resolvedPath());
        OSImage parent = requireActiveImage(prepared.osImageId());
        Path target = Path.of(prepared.resolvedPath());

        String manifestHash = computeFileSha256(target);
        startJobStage(jobId, IsoRegistrationStage.CHECK_DUPLICATE);

        isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(prepared.osImageId(), prepared.resolvedPath())
                .ifPresent(existing -> {
                    throw new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다 : "
                            + existing.getIsoPath());
                });

        // 활성 ISO 와의 해시 충돌 → fail-fast.
        isoRepository.findFirstByChecksumAndIsDeletedFalse(manifestHash).ifPresent(existing -> {
            if (prepared.uploadedFile()) {
                deleteQuietly(target);
                deleteQuietly(markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR));
            }
            throw new DuplicateISOContentException(existing.getIsoPath());
        });

        // MK2 — soft-deleted / deprecated 자원과의 해시 충돌 → nudge 흐름.
        // 자동 purge 로 기존 row 를 silently 정리하지 않는다 — 사용자가 modal 에서 결정해야 한다.
        List<ISO> dormantConflicts =
                isoRepository.findByManifestHashAndIsDeletedTrueOrIsDeprecatedTrue(manifestHash);
        if (!dormantConflicts.isEmpty()) {
            NudgeRequiredResponse payload = registerIsoNudgeSession(
                    prepared, manifestHash, dormantConflicts);
            // 임시 파일은 그대로 두고 (confirm 시 정식 등록), 예외로 단계 B 결과를 호출자에게 전달.
            // job runner 가 본 예외를 catch 해 fail 메시지에 nudgeId 를 동봉, UI 가 폴링해 modal 표시.
            throw new IsoNudgeRequiredException(payload);
        }

        startJobStage(jobId, IsoRegistrationStage.PERSIST_METADATA);

        ISO saved = isoRepository.save(ISO.builder()
                .osImage(parent)
                .isoPath(prepared.resolvedPath())
                .checksum(manifestHash)
                .manifestHash(manifestHash)
                .markerSignature(null)
                .description(prepared.description())
                .isEnabled(true)
                .isDeleted(false)
                .build());

        finalizeMarker(saved, prepared, manifestHash, target);

        log.info("[finalizePreparedIsoRegistration] 등록 완료. isoId={}, osImageId={}, uploadedFile={}, hash={}",
                saved.getId(), prepared.osImageId(), prepared.uploadedFile(), manifestHash);
        return saved.getId();
    }

    private NudgeRequiredResponse registerIsoNudgeSession(PreparedIsoRegistration prepared,
                                                          String manifestHash,
                                                          List<ISO> dormantConflicts) {
        List<NudgeConflictEntry> conflicts = dormantConflicts.stream()
                .map(c -> new NudgeConflictEntry(
                        c.getId(),
                        c.currentStage(),
                        c.getManifestHash(),
                        c.getOsImage().getOsVersion(),
                        c.getIsoPath(),
                        // ISO 엔티티가 보유한 createdAt 은 LocalDateTime — Instant 로 환산.
                        c.getCreatedAt() != null
                                ? c.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()
                                : Instant.EPOCH))
                .toList();
        List<Long> conflictIds = dormantConflicts.stream().map(ISO::getId).toList();

        com.example.serverprovision.management.common.nudge.ContentNudgePayload payload =
                new com.example.serverprovision.management.common.nudge.ContentNudgePayload(
                        // OS_ISO 의 name 슬롯에는 isoPath 를 담는다 (도메인별 의미 매핑).
                        prepared.resolvedPath(),
                        "",
                        manifestHash,
                        prepared.resolvedPath(),
                        Map.of(
                                "osImageId", String.valueOf(prepared.osImageId()),
                                "originalFilename", prepared.originalFilename(),
                                "description", prepared.description() != null ? prepared.description() : "",
                                "uploadedFile", String.valueOf(prepared.uploadedFile())
                        )
                );
        NudgeSession session = nudgeRegistry.register(
                NudgeResourceType.OS_ISO,
                prepared.osImageId(),
                conflictIds,
                payload
        );
        return NudgeRequiredResponse.of(session.nudgeId(), conflicts, session.expiresAt());
    }

    /**
     * MK2 — nudge confirm (PROCEED / REPLACE) 에서 호출. 임시 파일이 가리키는 자원을 ACTIVE 로 영속화.
     */
    @Transactional
    public Long completePendingIsoFromNudge(NudgeSession session) {
        if (session.resourceType() != NudgeResourceType.OS_ISO) {
            throw new IllegalOSImageStateException("OS_ISO 세션이 아닙니다.");
        }
        Long osImageId = session.boardId();
        OSImage parent = requireActiveImage(osImageId);
        if (!(session.payload() instanceof com.example.serverprovision.management.common.nudge.ContentNudgePayload p)) {
            throw new IllegalOSImageStateException(
                    "OS_ISO nudge 세션은 ContentNudgePayload 만 허용합니다. nudgeId=" + session.nudgeId());
        }
        Path target = Path.of(p.tempFilePath());
        String manifestHash = p.manifestHash();
        String description = p.attributes().getOrDefault("description", "");
        String originalFilename = p.attributes().getOrDefault("originalFilename", "");
        boolean uploadedFile = Boolean.parseBoolean(p.attributes().getOrDefault("uploadedFile", "false"));

        // confirm 시점에 활성 ISO 와 해시가 또 충돌하면 fail-fast (다른 사용자가 그 사이 같은 자원을 등록).
        isoRepository.findFirstByChecksumAndIsDeletedFalse(manifestHash).ifPresent(existing -> {
            if (uploadedFile) {
                deleteQuietly(target);
                deleteQuietly(markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR));
            }
            throw new DuplicateISOContentException(existing.getIsoPath());
        });

        ISO saved = isoRepository.save(ISO.builder()
                .osImage(parent)
                .isoPath(p.tempFilePath())
                .checksum(manifestHash)
                .manifestHash(manifestHash)
                .markerSignature(null)
                .description(description)
                .isEnabled(true)
                .isDeleted(false)
                .build());

        PreparedIsoRegistration prepared = new PreparedIsoRegistration(
                osImageId, p.tempFilePath(), description, originalFilename, uploadedFile);
        finalizeMarker(saved, prepared, manifestHash, target);
        log.info("[completePendingIsoFromNudge] 신규 ISO 영속화 완료. isoId={}, osImageId={}, hash={}",
                saved.getId(), osImageId, manifestHash);
        return saved.getId();
    }

    /**
     * MK2 — REPLACE confirm 에서 충돌 후보 ISO 를 영구 삭제할 때 nudge 서비스가 호출한다.
     * 외부 endpoint 와 직접 묶이지 않은 내부 진입점. 호출자 (OsNudgeService) 가 LifecycleStage 검증을 마쳤다고 가정.
     */
    @Transactional
    public void purgeIsoForNudge(ISO target) {
        cleanupIsoArtifacts(target);
        isoRepository.delete(target);
        log.info("[purgeIsoForNudge] 충돌 후보 ISO 영구 삭제. isoId={}", target.getId());
    }

    private void finalizeMarker(ISO saved,
                                PreparedIsoRegistration prepared,
                                String manifestHash,
                                Path target) {
        MarkerContent unsigned = new MarkerContent(
                ResourceType.OS_ISO.name(),
                saved.getId(),
                Map.of(
                        "osImageId", String.valueOf(prepared.osImageId()),
                        "originalFilename", prepared.originalFilename()
                ),
                Instant.now(),
                manifestHash,
                null
        );
        String signature = markerService.computeSignature(unsigned);
        saved.reissueMarker(manifestHash, signature);
        markerService.write(target, MarkerLayout.SIDECAR, unsigned.withSignature(signature));
        // S3.1 (B1) + S3.2 (K12) — sidecar 경로는 ProvisionMarkerService 의 resolveMarkerFile 로 계산.
        fileSystemHardener.applyDefaultPermissionsForFile(
                markerService.resolveMarkerFile(target, MarkerLayout.SIDECAR));
    }

    /** 디스크에 이미 있는 파일의 SHA-256. */
    private String computeFileSha256(Path file) {
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

    private void ensureParentDirectory(String targetPath, boolean allowCreateDirectory) {
        Path target;
        try {
            target = Path.of(targetPath);
        } catch (java.nio.file.InvalidPathException e) {
            throw new ISOFileStorageException("ISO 경로 형식이 올바르지 않습니다 : " + targetPath, e);
        }
        Path parent = target.getParent();
        if (parent == null || Files.exists(parent)) {
            return;
        }
        if (!allowCreateDirectory) {
            log.info("[addISO] 상위 디렉토리 없음 + 생성 미허용 → 거절. parent={}", parent);
            throw new DirectoryMissingException(parent.toString());
        }
        try {
            Files.createDirectories(parent);
            log.info("[addISO] 상위 디렉토리 자동 생성. parent={}", parent);
        } catch (IOException e) {
            throw new ISOFileStorageException("디렉토리 생성에 실패했습니다. path=" + parent, e);
        }
    }

    private void storeUploadedFile(MultipartFile file, String targetPath) {
        try {
            Path target = Path.of(targetPath);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new ISOFileStorageException("ISO 파일 저장에 실패했습니다. path=" + targetPath + " (" + reason + ")", e);
        }
    }

    private void startJobStage(String jobId, IsoRegistrationStage stage) {
        if (jobId != null && !jobId.isBlank()) {
            backgroundJobService.startStage(jobId, stage);
        }
    }

    private void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
            // 중복 판정 직후의 파일 제거 실패는 핵심 흐름을 막지 않는다 — 운영자가 별도로 정리할 대상.
        }
    }

    /**
     * ISO 의 디스크 부산물 (본체 파일 + sidecar) 을 모두 정리한다. purge / replace 시 공통 사용.
     */
    private void cleanupIsoArtifacts(ISO iso) {
        Path body = Path.of(iso.getIsoPath());
        deleteQuietly(markerService.resolveMarkerFile(body, MarkerLayout.SIDECAR));
        deleteQuietly(body);
    }

    public record PreparedIsoRegistration(
            Long osImageId,
            String resolvedPath,
            String description,
            String originalFilename,
            boolean uploadedFile
    ) {}

    @Transactional
    public void updateISO(Long osImageId, Long isoId, ISOUpdateRequest request) {
        // S4.x — isoPath 를 PathPolicyService 로 검증해 ".." 등 traversal 입력이 DB 에 silent 저장되는 사고 차단.
        Path validated = pathPolicyService.assertWritablePath(request.isoPath());
        requireLiveISO(osImageId, isoId).update(validated.toString(), request.description());
    }

    @Transactional
    public void toggleIsoEnabled(Long osImageId, Long isoId) {
        requireLiveISO(osImageId, isoId).toggleEnabled();
    }

    @Transactional
    public void softDeleteISO(Long osImageId, Long isoId) {
        requireLiveISO(osImageId, isoId).softDelete();
    }

    @Transactional
    public void restoreISO(Long osImageId, Long isoId) {
        requireActiveImage(osImageId);
        ISO iso = isoRepository.findByIdAndOsImage_Id(isoId, osImageId)
                .orElseThrow(() -> new ISONotFoundException(osImageId, isoId));
        if (!iso.isDeleted()) {
            throw new IllegalOSImageStateException("이미 활성 상태인 ISO 입니다. isoId=" + isoId);
        }
        iso.restore();
    }

    // ---- MK2 ISO lifecycle --------------------------------------------

    @Transactional
    public void deprecateIso(Long osImageId, Long isoId) {
        requireLiveISO(osImageId, isoId).deprecate();
    }

    @Transactional
    public void undeprecateIso(Long osImageId, Long isoId) {
        ISO iso = requireLiveISO(osImageId, isoId);
        iso.undeprecate();
    }

    /**
     * ISO 영구 삭제. soft-deleted 상태에서만 호출 가능. sidecar + 본체 파일 모두 정리.
     */
    @Transactional
    public void purgeIso(Long osImageId, Long isoId) {
        requireActiveImage(osImageId);
        ISO iso = isoRepository.findByIdAndOsImage_Id(isoId, osImageId)
                .orElseThrow(() -> new ISONotFoundException(osImageId, isoId));
        if (iso.currentStage() != LifecycleStage.SOFT_DELETED) {
            throw new IllegalOSImageStateException(
                    "soft-deleted 상태가 아니어서 영구 삭제할 수 없습니다. isoId=" + isoId);
        }
        cleanupIsoArtifacts(iso);
        isoRepository.delete(iso);
        log.info("[purgeIso] ISO 영구 삭제 완료. osImageId={}, isoId={}", osImageId, isoId);
    }

    // ==== 무결성 검증 / 마커 재발급 (BIOS 와 동일 패턴) ===================

    public IntegrityStatus verifyIntegrity(Long osImageId, Long isoId) {
        requireActiveImage(osImageId);
        ISO iso = isoRepository.findByIdAndOsImage_Id(isoId, osImageId)
                .orElseThrow(() -> new ISONotFoundException(osImageId, isoId));
        if (iso.isDeleted()) {
            throw new IllegalOSImageStateException("삭제된 ISO 는 검증할 수 없습니다. isoId=" + isoId);
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
    public IntegrityStatus verifyAndRecordIntegrity(Long osImageId, Long isoId) {
        IntegrityStatus status = verifyIntegrity(osImageId, isoId);
        requireLiveISO(osImageId, isoId).recordIntegritySnapshot(status, Instant.now());
        return status;
    }

    // ==== 환경·그룹 응답 조립 (A1-1) =====================================

    private List<OSEnvironmentResponse> buildEnvResponses(OSImage image) {
        List<OSEnvironment> envs = envRepository
                .findAllByOsImage_IdOrderByEnvironmentCode_ValueAsc(image.getId());
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

    private List<OSPackageGroupResponse> buildGroupResponses(OSImage image) {
        List<OSPackageGroup> grps = grpRepository
                .findAllByOsImage_IdOrderByGroupCode_ValueAsc(image.getId());
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

    private OSImage requireActiveImage(Long id) {
        return osImageRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new OSImageNotFoundException(id));
    }

    private ISO requireLiveISO(Long osImageId, Long isoId) {
        requireActiveImage(osImageId);
        ISO iso = isoRepository.findByIdAndOsImage_Id(isoId, osImageId)
                .orElseThrow(() -> new ISONotFoundException(osImageId, isoId));
        if (iso.isDeleted()) {
            throw new IllegalOSImageStateException("삭제된 ISO 에는 수행할 수 없는 작업입니다. isoId=" + isoId);
        }
        return iso;
    }

    private static List<ISO> visibleISOs(List<ISO> all, boolean includeDeleted) {
        return includeDeleted ? all : all.stream().filter(iso -> !iso.isDeleted()).toList();
    }
}
