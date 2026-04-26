package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.BundleExtractionException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.bios.service.BundleEntrypointDetector;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.dto.request.BmcUpdateRequest;
import com.example.serverprovision.management.bmc.dto.response.BmcResponse;
import com.example.serverprovision.management.bmc.dto.response.BoardWithBmcListResponse;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.exception.IllegalBmcStateException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
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
import java.util.stream.Collectors;

/**
 * MA4 BMC 펌웨어 도메인 로직 총괄.
 * BMC 는 BIOS 와 같은 번들 디렉토리 자원으로 저장하고 IN_TREE 마커를 사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BmcService {

    private final BmcRepository bmcRepository;
    private final BoardModelRepository boardModelRepository;
    private final BundleExtractionService bundleExtractionService;
    private final BundleEntrypointDetector bundleEntrypointDetector;
    private final BundleManifestService bundleManifestService;
    private final ProvisionMarkerService provisionMarkerService;
    private final TargetDirectoryPolicyService targetDirectoryPolicyService;
    private final BundleTreeCleanupService bundleTreeCleanupService;

    public List<BoardWithBmcListResponse> findAllGrouped(boolean includeDeleted) {
        List<BoardModel> boards = includeDeleted
                ? boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()
                : boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();
        if (boards.isEmpty()) return List.of();

        List<Long> boardIds = boards.stream().map(BoardModel::getId).toList();
        List<BoardBMC> allBmc = bmcRepository.findAllByBoardModel_IdIn(boardIds);
        Map<Long, List<BoardBMC>> byBoard = allBmc.stream()
                .filter(b -> includeDeleted || !b.isDeleted())
                .collect(Collectors.groupingBy(b -> b.getBoardModel().getId(), HashMap::new, Collectors.toList()));

        return boards.stream()
                .map(board -> new BoardWithBmcListResponse(
                        board.getId(),
                        board.getVendor(),
                        board.getVendor().getDisplayName(),
                        board.getModelName(),
                        board.isDeleted(),
                        byBoard.getOrDefault(board.getId(), List.of()).stream()
                                .sorted(Comparator.comparing(BoardBMC::getVersion).reversed())
                                .map(BmcService::toResponse)
                                .toList()
                ))
                .toList();
    }

    public BmcResponse findBmc(Long boardId, Long bmcId) {
        return toResponse(requireLiveBmc(boardId, bmcId));
    }

    @Transactional
    public Long addBmc(Long boardId,
                       BmcCreateRequest request,
                       BmcUploadMode uploadMode,
                       MultipartFile[] folderFiles,
                       MultipartFile zipFile,
                       MultipartFile singleFile) {
        BoardModel parent = requireActiveBoard(boardId);

        if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
            throw new DuplicateBmcVersionException(boardId, request.version());
        }

        Path targetDir = Path.of(request.targetDirectory());

        bmcRepository.findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(boardId, request.version())
                .ifPresent(existing -> {
                    bundleTreeCleanupService.purgeExistingTree(Path.of(existing.getTreeRootPath()), "purgeExistingTree");
                    bmcRepository.delete(existing);
                });

        targetDirectoryPolicyService.prepareForUpload(targetDir, request.allowCreateDirectory());

        bmcRepository.findFirstByBoardModel_IdAndTreeRootPathAndIsDeletedFalse(boardId, targetDir.toString())
                .ifPresent(existing -> {
                    throw new TargetDirectoryNotEmptyException(existing.getTreeRootPath());
                });

        try {
            switch (uploadMode) {
                case FOLDER -> bundleExtractionService.extractFolder(folderFiles, targetDir);
                case ZIP -> bundleExtractionService.extractZip(zipFile, targetDir);
                case SINGLE_FILE -> bundleExtractionService.extractSingleFile(singleFile, targetDir);
            }

            String entrypoint = bundleEntrypointDetector.detect(targetDir, request.entrypointRelativePath());
            ManifestSummary manifest = bundleManifestService.compute(targetDir);

            BoardBMC saved = bmcRepository.save(BoardBMC.builder()
                    .boardModel(parent)
                    .name(request.name())
                    .version(request.version())
                    .treeRootPath(targetDir.toString())
                    .legacyFilePath(targetDir.toString())
                    .boardModelIdMirror(parent.getId())
                    .entrypointRelativePath(entrypoint)
                    .manifestHash(manifest.manifestHash())
                    .markerSignature(null)
                    .fileCount(manifest.fileCount())
                    .totalBytes(manifest.totalBytes())
                    .description(request.description())
                    .isEnabled(true)
                    .isDeleted(false)
                    .build());

            MarkerContent unsigned = new MarkerContent(
                    ResourceType.BMC_FIRMWARE.name(),
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
            saved.reissueMarker(manifest.manifestHash(), signature);
            provisionMarkerService.write(targetDir, MarkerLayout.IN_TREE, unsigned.withSignature(signature));

            log.info("[addBmc] 등록 완료. bmcId={}, boardId={}, version={}, fileCount={}, totalBytes={}",
                    saved.getId(), boardId, request.version(), manifest.fileCount(), manifest.totalBytes());
            return saved.getId();
        } catch (RuntimeException e) {
            bundleTreeCleanupService.cleanupFailedUpload(targetDir, "purgeExistingTree", "addBMC", e);
            throw e;
        }
    }

    @Transactional
    public void update(Long boardId, Long bmcId, BmcUpdateRequest request) {
        BoardBMC bmc = requireLiveBmc(boardId, bmcId);
        if (!bmc.getVersion().equals(request.version())
                && bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, request.version())) {
            throw new DuplicateBmcVersionException(boardId, request.version());
        }
        bmc.update(request.name(), request.version(), request.description());
    }

    @Transactional
    public void toggleEnabled(Long boardId, Long bmcId) {
        requireLiveBmc(boardId, bmcId).toggleEnabled();
    }

    @Transactional
    public void softDelete(Long boardId, Long bmcId) {
        requireLiveBmc(boardId, bmcId).softDelete();
    }

    @Transactional
    public void restore(Long boardId, Long bmcId) {
        requireActiveBoard(boardId);
        BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
                .orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
        if (!bmc.isDeleted()) {
            throw new IllegalBmcStateException("이미 활성 상태인 BMC 펌웨어입니다. bmcId=" + bmcId);
        }
        if (bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(boardId, bmc.getVersion())) {
            throw new DuplicateBmcVersionException(boardId, bmc.getVersion());
        }
        bmc.restore();
    }

    public IntegrityStatus verifyIntegrity(Long boardId, Long bmcId) {
        BoardBMC bmc = requireLiveBmc(boardId, bmcId);
        Path treeRoot = Path.of(bmc.getTreeRootPath());
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

    private BoardModel requireActiveBoard(Long boardId) {
        return boardModelRepository.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardId));
    }

    private BoardBMC requireLiveBmc(Long boardId, Long bmcId) {
        requireActiveBoard(boardId);
        BoardBMC bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardId)
                .orElseThrow(() -> new BmcNotFoundException(boardId, bmcId));
        if (bmc.isDeleted()) {
            throw new IllegalBmcStateException("삭제된 BMC 펌웨어에는 수행할 수 없는 작업입니다. bmcId=" + bmcId);
        }
        return bmc;
    }

    private static BmcResponse toResponse(BoardBMC entity) {
        return new BmcResponse(
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
