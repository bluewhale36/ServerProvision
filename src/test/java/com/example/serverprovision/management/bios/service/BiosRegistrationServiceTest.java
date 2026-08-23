package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
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
 * <p>R12-1 — 번들(폴더 · zip) 시나리오를 폐지하고 단일 펌웨어 파일 등록(업로드 · claim)으로 개정.
 * happy(경로 해석 + 저장 + manifest + 2-phase save + marker 위임) + 실패(중복 / markerConflict /
 * targetNotEmpty) 커버리지를 보존한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosRegistrationServiceTest {

    @Mock BiosRepository biosRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleManifestService bundleManifestService;
    @Mock BiosFirmwareFilePolicy biosFirmwareFilePolicy;
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
    @DisplayName("addBios(happy) : 업로드 — 경로 해석 + 저장 + manifest + 2-phase save + marker 위임")
    void addBios_happy_upload(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
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

        // 디렉토리 경로(/ 로 끝남) + 업로드 파일 → 해석 결과 = target/image.RBU, treeRoot = target.
        Long id = biosRegistrationService.addBios(10L,
                new BiosCreateRequest("Test", "1.0", target + "/", null, true),
                new MockMultipartFile("firmwareFile", "image.RBU", "application/octet-stream", "rbu".getBytes()));

        assertThat(id).isEqualTo(77L);
        verify(bundleExtractionService).storeSingleFileAs(any(), eq(target), eq("image.RBU"));
        // E2-1-a — 신규 = 최신 기본: 전 행 +1 shift 뒤 새 행이 1위로 저장된다.
        verify(biosRepository).shiftAllVersionRanks(10L);
        org.mockito.ArgumentCaptor<BoardBIOS> savedCap = org.mockito.ArgumentCaptor.forClass(BoardBIOS.class);
        verify(biosRepository).save(savedCap.capture());
        assertThat(savedCap.getValue().getVersionRank()).isEqualTo(1);
        verify(biosMarkerWriter).writeSignedMarker(any(BoardBIOS.class), eq(target), eq(10L), eq("1.0"), eq("image.RBU"), eq("abc123"));
    }

    @Test
    @DisplayName("addBios(fail) : 활성 (board, version) 중복 → DuplicateBiosVersionException")
    void addBios_duplicateActive_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(true);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", tmp.resolve("t") + "/", null, true),
                new MockMultipartFile("firmwareFile", "image.RBU", null, "x".getBytes())))
                .isInstanceOf(DuplicateBiosVersionException.class);
        verify(biosRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBios(fail) : 대상 디렉토리에 다른 marker 존재 → MarkerConflictException")
    void addBios_markerConflict_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new MarkerConflictException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", target + "/", null, true),
                new MockMultipartFile("firmwareFile", "a.cap", null, "x".getBytes())))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("addBios(fail) : 대상 디렉토리 비어있지 않고 marker 없음 → TargetDirectoryNotEmpty")
    void addBios_targetNotEmpty_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new TargetDirectoryNotEmptyException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", target + "/", null, true),
                new MockMultipartFile("firmwareFile", "a.cap", null, "x".getBytes())))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }

    @Test
    @DisplayName("addBios(happy) : 끝 슬래시 없는 디렉토리 경로 + 업로드 → 디렉토리로 추론해 파일명을 붙인다")
    void addBios_directoryInferredWithoutTrailingSlash(@TempDir Path tmp) {
        Path target = tmp.resolve("v310");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "3.10")).willReturn(false);
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 100L));
        given(biosRepository.findHashConflictCandidates(eq(10L), eq("abc123"))).willReturn(List.of());
        given(biosRepository.save(any(BoardBIOS.class))).willAnswer(inv -> {
            BoardBIOS arg = inv.getArgument(0);
            return BoardBIOS.builder()
                    .id(99L).boardModel(arg.getBoardModel()).name(arg.getName()).version(arg.getVersion())
                    .treeRootPath(arg.getTreeRootPath()).entrypointRelativePath(arg.getEntrypointRelativePath())
                    .manifestHash(arg.getManifestHash()).markerSignature(arg.getMarkerSignature())
                    .fileCount(arg.getFileCount()).totalBytes(arg.getTotalBytes())
                    .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
        });

        // 끝 슬래시 없이 디렉토리를 의도한 입력 — 사용자가 실제로 부딪힌 함정.
        Long id = biosRegistrationService.addBios(10L,
                new BiosCreateRequest("v310", "3.10", target.toString(), null, true),
                new MockMultipartFile("firmwareFile", "image.RBU", null, "rbu".getBytes()));

        assertThat(id).isEqualTo(99L);
        verify(bundleExtractionService).storeSingleFileAs(any(), eq(target), eq("image.RBU"));
        verify(biosMarkerWriter).writeSignedMarker(any(BoardBIOS.class), eq(target), eq(10L), eq("3.10"), eq("image.RBU"), eq("abc123"));
    }

    // ==== R12-1 — claim (업로드 없는 기존 파일 등록) ====

    @Test
    @DisplayName("addBios(happy) : claim — 파일 실재 + 배타 통과, 저장 없이 manifest + save + marker")
    void addBios_happy_claim(@TempDir Path tmp) throws Exception {
        Path treeRoot = tmp.resolve("t");
        java.nio.file.Files.createDirectories(treeRoot);
        Path firmware = treeRoot.resolve("image.RBU");
        java.nio.file.Files.writeString(firmware, "rbu");

        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 3L));
        given(biosRepository.findHashConflictCandidates(eq(10L), eq("abc123"))).willReturn(List.of());
        given(biosRepository.save(any(BoardBIOS.class))).willAnswer(inv -> {
            BoardBIOS arg = inv.getArgument(0);
            return BoardBIOS.builder()
                    .id(88L).boardModel(arg.getBoardModel()).name(arg.getName()).version(arg.getVersion())
                    .treeRootPath(arg.getTreeRootPath()).entrypointRelativePath(arg.getEntrypointRelativePath())
                    .manifestHash(arg.getManifestHash()).markerSignature(arg.getMarkerSignature())
                    .fileCount(arg.getFileCount()).totalBytes(arg.getTotalBytes())
                    .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
        });

        Long id = biosRegistrationService.addBios(10L,
                new BiosCreateRequest("Claim", "1.0", firmware.toString(), null, false), null);

        assertThat(id).isEqualTo(88L);
        verify(bundleExtractionService, never()).storeSingleFileAs(any(), any(), any());
        verify(biosMarkerWriter).writeSignedMarker(any(BoardBIOS.class), eq(treeRoot), eq(10L), eq("1.0"), eq("image.RBU"), eq("abc123"));
    }

    @Test
    @DisplayName("addBios(fail) : claim — 경로에 파일 부재 → InvalidFirmwareFileException(firmwarePath)")
    void addBios_claim_fileMissing_throws(@TempDir Path tmp) throws Exception {
        Path treeRoot = tmp.resolve("t");
        java.nio.file.Files.createDirectories(treeRoot);
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", treeRoot.resolve("missing.RBU").toString(), null, false), null))
                .isInstanceOf(com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException.class)
                .satisfies(e -> assertThat(
                        ((com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException) e).fieldName())
                        .isEqualTo("firmwarePath"));
        verify(biosRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBios(fail) : claim — 부모 디렉토리에 다른 파일 존재(비배타) → InvalidFirmwareFileException")
    void addBios_claim_dirNotExclusive_throws(@TempDir Path tmp) throws Exception {
        Path treeRoot = tmp.resolve("t");
        java.nio.file.Files.createDirectories(treeRoot);
        Path firmware = treeRoot.resolve("image.RBU");
        java.nio.file.Files.writeString(firmware, "rbu");
        java.nio.file.Files.writeString(treeRoot.resolve("other.bin"), "x");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", firmware.toString(), null, false), null))
                .isInstanceOf(com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException.class)
                .hasMessageContaining("다른 파일");
    }

    @Test
    @DisplayName("addBios(fail) : claim — 디렉토리에 기존 마커 존재 → MarkerConflictException")
    void addBios_claim_markerConflict_throws(@TempDir Path tmp) throws Exception {
        Path treeRoot = tmp.resolve("t");
        java.nio.file.Files.createDirectories(treeRoot);
        Path firmware = treeRoot.resolve("image.RBU");
        java.nio.file.Files.writeString(firmware, "rbu");
        java.nio.file.Files.writeString(treeRoot.resolve(".provision.json"), "{}");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", firmware.toString(), null, false), null))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("addBios(fail) : 경로가 / 로 끝나는데 업로드 파일 없음 → InvalidFirmwareFileException")
    void addBios_directoryPathWithoutFile_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);

        assertThatThrownBy(() -> biosRegistrationService.addBios(10L,
                new BiosCreateRequest("x", "1.0", tmp.resolve("t") + "/", null, false), null))
                .isInstanceOf(com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException.class)
                .hasMessageContaining("업로드할 파일");
    }

    // ==== R12-1 — nudge cancel 정리의 claim 보존 ====

    @Test
    @DisplayName("cleanupNudgeCancelled : 업로드 임시 트리는 삭제, claim(사용자 기존 파일)은 보존")
    void cleanupNudgeCancelled_preservesClaimTree(@TempDir Path tmp) {
        var uploadPayload = new com.example.serverprovision.management.common.nudge.ContentNudgePayload(
                "n", "1.0", "abc", tmp.resolve("up").toString(),
                java.util.Map.of("claimExisting", "false"));
        biosRegistrationService.cleanupNudgeCancelled(uploadPayload);
        verify(bundleTreeCleanupService).purgeExistingTree(tmp.resolve("up"), "nudgeCancel");

        var claimPayload = new com.example.serverprovision.management.common.nudge.ContentNudgePayload(
                "n", "1.0", "abc", tmp.resolve("keep").toString(),
                java.util.Map.of("claimExisting", "true"));
        biosRegistrationService.cleanupNudgeCancelled(claimPayload);
        verify(bundleTreeCleanupService, never()).purgeExistingTree(eq(tmp.resolve("keep")), any());
    }
}
