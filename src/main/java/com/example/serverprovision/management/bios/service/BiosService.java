package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.dto.response.BiosResponse;
import com.example.serverprovision.management.bios.dto.response.BoardWithBiosListResponse;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.BundleExtractionException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.exception.IllegalBiosStateException;
import com.example.serverprovision.management.bios.exception.MarkerConflictException;
import com.example.serverprovision.management.bios.exception.MarkerSignatureMismatchException;
import com.example.serverprovision.management.bios.exception.ManifestHashMismatchException;
import com.example.serverprovision.management.bios.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A3 v3 BIOS 번들 도메인 로직 총괄. 번들 저장 · marker 관리 · 무결성 검증을 조합한다.
 *
 * <p>전반적 흐름 :</p>
 * <ul>
 *   <li>등록 : 검증 → 기존 soft-deleted 동일 (board, version) 정리 → 트리 전개 → manifest 계산 →
 *       엔티티 선 저장 → marker signature 계산(biosId 포함) → entity.reissueMarker → marker 파일 기록</li>
 *   <li>조회 : Miller 전체 뷰 (N+1 방지 배치 조회) + integrityStatus 는 NOT_VERIFIED 로 내려감</li>
 *   <li>검증 : {@code .provision.json} 읽어 서명 + manifestHash 재계산 비교</li>
 *   <li>재발급 : 현재 트리 내용으로 manifest 재계산 → 엔티티 · marker 갱신 (관리자 명시 액션 전제)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BiosService {

    private final BiosRepository biosRepository;
    private final BoardModelRepository boardModelRepository;
    private final BundleExtractionService bundleExtractionService;
    private final BundleEntrypointDetector bundleEntrypointDetector;
    private final BundleManifestService bundleManifestService;
    private final ProvisionMarkerService provisionMarkerService;
    private final TargetDirectoryPolicyService targetDirectoryPolicyService;
    private final BundleTreeCleanupService bundleTreeCleanupService;

    // ==== 조회 ========================================================

    public List<BoardWithBiosListResponse> findAllGrouped(boolean includeDeleted) {
        List<BoardModel> boards = includeDeleted
                ? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
                : boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();
        if (boards.isEmpty()) return List.of();

        List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
        List<BoardBIOS> allBios = biosRepository.findAllByBoardModel_IdIn(boardIds);
        Map<Long, List<BoardBIOS>> byBoard = allBios.stream()
                .filter(b -> includeDeleted || !b.isDeleted())
                .collect(Collectors.groupingBy(b -> b.getBoardModel().getId(), HashMap::new, Collectors.toList()));

        return boards.stream()
                .map(board -> new BoardWithBiosListResponse(
                        board.getId(),
                        board.getVendor(),
                        board.getVendor().getDisplayName(),
                        board.getModelName(),
                        board.isDeleted(),
                        byBoard.getOrDefault(board.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(BoardBIOS::getVersion).reversed())
                                .map(BiosService::toResponse)
                                .toList()
                ))
                .toList();
    }

    public BiosResponse findBios(Long boardId, Long biosId) {
        return toResponse(requireLiveBios(boardId, biosId));
    }

    // ==== 쓰기 연산 ====================================================

    @Transactional
    public Long addBios(Long boardId,
                        BiosCreateRequest request,
                        BiosUploadMode uploadMode,
                        MultipartFile[] folderFiles,
                        MultipartFile zipFile,
                        MultipartFile singleFile) {
        BoardModel parent = requireActiveBoard(boardId);

        // 1) 활성 (board, version) 중복 검사
        if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
            throw new DuplicateBiosVersionException(boardId, request.version());
        }

        Path targetDir = Path.of(request.targetDirectory());

        // 2) soft-deleted 같은 (board, version) 존재 시 기존 트리·레코드 물리 정리 (D3 정책)
        biosRepository.findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(boardId, request.version())
                .ifPresent(existing -> {
                    bundleTreeCleanupService.purgeExistingTree(Path.of(existing.getTreeRootPath()), "purgeExistingTree");
                    biosRepository.delete(existing);
                });

        // 3) targetDirectory 상태 검증 — 상위 dir 존재 or allowCreateDirectory, 그리고 자기 자신이 비어있거나 부재
        targetDirectoryPolicyService.prepareForUpload(targetDir, request.allowCreateDirectory());

        try {
            // 4) 업로드 페이로드 전개 (extractionService 가 targetDirectory 생성 · 비어있음 검증을 내부 수행)
            switch (uploadMode) {
                case FOLDER -> bundleExtractionService.extractFolder(folderFiles, targetDir);
                case ZIP -> bundleExtractionService.extractZip(zipFile, targetDir);
                case SINGLE_FILE -> bundleExtractionService.extractSingleFile(singleFile, targetDir);
            }

            // 5) 진입점 탐지 (override 우선)
            String entrypoint = bundleEntrypointDetector.detect(targetDir, request.entrypointRelativePath());

            // 6) manifest 집계
            ManifestSummary manifest = bundleManifestService.compute(targetDir);

            // 7) 2-phase save : 엔티티 선 저장 (signature=null) → biosId 획득
            BoardBIOS saved = biosRepository.save(BoardBIOS.builder()
                    .boardModel(parent)
                    .name(request.name())
                    .version(request.version())
                    .treeRootPath(targetDir.toString())
                    .entrypointRelativePath(entrypoint)
                    .manifestHash(manifest.manifestHash())
                    .markerSignature(null)
                    .fileCount(manifest.fileCount())
                    .totalBytes(manifest.totalBytes())
                    .description(request.description())
                    .isEnabled(true)
                    .isDeleted(false)
                    .build());

            // 8) biosId 를 포함한 marker 생성 + 서명 + entity 갱신 + 파일 기록
            MarkerContent unsigned = new MarkerContent(
                    ResourceType.BIOS_BUNDLE.name(),
                    saved.getId(),
                    Map.of(
                            "boardId", String.valueOf(boardId),
                            "version", request.version(),
                            "entrypointRelativePath", entrypoint
                    ),
                    Instant.now(),
                    manifest.manifestHash(),
                    null
            );
            String signature = provisionMarkerService.computeSignature(unsigned);
            MarkerContent signed = unsigned.withSignature(signature);
            saved.reissueMarker(manifest.manifestHash(), signature);
            provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, signed);

            log.info("[addBios] 등록 완료. biosId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
                    saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes());
            return saved.getId();
        } catch (RuntimeException e) {
            bundleTreeCleanupService.cleanupFailedUpload(targetDir, "purgeExistingTree", "addBios", e);
            throw e;
        }
    }

    @Transactional
    public void update(Long boardId, Long biosId, BiosUpdateRequest request) {
        BoardBIOS bios = requireLiveBios(boardId, biosId);
        if (!bios.getVersion().equals(request.version())
                && biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
            throw new DuplicateBiosVersionException(boardId, request.version());
        }
        bios.update(request.name(), request.version(), request.description());
    }

    @Transactional
    public void toggleEnabled(Long boardId, Long biosId) {
        requireLiveBios(boardId, biosId).toggleEnabled();
    }

    @Transactional
    public void softDelete(Long boardId, Long biosId) {
        requireLiveBios(boardId, biosId).softDelete();
    }

    @Transactional
    public void restore(Long boardId, Long biosId) {
        requireActiveBoard(boardId);
        BoardBIOS bios = biosRepository.findByIdAndBoardModel_Id(biosId, boardId)
                .orElseThrow(() -> new BiosNotFoundException(boardId, biosId));
        if (!bios.isDeleted()) {
            throw new IllegalBiosStateException("이미 활성 상태인 BIOS 입니다. biosId=" + biosId);
        }
        if (biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, bios.getVersion())) {
            throw new DuplicateBiosVersionException(boardId, bios.getVersion());
        }
        bios.restore();
    }

    // ==== 무결성 / marker 재발급 =======================================

    public IntegrityStatus verifyIntegrity(Long boardId, Long biosId) {
        BoardBIOS bios = requireLiveBios(boardId, biosId);
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

    // 단건 BIOS marker 재발급 메서드는 위험도가 높아 외부 endpoint 와 함께 제거됨.
    // 일괄 재발급(secret 회전 시)은 PathReconciliationService.performReissue 가 담당.
    // 이전 hash → 새 hash audit 로그도 그곳의 일괄 audit 으로 통합.

    // ==== 내부 헬퍼 =====================================================

    private BoardModel requireActiveBoard(Long boardId) {
        return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardId));
    }

    private BoardBIOS requireLiveBios(Long boardId, Long biosId) {
        requireActiveBoard(boardId);
        BoardBIOS bios = biosRepository.findByIdAndBoardModel_Id(biosId, boardId)
                .orElseThrow(() -> new BiosNotFoundException(boardId, biosId));
        if (bios.isDeleted()) {
            throw new IllegalBiosStateException("삭제된 BIOS 에는 수행할 수 없는 작업입니다. biosId=" + biosId);
        }
        return bios;
    }

    private static BiosResponse toResponse(BoardBIOS entity) {
        return new BiosResponse(
                entity.getId(),
                entity.getBoardModel().getId(),
                entity.getName(),
                entity.getVersion(),
                entity.getTreeRootPath(),
                entity.getEntrypointRelativePath(),
                entity.getManifestHash(),
                entity.getFileCount(),
                entity.getTotalBytes(),
                entity.getDescription(),
                IntegrityStatus.NOT_VERIFIED,
                entity.isEnabled(),
                entity.isDeleted()
        );
    }
}
