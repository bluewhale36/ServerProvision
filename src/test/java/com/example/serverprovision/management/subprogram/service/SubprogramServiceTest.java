package com.example.serverprovision.management.subprogram.service;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.bios.service.BundleExtractionService;
import com.example.serverprovision.management.bios.service.BundleManifestService;
import com.example.serverprovision.management.bios.service.BundleManifestService.ManifestSummary;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.filesystem.service.BundleTreeCleanupService;
import com.example.serverprovision.management.common.filesystem.service.TargetDirectoryPolicyService;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramCreateRequest;
import com.example.serverprovision.management.subprogram.dto.request.SubprogramUpdateRequest;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubprogramServiceTest {

    @Mock SubprogramRepository subprogramRepository;
    @Mock BoardModelRepository boardModelRepository;
    @Mock BundleExtractionService bundleExtractionService;
    @Mock BundleManifestService bundleManifestService;
    @Mock ProvisionMarkerService provisionMarkerService;
    @Mock TargetDirectoryPolicyService targetDirectoryPolicyService;
    @Mock BundleTreeCleanupService bundleTreeCleanupService;
    @Mock com.example.serverprovision.global.security.PathPolicyService pathPolicyService;
    @Mock com.example.serverprovision.global.security.EntrypointPolicyService entrypointPolicyService;
    @InjectMocks SubprogramService subprogramService;

    @org.junit.jupiter.api.BeforeEach
    void stubSecurity() {
        // S3 — 테스트 의도와 무관한 가드는 통과시키는 default stub.
        org.mockito.Mockito.lenient().when(pathPolicyService.assertWritablePath(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> java.nio.file.Path.of(inv.getArgument(0, String.class)).toAbsolutePath().normalize());
        org.mockito.Mockito.lenient().when(entrypointPolicyService.validateAndNormalize(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(1));
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
        given(subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(target.toString()))
                .willReturn(Optional.empty());
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("hash1", 5, 2048L));
        given(provisionMarkerService.computeSignature(any())).willReturn("sig1");
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

        Long id = subprogramService.addSubprogram(
                SubprogramKind.DRIVER, BoardScope.ofBoard(10L),
                req("ixgbe", "5.20", target),
                SubprogramUploadMode.FOLDER, new MultipartFile[]{}, null, null);

        assertThat(id).isEqualTo(99L);
        verify(bundleExtractionService).extractFolder(any(), any());
        verify(provisionMarkerService).write(any(), any(), any());
    }

    @Test
    @DisplayName("addSubprogram(happy) : Utility 공용 등록 → boardModel 없이 save + 마커 attributes scope=common")
    void addUtility_common_happy(@TempDir Path tmp) {
        Path target = tmp.resolve("util-common");
        given(subprogramRepository.findActiveByCommonKey(SubprogramKind.UTILITY, "raid-cli", "1.0"))
                .willReturn(Optional.empty());
        given(subprogramRepository.findSoftDeletedByCommonKey(SubprogramKind.UTILITY, "raid-cli", "1.0"))
                .willReturn(Optional.empty());
        given(subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(target.toString()))
                .willReturn(Optional.empty());
        given(bundleManifestService.compute(any())).willReturn(new ManifestSummary("h2", 1, 100L));
        given(provisionMarkerService.computeSignature(any())).willReturn("sig2");
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

        Long id = subprogramService.addSubprogram(
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

        assertThatThrownBy(() -> subprogramService.addSubprogram(
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

        assertThatThrownBy(() -> subprogramService.addSubprogram(
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

        assertThatThrownBy(() -> subprogramService.addSubprogram(
                SubprogramKind.DRIVER, BoardScope.ofBoard(10L),
                req("n", "v", target),
                SubprogramUploadMode.SINGLE_FILE, null, null, null))
                .isInstanceOf(com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException.class);

        // 자동 purge 가 호출되지 않음을 검증.
        verify(bundleTreeCleanupService, org.mockito.Mockito.never()).purgeExistingTree(any(), org.mockito.ArgumentMatchers.anyString());
        verify(subprogramRepository, org.mockito.Mockito.never()).delete(stale);
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
        given(subprogramRepository.findFirstByTreeRootPathAndIsDeletedFalse(target.toString()))
                .willReturn(Optional.of(occupant));

        assertThatThrownBy(() -> subprogramService.addSubprogram(
                SubprogramKind.DRIVER, BoardScope.ofBoard(10L),
                req("new", "1.0", target),
                SubprogramUploadMode.SINGLE_FILE, null, null, null))
                .isInstanceOf(SubprogramPathConflictException.class);
    }

    @Test
    @DisplayName("update : entrypointRelativePath 입력 → entity 에 저장")
    void update_entrypoint() {
        Subprogram sp = Subprogram.builder()
                .id(5L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp));

        subprogramService.update(5L, new SubprogramUpdateRequest("n", "v", "desc", "install.sh"));

        assertThat(sp.getEntrypointRelativePath()).isEqualTo("install.sh");
    }

    @Test
    @DisplayName("C2 — update : entrypointRelativePath 빈 입력(null) → 기존 값 유지 (wipe 방지)")
    void update_entrypointBlank_keepsExisting() {
        Subprogram sp = Subprogram.builder()
                .id(7L).kind(SubprogramKind.DRIVER).boardModel(activeBoard())
                .name("n").version("v").treeRootPath("/p").manifestHash("h")
                .entrypointRelativePath("install.sh") // 기존 값
                .fileCount(1).totalBytes(1L).isDeleted(false).build();
        given(subprogramRepository.findById(7L)).willReturn(Optional.of(sp));

        // null entrypoint 로 update 호출 — 기존 값이 유지되어야 한다.
        subprogramService.update(7L, new SubprogramUpdateRequest("n", "v", "desc", null));

        assertThat(sp.getEntrypointRelativePath()).isEqualTo("install.sh");

        // blank("   ") 도 동일.
        subprogramService.update(7L, new SubprogramUpdateRequest("n", "v", "desc", "   "));
        assertThat(sp.getEntrypointRelativePath()).isEqualTo("install.sh");
    }

    // ──────────────────────────────────────────────────────────────
    // R2-2-1 — 부모(BoardModel) lifecycle 가드 (UI 1차 차단의 서버 안전망)
    // ──────────────────────────────────────────────────────────────

    private BoardModel board(boolean enabled, boolean deprecated, boolean deleted) {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted).build();
    }

    private Subprogram sp(Long id, BoardModel parent, boolean enabled, boolean deprecated, boolean deleted) {
        return Subprogram.builder()
                .id(id).kind(SubprogramKind.DRIVER).boardModel(parent)
                .name("a").version("1.0").treeRootPath("/p").manifestHash("h")
                .fileCount(1).totalBytes(1L)
                .isEnabled(enabled).isDeprecated(deprecated).isDeleted(deleted).build();
    }

    @Test
    @DisplayName("toggleEnabled : 부모 DISABLED → 자식 활성화 거절 (ChildLifecycleBlockedByParent)")
    void toggle_parentDisabled_blocks() {
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp(5L, board(false, false, false), false, false, false)));
        assertThatThrownBy(() -> subprogramService.toggleEnabled(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("toggleEnabled : 부모 DELETED → 자식 활성화 거절 (comprehensive)")
    void toggle_parentDeleted_blocks() {
        given(subprogramRepository.findById(5L)).willReturn(Optional.of(sp(5L, board(true, false, true), false, false, false)));
        assertThatThrownBy(() -> subprogramService.toggleEnabled(5L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("undeprecate : 부모 DEPRECATED → 자식 undeprecate 거절")
    void undeprecate_parentDeprecated_blocks() {
        given(subprogramRepository.findById(6L)).willReturn(Optional.of(sp(6L, board(true, true, false), true, true, false)));
        assertThatThrownBy(() -> subprogramService.undeprecate(6L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("undeprecate : 부모 DELETED → 자식 undeprecate 거절")
    void undeprecate_parentDeleted_blocks() {
        given(subprogramRepository.findById(6L)).willReturn(Optional.of(sp(6L, board(true, false, true), true, true, false)));
        assertThatThrownBy(() -> subprogramService.undeprecate(6L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("restore : 부모 DELETED → 자식 단독 restore 거절")
    void restore_parentDeleted_blocks() {
        given(subprogramRepository.findById(7L)).willReturn(Optional.of(sp(7L, board(true, false, true), false, false, true)));
        assertThatThrownBy(() -> subprogramService.restore(7L))
                .isInstanceOf(ChildLifecycleBlockedByParentException.class);
    }

    @Test
    @DisplayName("toggleEnabled : 공용(boardModel=null) → 부모 가드 미적용, 정상 활성화")
    void toggle_commonScope_passes() {
        Subprogram s = sp(8L, null, false, false, false);
        given(subprogramRepository.findById(8L)).willReturn(Optional.of(s));
        subprogramService.toggleEnabled(8L);
        assertThat(s.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("toggleEnabled : 부모 ACTIVE → 정상 활성화")
    void toggle_parentActive_passes() {
        Subprogram s = sp(9L, board(true, false, false), false, false, false);
        given(subprogramRepository.findById(9L)).willReturn(Optional.of(s));
        subprogramService.toggleEnabled(9L);
        assertThat(s.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("undeprecate : 공용 → 부모 가드 미적용, 정상 해제")
    void undeprecate_commonScope_passes() {
        Subprogram s = sp(11L, null, true, true, false);
        given(subprogramRepository.findById(11L)).willReturn(Optional.of(s));
        subprogramService.undeprecate(11L);
        assertThat(s.isDeprecated()).isFalse();
    }

    @Test
    @DisplayName("restore : 공용 → 부모 가드 건너뜀, 기존 중복키 가드는 그대로 (Duplicate)")
    void restore_commonScope_skipsParentGuard_keepsDupGuard() {
        given(subprogramRepository.findById(12L)).willReturn(Optional.of(sp(12L, null, false, false, true)));
        given(subprogramRepository.findActiveByCommonKey(SubprogramKind.DRIVER, "a", "1.0"))
                .willReturn(Optional.of(sp(99L, null, true, false, false)));
        assertThatThrownBy(() -> subprogramService.restore(12L))
                .isInstanceOf(DuplicateSubprogramVersionException.class);
    }

}
