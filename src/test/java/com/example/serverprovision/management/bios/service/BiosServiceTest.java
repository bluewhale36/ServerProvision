package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.exception.IllegalBiosStateException;
import com.example.serverprovision.management.common.filesystem.exception.MarkerConflictException;
import com.example.serverprovision.management.common.filesystem.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
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
class BiosServiceTest {

    @Mock BiosRepository biosRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleEntrypointDetector bundleEntrypointDetector;
    @Mock BundleManifestService bundleManifestService;
    @Mock ProvisionMarkerService provisionMarkerService;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @InjectMocks BiosService biosService;

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
    @DisplayName("addBios(happy) : SINGLE_FILE 모드 - 전개 + manifest + 2-phase save + marker 기록 호출")
    void addBios_happy_singleFile(@TempDir Path tmp) {
        Path target = tmp.resolve("target");
        // board 활성
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        // 중복 없음
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        // MK2 — 자동 purge 인라인 제거로 findFirstByBoardModel_IdAndVersionAndIsDeletedTrue stub 불필요.
        // entrypoint 탐지 + manifest
        given(bundleEntrypointDetector.detect(any(), any())).willReturn("X99E-WS.CAP");
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("abc123", 1, 100L));
        given(provisionMarkerService.computeSignature(any())).willReturn("sig123");
        // save 후 id 부여
        given(biosRepository.save(any(BoardBIOS.class))).willAnswer(inv -> {
            BoardBIOS arg = inv.getArgument(0);
            return BoardBIOS.builder()
                    .id(77L).boardModel(arg.getBoardModel()).name(arg.getName()).version(arg.getVersion())
                    .treeRootPath(arg.getTreeRootPath()).entrypointRelativePath(arg.getEntrypointRelativePath())
                    .manifestHash(arg.getManifestHash()).markerSignature(arg.getMarkerSignature())
                    .fileCount(arg.getFileCount()).totalBytes(arg.getTotalBytes())
                    .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
        });

        Long id = biosService.addBios(10L,
                new BiosCreateRequest("Test", "1.0", target.toString(), null, true, ""),
                BiosUploadMode.SINGLE_FILE,
                null, null,
                new MockMultipartFile("singleFile", "X99E-WS.CAP", "application/octet-stream", "cap".getBytes()));

        assertThat(id).isEqualTo(77L);
        verify(bundleExtractionService).extractSingleFile(any(), any());
        verify(biosRepository).save(any(BoardBIOS.class));
        verify(provisionMarkerService).write(any(), any(), any());
    }

    @Test
    @DisplayName("addBios(fail) : 활성 (board, version) 중복 → DuplicateBiosVersionException")
    void addBios_duplicateActive_throws(@TempDir Path tmp) {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(true);

        assertThatThrownBy(() -> biosService.addBios(10L,
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
        // MK2 — 자동 purge 인라인 제거로 findFirstByBoardModel_IdAndVersionAndIsDeletedTrue stub 불필요.
        org.mockito.BDDMockito.willThrow(new MarkerConflictException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> biosService.addBios(10L,
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
        // MK2 — 자동 purge 인라인 제거로 findFirstByBoardModel_IdAndVersionAndIsDeletedTrue stub 불필요.
        org.mockito.BDDMockito.willThrow(new TargetDirectoryNotEmptyException(target.toString()))
                .given(targetDirectoryPolicyService).prepareForUpload(target, true);

        assertThatThrownBy(() -> biosService.addBios(10L,
                new BiosCreateRequest("x", "1.0", target.toString(), null, true, ""),
                BiosUploadMode.SINGLE_FILE, null, null,
                new MockMultipartFile("singleFile", "a.cap", null, "x".getBytes())))
                .isInstanceOf(TargetDirectoryNotEmptyException.class);
    }

    @Test
    @DisplayName("toggleEnabled(happy) : 활성 BIOS 는 토글")
    void toggleEnabled_happy() {
        BoardBIOS bios = buildActiveBios();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));

        biosService.toggleEnabled(10L, 1L);

        assertThat(bios.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toggleEnabled(fail) : 삭제된 BIOS 에는 IllegalBiosStateException")
    void toggleEnabled_deleted_throws() {
        BoardBIOS bios = buildActiveBios();
        bios.softDelete();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));

        assertThatThrownBy(() -> biosService.toggleEnabled(10L, 1L))
                .isInstanceOf(IllegalBiosStateException.class);
    }

    @Test
    @DisplayName("findBios(fail) : 없는 BIOS → BiosNotFoundException")
    void findBios_notFound_throws() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(99L, 10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> biosService.findBios(10L, 99L))
                .isInstanceOf(BiosNotFoundException.class);
    }

    @Test
    @DisplayName("findAllGrouped : Miller 데이터 + integrityStatus 는 NOT_VERIFIED")
    void findAllGrouped_integrityNotVerifiedByDefault() {
        BoardModel b = activeBoard();
        BoardBIOS bios = buildActiveBios();
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(b));
        given(biosRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(bios));

        var groups = biosService.findAllGrouped(false);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).biosList()).hasSize(1);
        assertThat(groups.get(0).biosList().get(0).integrityStatus()).isEqualTo(IntegrityStatus.NOT_VERIFIED);
    }

    @Test
    @DisplayName("findAllGrouped : 저장된 마지막 무결성 상태가 있으면 응답에 그대로 반영한다")
    void findAllGrouped_usesStoredIntegrityStatus() {
        BoardModel b = activeBoard();
        BoardBIOS bios = buildActiveBios();
        bios.recordIntegritySnapshot(IntegrityStatus.TAMPERED, java.time.Instant.now());
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(b));
        given(biosRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(bios));

        var groups = biosService.findAllGrouped(false);

        assertThat(groups.get(0).biosList().get(0).integrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
    }

    @Test
    @DisplayName("verifyAndRecordIntegrity : 계산 결과를 엔티티 스냅샷에 기록한다")
    void verifyAndRecordIntegrity_recordsSnapshot(@TempDir Path tmp) throws Exception {
        Path tree = tmp.resolve("bios");
        Files.createDirectories(tree);
        Files.writeString(tree.resolve("flash.nsh"), "echo");

        BoardBIOS bios = BoardBIOS.builder()
                .id(1L).boardModel(activeBoard())
                .name("x").version("1.0")
                .treeRootPath(tree.toString()).entrypointRelativePath("flash.nsh")
                .manifestHash("old").markerSignature("sig")
                .fileCount(1).totalBytes(4L)
                .isEnabled(true).isDeleted(false).build();
        var marker = new com.example.serverprovision.global.marker.MarkerContent(
                com.example.serverprovision.global.marker.ResourceType.BIOS_BUNDLE.name(),
                1L, java.util.Map.of(), Instant.now(), "old", "sig");

        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));
        given(provisionMarkerService.read(tree, com.example.serverprovision.global.marker.MarkerLayout.IN_TREE))
                .willReturn(marker);
        given(provisionMarkerService.verifySignature(marker)).willReturn(true);
        given(bundleManifestService.compute(tree)).willReturn(new ManifestSummary("new-hash", 1, 4L));
        given(provisionMarkerService.verifyManifestHash(marker, "new-hash")).willReturn(false);

        IntegrityStatus status = biosService.verifyAndRecordIntegrity(10L, 1L);

        assertThat(status).isEqualTo(IntegrityStatus.TAMPERED);
        assertThat(bios.getLastIntegrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
        assertThat(bios.getLastVerifiedAt()).isNotNull();
    }

    private BoardBIOS buildActiveBios() {
        return BoardBIOS.builder()
                .id(1L).boardModel(activeBoard())
                .name("x").version("1.0")
                .treeRootPath("/tmp/x").entrypointRelativePath("f.nsh")
                .manifestHash("h").markerSignature("s")
                .fileCount(2).totalBytes(100L)
                .isEnabled(true).isDeleted(false).build();
    }
}
