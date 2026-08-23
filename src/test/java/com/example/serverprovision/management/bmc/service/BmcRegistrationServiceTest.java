package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bmc.dto.request.BmcCreateRequest;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.exception.DuplicateBmcVersionException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.firmware.exception.InvalidFirmwareFileException;
import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R5-3 CP4 — BmcRegistrationService 단위 테스트.
 *
 * <p>R12-2 — 번들(폴더 · zip · 단일 파일 모드) 시나리오를 폐지하고 단일 펌웨어 파일 등록(업로드 · claim)으로
 * 개정했다. happy(경로 해석 + 저장 + manifest + 2-phase save + marker 위임) + 실패(중복 / markerConflict /
 * targetNotEmpty / cleanup-on-failure) 커버리지를 보존하고, claim 경로와 nudge 취소 보존을 새로 덮는다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BmcRegistrationServiceTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleManifestService bundleManifestService;
    @Mock BmcFirmwareFilePolicy bmcFirmwareFilePolicy;
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
        // 정책 dispatcher 는 별도 테스트가 덮는다 — 여기서는 GIGABYTE 허용 확장자만 제공한다.
        org.mockito.Mockito.lenient().when(bmcFirmwareFilePolicy.allowedExtensions(any()))
                .thenReturn(List.of("ima_enc"));
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
    @DisplayName("addBmc(happy) : 업로드 — 경로 해석 + 저장 + manifest + 2-phase save + marker 위임")
    void addBmc_happy_upload(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(false);
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 2048L));
        given(bmcRepository.findHashConflictCandidates(10L, "abc123")).willReturn(List.of());
        given(bmcRepository.save(any(BoardBMC.class))).willAnswer(inv -> echoSaved(inv.getArgument(0), 77L));

        Long id = bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target + "/", "", true),
                new MockMultipartFile("firmwareFile", "bmc.ima_enc", "application/octet-stream", "bin".getBytes()));

        assertThat(id).isEqualTo(77L);
        verify(bundleExtractionService).storeSingleFileAs(any(), eq(target), eq("bmc.ima_enc"));
        verify(bmcRepository).save(any(BoardBMC.class));
        verify(bmcMarkerWriter).writeSignedMarker(any(), eq(target), eq(10L), eq("13.06.25"), eq("bmc.ima_enc"), eq("abc123"));
    }

    @Test
    @DisplayName("addBmc(happy) : 버전 번호 디렉토리(…/13.06.25)도 허용 확장자 기준으로 디렉토리로 추론된다 (R12-2 D8)")
    void addBmc_versionNumberDirectory_inferred(@TempDir Path tmp) {
        Path target = tmp.resolve("13.06.25");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(false);
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 2048L));
        given(bmcRepository.findHashConflictCandidates(10L, "abc123")).willReturn(List.of());
        given(bmcRepository.save(any(BoardBMC.class))).willAnswer(inv -> echoSaved(inv.getArgument(0), 78L));

        Long id = bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target.toString(), "", true),
                new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "bin".getBytes()));

        assertThat(id).isEqualTo(78L);
        verify(bundleExtractionService).storeSingleFileAs(any(), eq(target), eq("bmc.ima_enc"));
    }

    @Test
    @DisplayName("addBmc(fail) : 활성 (board, version) 중복 → DuplicateBmcVersionException")
    void addBmc_duplicateActive_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(true);

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("x", "13.06.25", tmp.resolve("t") + "/", null, true),
                new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "x".getBytes())))
                .isInstanceOf(DuplicateBmcVersionException.class);
        verify(bmcRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBmc(fail) : 대상 디렉토리에 다른 marker 존재 → MarkerConflictException")
    void addBmc_markerConflict_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new MarkerConflictException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(any(), org.mockito.ArgumentMatchers.anyBoolean());

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("x", "1.0", target + "/", null, true),
                new MockMultipartFile("firmwareFile", "a.ima_enc", null, "x".getBytes())))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("addBmc(fail) : 대상 디렉토리 비어있지 않고 marker 없음 → TargetDirectoryNotEmpty")
    void addBmc_targetNotEmpty_throws(@TempDir Path tmp) {
        Path target = tmp.resolve("t");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        org.mockito.BDDMockito.willThrow(new TargetDirectoryNotEmptyException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(any(), org.mockito.ArgumentMatchers.anyBoolean());

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("x", "1.0", target + "/", null, true),
                new MockMultipartFile("firmwareFile", "a.ima_enc", null, "x".getBytes())))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }

    @Test
    @DisplayName("addBmc(fail) : 저장 후 DB 실패면 대상 디렉토리를 정리한다")
    void addBmc_cleanupTargetDirWhenSaveFails(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "13.06.25")).willReturn(false);
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 10L));
        given(bmcRepository.findHashConflictCandidates(10L, "abc123")).willReturn(List.of());
        given(bmcRepository.save(any(BoardBMC.class))).willThrow(new IllegalStateException("db fail"));

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("AST2600", "13.06.25", target + "/", "", true),
                new MockMultipartFile("firmwareFile", "bmc.ima_enc", null, "bin".getBytes())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db fail");

        verify(bundleTreeCleanupService).cleanupFailedUpload(eq(target), any(), any(), any());
    }

    // ==== R12-2 — claim (업로드 없는 기존 파일 등록) ====

    @Test
    @DisplayName("addBmc(happy) : claim — 파일 실재 + 배타 통과, 저장 없이 manifest + save + marker")
    void addBmc_happy_claim(@TempDir Path tmp) throws Exception {
        Path treeRoot = tmp.resolve("t");
        Files.createDirectories(treeRoot);
        Path firmware = treeRoot.resolve("bmc.ima_enc");
        Files.writeString(firmware, "enc");

        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 3L));
        given(bmcRepository.findHashConflictCandidates(10L, "abc123")).willReturn(List.of());
        given(bmcRepository.save(any(BoardBMC.class))).willAnswer(inv -> echoSaved(inv.getArgument(0), 88L));

        Long id = bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("Claim", "1.0", firmware.toString(), null, false), null);

        assertThat(id).isEqualTo(88L);
        verify(bundleExtractionService, never()).storeSingleFileAs(any(), any(), any());
        verify(bmcMarkerWriter).writeSignedMarker(any(), eq(treeRoot), eq(10L), eq("1.0"), eq("bmc.ima_enc"), eq("abc123"));
    }

    @Test
    @DisplayName("addBmc(fail) : claim — 경로에 파일 부재 → InvalidFirmwareFileException(firmwarePath)")
    void addBmc_claim_fileMissing_throws(@TempDir Path tmp) throws Exception {
        Path treeRoot = tmp.resolve("t");
        Files.createDirectories(treeRoot);
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("x", "1.0", treeRoot.resolve("missing.ima_enc").toString(), null, false), null))
                .isInstanceOf(InvalidFirmwareFileException.class)
                .satisfies(e -> assertThat(((InvalidFirmwareFileException) e).fieldName()).isEqualTo("firmwarePath"));
        verify(bmcRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBmc(fail) : claim — 부모 디렉토리에 다른 파일 존재(비배타) → InvalidFirmwareFileException")
    void addBmc_claim_dirNotExclusive_throws(@TempDir Path tmp) throws Exception {
        Path treeRoot = tmp.resolve("t");
        Files.createDirectories(treeRoot);
        Path firmware = treeRoot.resolve("bmc.ima_enc");
        Files.writeString(firmware, "enc");
        Files.writeString(treeRoot.resolve("other.bin"), "x");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("x", "1.0", firmware.toString(), null, false), null))
                .isInstanceOf(InvalidFirmwareFileException.class)
                .hasMessageContaining("다른 파일");
    }

    @Test
    @DisplayName("addBmc(fail) : 경로가 / 로 끝나는데 업로드 파일 없음 → InvalidFirmwareFileException")
    void addBmc_directoryPathWithoutFile_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(bmcRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);

        assertThatThrownBy(() -> bmcRegistrationService.addBmc(10L,
                new BmcCreateRequest("x", "1.0", tmp.resolve("t") + "/", null, false), null))
                .isInstanceOf(InvalidFirmwareFileException.class)
                .hasMessageContaining("업로드할 파일");
    }

    @Test
    @DisplayName("cleanupNudgeCancelled : 업로드 임시 트리는 삭제, claim(사용자 기존 파일)은 보존")
    void cleanupNudgeCancelled_preservesClaimTree(@TempDir Path tmp) {
        var uploadPayload = new ContentNudgePayload(
                "n", "1.0", "abc", tmp.resolve("up").toString(), Map.of("claimExisting", "false"));
        bmcRegistrationService.cleanupNudgeCancelled(uploadPayload);
        verify(bundleTreeCleanupService).purgeExistingTree(tmp.resolve("up"), "nudgeCancel.bmc");

        var claimPayload = new ContentNudgePayload(
                "n", "1.0", "abc", tmp.resolve("keep").toString(), Map.of("claimExisting", "true"));
        bmcRegistrationService.cleanupNudgeCancelled(claimPayload);
        verify(bundleTreeCleanupService, never()).purgeExistingTree(eq(tmp.resolve("keep")), any());
    }
}
