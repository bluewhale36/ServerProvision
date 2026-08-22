package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.bios.dto.request.BiosUpdateRequest;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.exception.DuplicateBiosVersionException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * R4-3 — 5분할 후 잔류 {@code BiosService}(read + update 코어) 단위 테스트.
 *
 * <p>lifecycle / 등록 / 무결성 시나리오는 각각 {@code BiosLifecycleServiceTest} / {@code BiosRegistrationServiceTest}
 * / {@code BiosIntegrityServiceTest} 로 이동했다. 본 file 은 조회(findBios / findAllGrouped) + 메타 수정(update) 만 검증.</p>
 */
@ExtendWith(MockitoExtension.class)
class BiosServiceTest {

    @Mock BiosRepository biosRepository;
    @Mock BoardModelRepository boardModelRepository;
    @InjectMocks BiosService biosService;

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
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

    // ==== 조회 ========================================================

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

    // ==== 메타 수정 ===================================================

    @Test
    @DisplayName("update(happy) : 메타 갱신 (버전 동일 → 중복 검사 생략)")
    void update_happy() {
        BoardBIOS bios = buildActiveBios();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));

        biosService.update(10L, 1L, new BiosUpdateRequest("new-name", "1.0", "desc"));

        assertThat(bios.getName()).isEqualTo("new-name");
        assertThat(bios.getDescription()).isEqualTo("desc");
    }

    @Test
    @DisplayName("update(fail) : 버전 변경 시 (board, version) 중복 → DuplicateBiosVersionException")
    void update_duplicateVersion_throws() {
        BoardBIOS bios = buildActiveBios();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(Optional.of(activeBoard()));
        given(biosRepository.findByIdAndBoardModel_Id(1L, 10L)).willReturn(Optional.of(bios));
        given(biosRepository.existsByBoardModel_IdAndVersionAndIsDeletedFalse(10L, "2.0")).willReturn(true);

        assertThatThrownBy(() -> biosService.update(10L, 1L, new BiosUpdateRequest("x", "2.0", "")))
                .isInstanceOf(DuplicateBiosVersionException.class);
    }

    // ==== E2-1-a — 버전 순위 (순서 SSOT) ================================

    private BoardBIOS rankedBios(long id, BoardModel board, int rank, boolean enabled, boolean deleted) {
        return BoardBIOS.builder()
                .id(id).boardModel(board)
                .name("b" + id).version("V" + id)
                .treeRootPath("/tmp/" + id).entrypointRelativePath("f.nsh")
                .manifestHash("h" + id).markerSignature("s")
                .fileCount(1).totalBytes(10L)
                .versionRank(rank)
                .isEnabled(enabled).isDeleted(deleted).build();
    }

    @Test
    @DisplayName("findAllGrouped : 목록은 순위 오름차순(1 = 최신) — 문자열 정렬이 아니라 운영자 순서(E2-1-a)")
    void findAllGrouped_ordersByVersionRank() {
        BoardModel board = activeBoard();
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(board));
        // 표기 체계가 섞여도(2101 · A40) 순위가 순서를 정한다 — 채택 동기의 단위 실증.
        BoardBIOS second = rankedBios(1L, board, 2, true, false);
        BoardBIOS first = rankedBios(2L, board, 1, true, false);
        given(biosRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(second, first));

        var groups = biosService.findAllGrouped(false);

        assertThat(groups.get(0).biosList()).extracting(r -> r.id()).containsExactly(2L, 1L);
        assertThat(groups.get(0).latestBiosId()).isEqualTo(2L);   // 순위 1위 enabled = 최신
    }

    @Test
    @DisplayName("findAllGrouped : 순위 1위가 비활성이면 '최신' 은 다음 enabled — resolve 의 LATEST 와 같은 술어")
    void findAllGrouped_latestSkipsDisabledTop() {
        BoardModel board = activeBoard();
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(board));
        BoardBIOS disabledTop = rankedBios(1L, board, 1, false, false);
        BoardBIOS enabledSecond = rankedBios(2L, board, 2, true, false);
        given(biosRepository.findAllByBoardModel_IdIn(List.of(10L))).willReturn(List.of(disabledTop, enabledSecond));

        var groups = biosService.findAllGrouped(false);

        assertThat(groups.get(0).latestBiosId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("reorderVersionRanks(happy) : 살아있는 행 재배열 + 삭제 행 상대 위치 보존 + 밀집 1..n")
    void reorder_reassignsDenseRanks_preservingDeletedSlots() {
        BoardModel board = activeBoard();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(java.util.Optional.of(board));
        BoardBIOS a = rankedBios(1L, board, 1, true, false);
        BoardBIOS trashed = rankedBios(9L, board, 2, true, true);   // soft-deleted — 2위 자리 보존 대상
        BoardBIOS b = rankedBios(2L, board, 3, true, false);
        given(biosRepository.findAllByBoardModel_IdOrderByVersionRankAsc(10L))
                .willReturn(List.of(a, trashed, b));

        biosService.reorderVersionRanks(10L, List.of(2L, 1L));   // b 를 1위로

        assertThat(b.getVersionRank()).isEqualTo(1);
        assertThat(trashed.getVersionRank()).isEqualTo(2);       // 상대 위치 보존
        assertThat(a.getVersionRank()).isEqualTo(3);
    }

    @Test
    @DisplayName("reorderVersionRanks(fail) : 중복 id → 400 InvalidVersionRankRequest / 누락 → 400 / 타 보드 → 404")
    void reorder_rejectsMalformedRequests() {
        BoardModel board = activeBoard();
        given(boardModelRepository.findByIdAndIsDeletedFalse(10L)).willReturn(java.util.Optional.of(board));
        BoardBIOS a = rankedBios(1L, board, 1, true, false);
        BoardBIOS b = rankedBios(2L, board, 2, true, false);
        given(biosRepository.findAllByBoardModel_IdOrderByVersionRankAsc(10L)).willReturn(List.of(a, b));

        assertThatThrownBy(() -> biosService.reorderVersionRanks(10L, List.of(1L, 1L)))
                .isInstanceOf(com.example.serverprovision.management.board.exception.InvalidVersionRankRequestException.class);
        assertThatThrownBy(() -> biosService.reorderVersionRanks(10L, List.of(1L)))
                .isInstanceOf(com.example.serverprovision.management.board.exception.InvalidVersionRankRequestException.class);
        assertThatThrownBy(() -> biosService.reorderVersionRanks(10L, List.of(1L, 77L)))
                .isInstanceOf(BiosNotFoundException.class);   // 타 보드 · 미존재 — forging 관례
        assertThat(a.getVersionRank()).isEqualTo(1);          // 거절 시 순위 불변
        assertThat(b.getVersionRank()).isEqualTo(2);
    }
}
