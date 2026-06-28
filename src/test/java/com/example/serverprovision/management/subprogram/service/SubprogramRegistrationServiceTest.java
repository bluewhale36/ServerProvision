package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.global.security.PathPolicyService;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramCreateRequest;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.enums.SubprogramUploadMode;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.SubprogramPathConflictException;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R6-3 CP4 — {@link SubprogramRegistrationService} 단위 테스트.
 *
 * <p>fat {@code SubprogramService} 5분할 시 등록 흐름 (addSubprogram) 의 happy + 중복키 / 트리 점유 /
 * soft-deleted 차단 시나리오를 본 file 로 이동. marker 발급은 {@link SubprogramMarkerWriter} 위임(void mock)
 * 이라 ProvisionMarkerService 를 직접 mock 하지 않는다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SubprogramRegistrationServiceTest {

    @Mock SubprogramRepository subprogramRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleManifestService bundleManifestService;
    @Mock SubprogramMarkerWriter subprogramMarkerWriter;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;
    @Mock PathPolicyService pathPolicyService;
    @Mock NudgeRegistry nudgeRegistry;
    @InjectMocks SubprogramRegistrationService subprogramRegistrationService;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        // S3 — 테스트 의도와 무관한 가드는 통과시키는 default stub.
        Mockito.lenient().when(pathPolicyService.assertWritablePath(anyString()))
                .thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)).toAbsolutePath().normalize());
    }

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
    }

    private SubprogramCreateRequest req(String name, String version, Path target) {
        return new SubprogramCreateRequest(name, version, target.toString(), "desc", false);
    }

    @Test
    @DisplayName("addSubprogram(happy) : Driver 보드별 등록 → save + marker 발급")
    void addDriver_board_happy(@TempDir Path tmp) {
        Path target = tmp.resolve("driver-board");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(subprogramRepository.findActiveByBoardKey(SubprogramKind.DRIVER, 10L, "ixgbe", "5.20"))
                .willReturn(Optional.empty());
        given(subprogramRepository.findSoftDeletedByBoardKey(SubprogramKind.DRIVER, 10L, "ixgbe", "5.20"))
                .willReturn(Optional.empty());
        given(subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(any()))
                .willReturn(Optional.empty());
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("hash1", 5, 2048L));
        given(subprogramRepository.save(any(Subprogram.class))).willAnswer(inv -> {
            Subprogram s = inv.getArgument(0);
            return Subprogram.builder()
                    .id(99L).kind(s.getKind()).boardModel(s.getBoardModel())
                    .name(s.getName()).version(s.getVersion())
                    .treeRootPath(s.getTreeRootPath())
                    .manifestHash(s.getManifestHash())
                    .fileCount(s.getFileCount()).totalBytes(s.getTotalBytes())
                    .description(s.getDescription())
                    .isEnabled(true).isDeleted(false)
                    .build();
        });

        Long id = subprogramRegistrationService.addSubprogram(
                SubprogramKind.DRIVER, BoardScope.ofBoard(10L),
                req("ixgbe", "5.20", target),
                SubprogramUploadMode.FOLDER, new MultipartFile[]{}, null, null);

        assertThat(id).isEqualTo(99L);
        verify(bundleExtractionService).extractFolder(any(), any());
        verify(subprogramMarkerWriter).writeSignedMarker(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("addSubprogram(happy) : Utility 공용 등록 → boardModel 없이 save")
    void addUtility_common_happy(@TempDir Path tmp) {
        Path target = tmp.resolve("util-common");
        given(subprogramRepository.findActiveByCommonKey(SubprogramKind.UTILITY, "raid-cli", "1.0"))
                .willReturn(Optional.empty());
        given(subprogramRepository.findSoftDeletedByCommonKey(SubprogramKind.UTILITY, "raid-cli", "1.0"))
                .willReturn(Optional.empty());
        given(subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(any()))
                .willReturn(Optional.empty());
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("h2", 1, 100L));
        given(subprogramRepository.save(any(Subprogram.class))).willAnswer(inv -> {
            Subprogram s = inv.getArgument(0);
            return Subprogram.builder()
                    .id(101L).kind(s.getKind()).boardModel(null)
                    .name(s.getName()).version(s.getVersion())
                    .treeRootPath(s.getTreeRootPath())
                    .manifestHash(s.getManifestHash())
                    .fileCount(s.getFileCount()).totalBytes(s.getTotalBytes())
                    .isEnabled(true).isDeleted(false)
                    .build();
        });

        Long id = subprogramRegistrationService.addSubprogram(
                SubprogramKind.UTILITY, BoardScope.COMMON,
                req("raid-cli", "1.0", target),
                SubprogramUploadMode.SINGLE_FILE, null, null, null);

        assertThat(id).isEqualTo(101L);
        verify(bundleExtractionService).extractSingleFile(any(), any());
    }

    @Test
    @DisplayName("addSubprogram : 활성 (kind, board, name, version) 중복 → DuplicateSubprogramVersionException")
    void duplicate_board(@TempDir Path tmp) {
        Path target = tmp.resolve("dup");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        Subprogram existing = Subprogram.builder()
                .id(1L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("ixgbe").version("5.20").treeRootPath("/whatever")
                .manifestHash("x").fileCount(1).totalBytes(1L).build();
        given(subprogramRepository.findActiveByBoardKey(SubprogramKind.DRIVER, 10L, "ixgbe", "5.20"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> subprogramRegistrationService.addSubprogram(
                SubprogramKind.DRIVER, BoardScope.ofBoard(10L),
                req("ixgbe", "5.20", target),
                SubprogramUploadMode.SINGLE_FILE, null, null, null))
                .isInstanceOf(DuplicateSubprogramVersionException.class);
    }

    @Test
    @DisplayName("addSubprogram : 공용 동일 (name, version) 활성 중복 → DuplicateSubprogramVersionException")
    void duplicate_common(@TempDir Path tmp) {
        Path target = tmp.resolve("dup-c");
        Subprogram existing = Subprogram.builder()
                .id(2L).kind(SubprogramKind.UTILITY).boardModel(null)
                .name("raid").version("1.0").treeRootPath("/x")
                .manifestHash("y").fileCount(1).totalBytes(1L).build();
        given(subprogramRepository.findActiveByCommonKey(SubprogramKind.UTILITY, "raid", "1.0"))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> subprogramRegistrationService.addSubprogram(
                SubprogramKind.UTILITY, BoardScope.COMMON,
                req("raid", "1.0", target),
                SubprogramUploadMode.SINGLE_FILE, null, null, null))
                .isInstanceOf(DuplicateSubprogramVersionException.class);
    }

    @Test
    @DisplayName("addSubprogram : soft-deleted 동일 키 존재 → DuplicateSubprogramVersionException (MK2 — 자동 purge 폐기)")
    void softDeleted_blocked_by_dupcheck(@TempDir Path tmp) {
        // MK2 결정 #5 — 자동 purge 인라인 로직 폐기. soft-deleted 동일 키는 사용자 명시 액션
        // (UI nudge replace 또는 purge endpoint) 으로만 정리 가능. addSubprogram 은 차단.
        Path target = tmp.resolve("revive");
        BoardModel board = activeBoard();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(board));
        given(subprogramRepository.findActiveByBoardKey(SubprogramKind.DRIVER, 10L, "n", "v"))
                .willReturn(Optional.empty());
        Subprogram stale = Subprogram.builder()
                .id(50L).kind(SubprogramKind.DRIVER).boardModel(board)
                .name("n").version("v").treeRootPath("/old")
                .manifestHash("h").fileCount(1).totalBytes(1L)
                .isDeleted(true).build();
        given(subprogramRepository.findSoftDeletedByBoardKey(SubprogramKind.DRIVER, 10L, "n", "v"))
                .willReturn(Optional.of(stale));

        assertThatThrownBy(() -> subprogramRegistrationService.addSubprogram(
                SubprogramKind.DRIVER, BoardScope.ofBoard(10L),
                req("n", "v", target),
                SubprogramUploadMode.SINGLE_FILE, null, null, null))
                .isInstanceOf(DuplicateSubprogramVersionException.class);

        // 자동 purge 가 호출되지 않음을 검증.
        verify(bundleTreeCleanupService, never()).purgeExistingTree(any(), anyString());
        verify(subprogramRepository, never()).delete(stale);
    }

    @Test
    @DisplayName("addSubprogram : 트리 루트 활성 점유 → SubprogramPathConflictException")
    void path_conflict(@TempDir Path tmp) {
        Path target = tmp.resolve("conflict");
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(subprogramRepository.findActiveByBoardKey(any(), any(), any(), any()))
                .willReturn(Optional.empty());
        given(subprogramRepository.findSoftDeletedByBoardKey(any(), any(), any(), any()))
                .willReturn(Optional.empty());
        Subprogram occupant = Subprogram.builder()
                .id(7L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("other").version("0.1").treeRootPath(target.toString())
                .manifestHash("h").fileCount(1).totalBytes(1L).build();
        given(subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(any()))
                .willReturn(Optional.of(occupant));

        assertThatThrownBy(() -> subprogramRegistrationService.addSubprogram(
                SubprogramKind.DRIVER, BoardScope.ofBoard(10L),
                req("new", "1.0", target),
                SubprogramUploadMode.SINGLE_FILE, null, null, null))
                .isInstanceOf(SubprogramPathConflictException.class);
    }
}
