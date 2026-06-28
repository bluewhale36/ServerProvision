package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R4-3 CP4 — BiosRegistrationService 단위 테스트.
 *
 * <p>옛 {@code BiosServiceTest} 의 등록(addBios) 시나리오를 본 file 로 이동. happy(SINGLE_FILE 전개 + manifest
 * + 2-phase save + marker 위임) + 실패(중복 / markerConflict / targetNotEmpty) 커버리지를 보존한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosRegistrationServiceTest {

    @Mock BiosRepository biosRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleEntrypointDetector bundleEntrypointDetector;
    @Mock BundleManifestService bundleManifestService;
    @Mock BiosMarkerWriter biosMarkerWriter;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @Mock NudgeRegistry nudgeRegistry;
    @InjectMocks BiosRegistrationService biosRegistrationService;

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

    @Test
    @DisplayName("addBios(happy) : SINGLE_FILE 모드 - 전개 + manifest + 2-phase save + marker 위임")
    void addBios_happy_singleFile(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        given(bundleEntrypointDetector.detect(any(), any(), any())).willReturn("X99E-WS.CAP");
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 100L));
        // 해시 충돌 후보 없음 → nudge 미발급.
        given(biosRepository.findHashConflictCandidates(eq(10L), eq("abc123"))).willReturn(List.of());
        given(biosRepository.save(any(BoardBIOS.class))).willAnswer(inv -> {
            BoardBIOS arg = inv.getArgument(0);
            return BoardBIOS.builder()
                    .id(77L).boardModel(arg.getBoardModel()).name(arg.getName()).version(arg.getVersion())
                    .treeRootPath(arg.getTreeRootPath()).entrypointRelativePath(arg.getEntrypointRelativePath())
                    .manifestHash(arg.getManifestHash()).markerSignature(arg.getMarkerSignature())
                    .fileCount(arg.getFileCount()).totalBytes(arg.getTotalBytes())
                    .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
        });

        Long id = biosRegistrationService.addBios(10L,
                new BiosCreateRequest("Test", "1.0", target.toString(), null, true, ""),
                BiosUploadMode.SINGLE_FILE,
                null, null,
                new MockMultipartFile("singleFile", "X99E-WS.CAP", "application/octet-stream", "cap".getBytes()));

        assertThat(id).isEqualTo(77L);
        verify(bundleExtractionService).extractSingleFile(any(), any());
        verify(biosRepository).save(any(BoardBIOS.class));
        verify(biosMarkerWriter).writeSignedMarker(any(BoardBIOS.class), eq(target), eq(10L), eq("1.0"), eq("X99E-WS.CAP"), eq("abc123"));
    }

    @Test
    @DisplayName("addBios(fail) : 활성 (board, version) 중복 → DuplicateBiosVersionException")
    void addBios_duplicateActive_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(true);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", tmp.resolve("t").toString(), null, true, ""),
                BiosUploadMode.FOLDER, new org.springframework.web.multipart.MultipartFile[0], null, null))
                .isInstanceOf(DuplicateBiosVersionException.class);
        verify(biosRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBios(fail) : targetDirectory 에 다른 marker 존재 → MarkerConflictException")
    void addBios_markerConflict_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new MarkerConflictException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", target.toString(), null, true, ""),
                BiosUploadMode.SINGLE_FILE, null, null,
                new MockMultipartFile("singleFile", "a.cap", null, "x".getBytes())))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("addBios(fail) : targetDirectory 비어있지 않고 marker 없음 → TargetDirectoryNotEmpty")
    void addBios_targetNotEmpty_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new TargetDirectoryNotEmptyException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", target.toString(), null, true, ""),
                BiosUploadMode.SINGLE_FILE, null, null,
                new MockMultipartFile("singleFile", "a.cap", null, "x".getBytes())))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }
}
