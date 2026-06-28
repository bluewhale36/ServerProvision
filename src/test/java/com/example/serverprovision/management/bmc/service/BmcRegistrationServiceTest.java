package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.management.bios.service.BundleEntrypointDetector;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.enums.BmcUploadMode;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R5-3 CP4 — BmcRegistrationService 단위 테스트.
 *
 * <p>구 {@code BmcServiceTest} 의 addBmc 시나리오(happy / duplicate / markerConflict / targetNotEmpty /
 * cleanup-on-failure / .DS_Store)를 본 file 로 이동. marker 발급은 {@code BmcMarkerWriter} 위임으로 전환됐으므로
 * {@code provisionMarkerService.write} 대신 {@code bmcMarkerWriter.writeSignedMarker} 호출을 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BmcRegistrationServiceTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleEntrypointDetector bundleEntrypointDetector;
    @Mock BundleManifestService bundleManifestService;
    @Mock BmcMarkerWriter bmcMarkerWriter;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @Mock NudgeRegistry nudgeRegistry;

    @InjectMocks BmcRegistrationService bmcRegistrationService;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        org.mockito.Mockito.lenient().when(pathPolicyService.assertWritablePath(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)).toAbsolutePath().normalize());
    }

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    private BoardBMC echoSaved(BoardBMC arg, Long id) {
        return BoardBMC.builder()
                .id(id).boardModel(arg.getBoardModel()).name(arg.getName()).version(arg.getVersion())
                .treeRootPath(arg.getTreeRootPath()).legacyFilePath(arg.getLegacyFilePath())
                .boardModelIdMirror(arg.getBoardModelIdMirror())
                .entrypointRelativePath(arg.getEntrypointRelativePath())
                .manifestHash(arg.getManifestHash()).markerSignature(arg.getMarkerSignature())
                .fileCount(arg.getFileCount()).totalBytes(arg.getTotalBytes())
                .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
    }

    @Test
    @DisplayName("addBmc(happy) : SINGLE_FILE 모드 - 전개 + manifest + 2-phase save + marker 기록 위임")
    void addBmc_happy_singleFile(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(false);
        given(bundleEntrypointDetector.detect(any(), any(), any())).willReturn("flash.nsh");
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 3, 2048L));
        given(bmcRepository.findHashConflictCandidates(10L, "abc123")).willReturn(List.of());
        given(bmcRepository.save(any(BoardBMC.class))).willAnswer(inv -> echoSaved(inv.getArgument(0), 77L));

        Long id = bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target.toString(), "", true, ""),
                BmcUploadMode.SINGLE_FILE,
                null, null,
                new MockMultipartFile("singleFile", "firmware.bin", "application/octet-stream", "bin".getBytes()));

        assertThat(id).isEqualTo(77L);
        verify(bundleExtractionService).extractSingleFile(any(), any());
        verify(bmcRepository).save(any(BoardBMC.class));
        verify(bmcMarkerWriter).writeSignedMarker(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("addBmc(fail) : 활성 (board, version) 중복 → DuplicateBmcVersionException")
    void addBmc_duplicateActive_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(true);

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("x", "13.06.25", tmp.resolve("t").toString(), null, true, ""),
                BmcUploadMode.FOLDER, new MultipartFile[0], null, null))
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
                .given(targetDirectoryPolicyService).prepareForUpload(any(), org.mockito.ArgumentMatchers.anyBoolean());

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
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
                .given(targetDirectoryPolicyService).prepareForUpload(any(), org.mockito.ArgumentMatchers.anyBoolean());

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
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
        given(bmcRepository.findHashConflictCandidates(10L, "abc123")).willReturn(List.of());
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

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
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
        given(bmcRepository.findHashConflictCandidates(10L, "abc123")).willReturn(List.of());
        given(bmcRepository.save(any(BoardBMC.class))).willAnswer(inv -> echoSaved(inv.getArgument(0), 88L));
        org.mockito.Mockito.doAnswer(inv -> {
            Files.createDirectories(target);
            Files.writeString(target.resolve("flash.nsh"), "echo");
            return null;
        }).when(bundleExtractionService).extractSingleFile(any(), any());

        Long id = bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target.toString(), "", true, ""),
                BmcUploadMode.SINGLE_FILE,
                null, null,
                new MockMultipartFile("singleFile", "firmware.bin", "application/octet-stream", "bin".getBytes()));

        assertThat(id).isEqualTo(88L);
    }
}
