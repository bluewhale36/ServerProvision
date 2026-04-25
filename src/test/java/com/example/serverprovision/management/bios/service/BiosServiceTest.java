package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.dto.request.BiosCreateRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.enums.BiosUploadMode;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.exception.IllegalBiosStateException;
import com.example.serverprovision.management.bios.exception.MarkerConflictException;
import com.example.serverprovision.management.bios.exception.TargetDirectoryNotEmptyException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.bios.vo.IntegrityStatus;
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
    @InjectMocks BiosService biosService;

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
        given(biosRepository.findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(10L, "1.0"))
                .willReturn(Optional.empty());
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
    void addBios_markerConflict_throws(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("t");
        Files.createDirectories(target);
        Files.writeString(target.resolve(".provision.json"), "{}"); // 기존 marker 점유
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        given(biosRepository.findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(10L, "1.0"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> biosService.addBios(10L,
                new BiosCreateRequest("x", "1.0", target.toString(), null, true, ""),
                BiosUploadMode.SINGLE_FILE, null, null,
                new MockMultipartFile("singleFile", "a.cap", null, "x".getBytes())))
                .isInstanceOf(MarkerConflictException.class);
    }

    @Test
    @DisplayName("addBios(fail) : targetDirectory 비어있지 않고 marker 없음 → TargetDirectoryNotEmpty")
    void addBios_targetNotEmpty_throws(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("t");
        Files.createDirectories(target);
        Files.writeString(target.resolve("random.txt"), "외부 파일");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "1.0")).willReturn(false);
        given(biosRepository.findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(10L, "1.0"))
                .willReturn(Optional.empty());

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
