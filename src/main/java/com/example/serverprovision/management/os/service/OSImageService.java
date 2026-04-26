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
import com.example.serverprovision.management.os.exception.IsoUploadIntentConflictException;
import com.example.serverprovision.management.os.exception.OSImageNotFoundException;
import com.example.serverprovision.management.os.repository.ISORepository;
import com.example.serverprovision.management.os.repository.OSEnvironmentRepository;
import com.example.serverprovision.management.os.repository.OSImageRepository;
import com.example.serverprovision.management.os.repository.OSPackageGroupRepository;
import com.example.serverprovision.management.os.util.IsoPathResolver;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.exception.SidecarConflictException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
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
 * <ul>
 *   <li>Controller 는 Request / Response 만 주고받는다.</li>
 *   <li>엔티티 상태 전이(토글/삭제/복구)는 모두 이 서비스의 도메인 메서드 호출로 수행한다.</li>
 *   <li>{@code softDelete(id)} 는 자식 ISO 중 활성인 것들까지 동반 soft 삭제한다 (엔티티 내부 로직).</li>
 *   <li>환경/그룹의 "제공 ISO" 배지는 {@code buildEnvResponses} / {@code buildGroupResponses} 가 ISO 순회로 집계한다.</li>
 * </ul>
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

    // ==== 조회 ========================================================

    /**
     * OS 이미지 단건 조회 (수정 폼 프리필 용도). 삭제된 레코드는 NotFound 로 취급한다.
     */
    public OSImageResponse findById(Long id) {
        OSImage image = requireActiveImage(id);
        return OSImageResponse.of(
                image,
                visibleISOs(image.getIsos(), false),
                buildEnvResponses(image),
                buildGroupResponses(image)
        );
    }

    /**
     * ISO 단건 조회 (ISO 수정 폼 프리필 용도). 부모 OS 가 살아있고 ISO 도 활성 상태여야 반환한다.
     */
    public ISOResponse findISO(Long osImageId, Long isoId) {
        return ISOResponse.from(requireLiveISO(osImageId, isoId));
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
        if (osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(request.osName(), request.osVersion())) {
            throw new DuplicateOSImageException(request.osName(), request.osVersion());
        }
        OSImage saved = osImageRepository.save(OSImage.builder()
                .osName(request.osName())
                .osVersion(request.osVersion())
                .description(request.description())
                .isEnabled(true)
                .isDeleted(false)
                .build());
        return saved.getId();
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
     * OS 이미지 soft delete.
     * <p>OSImage 와 활성 ISO 들만 {@code isDeleted=true} 로 전환한다. 환경·패키지 그룹은 물리적으로 남겨
     * 추후 복구 시 관계가 자동으로 다시 보인다. "삭제된 것처럼 보이게" 는 응답 조립 단계에서
     * 활성 ISO provider 가 없는 환경·그룹을 제외하는 필터로 구현된다
     * ({@link #buildEnvResponses}, {@link #buildGroupResponses}).</p>
     */
    @Transactional
    public void softDelete(Long id) {
        requireActiveImage(id).softDelete();
    }

    @Transactional
    public void restore(Long id) {
        OSImage image = osImageRepository.findByIdAndIsDeletedTrue(id)
                .orElseThrow(() -> new IllegalOSImageStateException(
                        "이미 활성 상태이거나 존재하지 않는 OS 이미지입니다. id=" + id));
        if (osImageRepository.existsByOsNameAndOsVersionAndIsDeletedFalse(image.getOsName(), image.getOsVersion())) {
            throw new DuplicateOSImageException(image.getOsName(), image.getOsVersion());
        }
        image.restore();
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
        Path target = Path.of(resolvedPath);

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
        OSImage parent = requireActiveImage(prepared.osImageId());
        Path target = Path.of(prepared.resolvedPath());

        String manifestHash = computeFileSha256(target);
        startJobStage(jobId, IsoRegistrationStage.CHECK_DUPLICATE);

        isoRepository.findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(prepared.osImageId(), prepared.resolvedPath())
                .ifPresent(existing -> {
                    throw new IsoUploadIntentConflictException("같은 경로에 이미 등록된 ISO 가 있습니다 : "
                            + existing.getIsoPath());
                });

        isoRepository.findFirstByChecksumAndIsDeletedFalse(manifestHash).ifPresent(existing -> {
            if (prepared.uploadedFile()) {
                deleteQuietly(target);
            }
            throw new DuplicateISOContentException(existing.getIsoPath());
        });

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

        log.info("[finalizePreparedIsoRegistration] 등록 완료. isoId={}, osImageId={}, uploadedFile={}, hash={}",
                saved.getId(), prepared.osImageId(), prepared.uploadedFile(), manifestHash);
        return saved.getId();
    }

    /** 디스크에 이미 있는 파일의 SHA-256. 경로만 등록 케이스에서 manifestHash 산출용. */
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

    /**
     * 상위 디렉토리가 없으면 {@code allowCreateDirectory} 플래그에 따라 생성하거나 예외를 던진다.
     * addISO 의 진입부에서 먼저 호출되어, 파일이 없는 "경로만 등록" 케이스에도 동일하게 적용된다.
     */
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

    /**
     * 업로드 스트림을 목적 경로에 저장한다.
     * 해시 계산은 foreground 요청에서 제거되었고, background 후처리 단계가 파일을 다시 읽어 수행한다.
     */
    private void storeUploadedFile(MultipartFile file, String targetPath) {
        try {
            Path target = Path.of(targetPath);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
        } catch (IOException e) {
            // IOException 의 cause 메시지(예: "No space left on device") 까지 노출해 운영자 진단을 돕는다.
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

    public record PreparedIsoRegistration(
            Long osImageId,
            String resolvedPath,
            String description,
            String originalFilename,
            boolean uploadedFile
    ) {}

    @Transactional
    public void updateISO(Long osImageId, Long isoId, ISOUpdateRequest request) {
        requireLiveISO(osImageId, isoId).update(request.isoPath(), request.description());
    }

    @Transactional
    public void toggleIsoEnabled(Long osImageId, Long isoId) {
        requireLiveISO(osImageId, isoId).toggleEnabled();
    }

    /**
     * ISO soft delete.
     * <p>{@code isDeleted=true} 만 전환한다. 이 ISO 가 유일 provider 였던 환경·그룹은 물리 삭제하지 않고,
     * 응답 조립 단계에서 "활성 provider 가 없으면 제외" 하는 필터로 노출만 숨긴다. 복구 시 자연스럽게
     * 다시 보인다.</p>
     */
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

    // ==== 무결성 검증 / 마커 재발급 (BIOS 와 동일 패턴) ===================

    /**
     * ISO sidecar 마커 검증. BIOS {@code BiosService.verifyIntegrity} 와 동일 4 단계:
     * marker 존재 → signature 검증 → manifestHash 재계산 비교 → 결과 enum 반환.
     * 단일 파일 ISO 라 manifestHash 재계산 = SHA-256(file bytes).
     */
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
            // 마커는 있으나 본체 파일이 사라진 케이스 — TAMPERED 와 동일하게 무결성 깨짐으로 처리.
            return IntegrityStatus.TAMPERED;
        }
        String recomputed = computeFileSha256(target);
        if (!markerService.verifyManifestHash(marker, recomputed)) {
            return IntegrityStatus.TAMPERED;
        }
        return IntegrityStatus.ORIGINAL;
    }

    // 단건 ISO marker 재발급 메서드는 위험도가 높아 외부 endpoint 와 함께 제거됨.
    // 일괄 재발급(secret 회전 시)은 PathReconciliationService.performReissue 가 담당.

    // ==== 환경·그룹 응답 조립 (A1-1) =====================================

    /**
     * 설치 환경 Response 목록.
     * <p>"삭제된 것처럼 보이게" 원칙에 따라, 활성(isDeleted=false) ISO 가 하나도 제공하지 않는 환경은 응답에서 제외한다.
     * 물리 row 는 유지되므로 ISO 를 restore 하면 관계가 다시 살아난다.</p>
     */
    private List<OSEnvironmentResponse> buildEnvResponses(OSImage image) {
        List<OSEnvironment> envs = envRepository
                .findAllByOsImage_IdOrderByEnvironmentCode_ValueAsc(image.getId());
        if (envs.isEmpty()) return List.of();

        Map<Long, List<IsoProvisionView>> envProviders = new HashMap<>();
        for (ISO iso : image.getIsos()) {
            if (iso.isDeleted()) continue; // 삭제된 ISO 의 제공 관계는 집계하지 않는다.
            for (OSEnvironment e : iso.getProvidedEnvironments()) {
                envProviders
                        .computeIfAbsent(e.getId(), k -> new ArrayList<>())
                        .add(new IsoProvisionView(iso.getId(), image.getOsVersion(), iso.getIsoPath()));
            }
        }

        return envs.stream()
                .filter(e -> envProviders.containsKey(e.getId())) // 활성 provider 없는 환경은 숨김
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

    /**
     * 패키지 그룹 Response 목록. 환경 응답과 동일한 필터 정책.
     */
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
