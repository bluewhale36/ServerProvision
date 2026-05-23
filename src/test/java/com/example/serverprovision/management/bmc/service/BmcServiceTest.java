package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.bios.service.BundleEntrypointDetector;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.bmc.dto.response.BoardWithBmcListResponse;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BmcServiceTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleEntrypointDetector bundleEntrypointDetector;
    @Mock BundleManifestService bundleManifestService;
    @Mock ProvisionMarkerService provisionMarkerService;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @InjectMocks BmcService bmcService;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        org.mockito.Mockito.lenient().when(pathPolicyService.assertWritablePath(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> java.nio.file.Path.of(inv.getArgument(0, String.class)).toAbsolutePath().normalize());
    }

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    @Test
    @DisplayName("addBmc(happy) : SINGLE_FILE 모드 - 전개 + manifest + 2-phase save + marker 기록 호출")
    void addBmc_happy_singleFile(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(false);
        given(bundleEntrypointDetector.detect(any(), any(), any())).willReturn("flash.nsh");
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 3, 2048L));
        given(provisionMarkerService.computeSignature(any())).willReturn("sig123");
        given(bmcRepository.save(any(BoardBMC.class))).willAnswer(inv -> {
            BoardBMC arg = inv.getArgument(0);
            return BoardBMC.builder()
                    .id(77L).boardModel(arg.getBoardModel()).name(arg.getName()).version(arg.getVersion())
                    .treeRootPath(arg.getTreeRootPath()).legacyFilePath(arg.getLegacyFilePath())
                    .boardModelIdMirror(arg.getBoardModelIdMirror())
                    .entrypointRelativePath(arg.getEntrypointRelativePath())
                    .manifestHash(arg.getManifestHash()).markerSignature(arg.getMarkerSignature())
                    .fileCount(arg.getFileCount()).totalBytes(arg.getTotalBytes())
                    .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
        });

        Long id = bmcService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target.toString(), "", true, ""),
                BmcUploadMode.SINGLE_FILE,
                null, null,
                new MockMultipartFile("singleFile", "firmware.bin", "application/octet-stream", "bin".getBytes()));

        assertThat(id).isEqualTo(77L);
        verify(bundleExtractionService).extractSingleFile(any(), any());
        verify(bmcRepository).save(any(BoardBMC.class));
        verify(provisionMarkerService).write(any(), any(), any());
    }

    @Test
    @DisplayName("addBmc(fail) : 활성 (board, version) 중복 → DuplicateBmcVersionException")
    void addBmc_duplicateActive_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(true);

        assertThatThrownBy(() -> bmcService.addBmc(10L,
                new BmcCreateRequest("x", "13.06.25", tmp.resolve("t").toString(), null, true, ""),
                BmcUploadMode.FOLDER, new org.springframework.web.multipart.MultipartFile[0], null, null))
                .isInstanceOf(DuplicateBmcVersionException.class);
        verify(bmcRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBmc(fail) : targetDirectory 에 다른 marker 존재 → MarkerConflictException")
    void addBmc_markerConflict_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new MarkerConflictException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> bmcService.addBmc(10L,
                new BmcCreateRequest("x", "1.0", target.toString(), null, true, ""),
                BmcUploadMode.SINGLE_FILE, null, null,
                new MockMultipartFile("singleFile", "a.bin", null, "x".getBytes())))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("addBmc(fail) : targetDirectory 비어있지 않고 marker 없음 → TargetDirectoryNotEmpty")
    void addBmc_targetNotEmpty_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new TargetDirectoryNotEmptyException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> bmcService.addBmc(10L,
                new BmcCreateRequest("x", "1.0", target.toString(), null, true, ""),
                BmcUploadMode.SINGLE_FILE, null, null,
                new MockMultipartFile("singleFile", "a.bin", null, "x".getBytes())))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }

    @Test
    @DisplayName("addBmc(fail) : 전개 후 저장 실패면 대상 디렉토리를 정리한다")
    void addBmc_cleanupTargetDirWhenSaveFails(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(false);
        given(bundleEntrypointDetector.detect(any(), any(), any())).willReturn("flash.nsh");
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 10L));
        given(bmcRepository.save(any(BoardBMC.class))).willThrow(new IllegalStateException("db fail"));
        org.mockito.Mockito.doAnswer(inv -> {
            Path dir = inv.getArgument(1);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("flash.nsh"), "echo");
            return null;
        }).when(bundleExtractionService).extractSingleFile(any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            Files.walk(target)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (java.io.IOException ignored) { }
                    });
            return null;
        }).when(bundleTreeCleanupService)
                .cleanupFailedUpload(org.mockito.ArgumentMatchers.eq(target), any(), any(), any());

        assertThatThrownBy(() -> bmcService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target.toString(), "", true, ""),
                BmcUploadMode.SINGLE_FILE,
                null, null,
                new MockMultipartFile("singleFile", "firmware.bin", "application/octet-stream", "bin".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db fail");

        assertThat(Files.exists(target)).isFalse();
    }

    @Test
    @DisplayName("addBmc(happy) : 대상 디렉토리에 .DS_Store 만 있으면 빈 디렉토리로 간주한다")
    void addBmc_ignoresDsStoreInTargetDirectory(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("target");

        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(false);
        given(bundleEntrypointDetector.detect(any(), any(), any())).willReturn("flash.nsh");
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 10L));
        given(provisionMarkerService.computeSignature(any())).willReturn("sig123");
        given(bmcRepository.save(any(BoardBMC.class))).willAnswer(inv -> {
            BoardBMC arg = inv.getArgument(0);
            return BoardBMC.builder()
                    .id(88L).boardModel(arg.getBoardModel()).name(arg.getName()).version(arg.getVersion())
                    .treeRootPath(arg.getTreeRootPath()).legacyFilePath(arg.getLegacyFilePath())
                    .boardModelIdMirror(arg.getBoardModelIdMirror())
                    .entrypointRelativePath(arg.getEntrypointRelativePath())
                    .manifestHash(arg.getManifestHash()).markerSignature(arg.getMarkerSignature())
                    .fileCount(arg.getFileCount()).totalBytes(arg.getTotalBytes())
                    .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
        });
        org.mockito.Mockito.doAnswer(inv -> {
            Files.createDirectories(target);
            Files.writeString(target.resolve("flash.nsh"), "echo");
            return null;
        }).when(bundleExtractionService).extractSingleFile(any(), any());

        Long id = bmcService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target.toString(), "", true, ""),
                BmcUploadMode.SINGLE_FILE,
                null, null,
                new MockMultipartFile("singleFile", "firmware.bin", "application/octet-stream", "bin".getBytes()));

        assertThat(id).isEqualTo(88L);
    }

    @Test
    @DisplayName("findAllGrouped : 저장된 마지막 무결성 상태를 응답에 반영한다")
    void findAllGrouped_usesStoredIntegrityStatus() {
        BoardBMC bmc = BoardBMC.builder()
                .id(7L).boardModel(activeBoard()).name("AST2600").version("13.06.25")
                .treeRootPath("/opt/bmc").legacyFilePath("/opt/bmc").boardModelIdMirror(10L)
                .entrypointRelativePath("flash.nsh").manifestHash("hash").markerSignature("sig")
                .fileCount(3).totalBytes(2048L).description("")
                .isEnabled(true).isDeleted(false)
                .build();
        bmc.recordIntegritySnapshot(IntegrityStatus.ORIGINAL, java.time.Instant.now());

        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(activeBoard()));
        given(bmcRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(bmc));

        List<BoardWithBmcListResponse> groups = bmcService.findAllGrouped(false);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).bmcList().get(0).integrityStatus()).isEqualTo(IntegrityStatus.ORIGINAL);
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity : 계산 결과를 엔티티 스냅샷에 기록한다")
    void verifyAndRecordIntegrity_recordsSnapshot(@TempDir Path tmp) throws Exception {
        Path tree = tmp.resolve("bmc");
        Files.createDirectories(tree);
        Files.writeString(tree.resolve("flash.nsh"), "echo");

        BoardBMC bmc = BoardBMC.builder()
                .id(1L).boardModel(activeBoard()).name("AST2600").version("13.06.25")
                .treeRootPath(tree.toString()).legacyFilePath(tree.toString()).boardModelIdMirror(10L)
                .entrypointRelativePath("flash.nsh").manifestHash("hash").markerSignature("sig")
                .fileCount(1).totalBytes(4L).isEnabled(true).isDeleted(false).build();
        var marker = new com.example.serverprovision.global.marker.MarkerContent(
                com.example.serverprovision.global.marker.ResourceType.BMC_FIRMWARE.name(),
                1L, java.util.Map.of(), Instant.now(), "hash", "sig");

        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bmc));
        given(provisionMarkerService.read(tree, com.example.serverprovision.global.marker.MarkerLayout.IN_TREE))
                .willReturn(marker);
        given(provisionMarkerService.verifySignature(marker)).willReturn(false);

        IntegrityStatus status = bmcService.verifyAndRecordIntegrity(10L, 1L);

        assertThat(status).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
        assertThat(bmc.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.SIGNATURE_INVALID);
        assertThat(bmc.getLastVerifiedAt()).isNotNull();
    }
}
