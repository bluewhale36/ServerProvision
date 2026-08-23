package com.example.serverprovision.management.bmc.service;

import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.bmc.dto.response.BoardWithBmcListResponse;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * R5-3 CP4 — 잔류 BmcService(read + update 코어) 단위 테스트.
 *
 * <p>5분할 후 lifecycle(toggle/softDelete/restore/deprecate/purge) 은 {@code BmcLifecycleServiceTest},
 * 등록(addBmc) 은 {@code BmcRegistrationServiceTest}, 무결성 검증은 {@code BmcIntegrityServiceTest} 로 이관.
 * 본 file 은 조회(findAllGrouped) 시나리오만 보유한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BmcServiceTest {

    @Mock BmcRepository bmcRepository;
    @Mock BoardModelRepository boardModelRepository;
    @InjectMocks BmcService bmcService;

    private BoardModel activeBoard() {
        return BoardModel.builder()
                .id(10L).vendor(Vendor.GIGABYTE).modelName("MS03-CE0")
                .isEnabled(true).isDeleted(false).build();
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

    // ==== E2-1-a — 버전 순위 (BiosService 와 대칭 계약 — happy 1 + 실패 1) ====

    @Test
    @DisplayName("reorderVersionRanks(happy) : 요청 순서대로 밀집 재번호 (대칭 계약)")
    void reorder_reassigns() {
        com.example.serverprovision.management.board.entity.BoardModel board =
                com.example.serverprovision.management.board.entity.BoardModel.builder()
                        .id(10L).vendor(com.example.serverprovision.management.board.enums.Vendor.GIGABYTE)
                        .modelName("MS03-CE0").isEnabled(true).isDeleted(false).build();
        org.mockito.BDDMockito.given(boardModelRepository.findByIdAndIsDeletedFalse(10L))
                .willReturn(java.util.Optional.of(board));
        com.example.serverprovision.management.bmc.entity.BoardBMC x = bmcOf(board, 1L, 1);
        com.example.serverprovision.management.bmc.entity.BoardBMC y = bmcOf(board, 2L, 2);
        org.mockito.BDDMockito.given(bmcRepository.findAllByBoardModel_IdOrderByVersionRankAsc(10L))
                .willReturn(java.util.List.of(x, y));

        bmcService.reorderVersionRanks(10L, java.util.List.of(2L, 1L));

        org.assertj.core.api.Assertions.assertThat(y.getVersionRank()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(x.getVersionRank()).isEqualTo(2);
    }

    @Test
    @DisplayName("reorderVersionRanks(fail) : 타 보드 id → BmcNotFoundException (forging 404)")
    void reorder_foreignId_notFound() {
        com.example.serverprovision.management.board.entity.BoardModel board =
                com.example.serverprovision.management.board.entity.BoardModel.builder()
                        .id(10L).vendor(com.example.serverprovision.management.board.enums.Vendor.GIGABYTE)
                        .modelName("MS03-CE0").isEnabled(true).isDeleted(false).build();
        org.mockito.BDDMockito.given(boardModelRepository.findByIdAndIsDeletedFalse(10L))
                .willReturn(java.util.Optional.of(board));
        org.mockito.BDDMockito.given(bmcRepository.findAllByBoardModel_IdOrderByVersionRankAsc(10L))
                .willReturn(java.util.List.of(bmcOf(board, 1L, 1)));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> bmcService.reorderVersionRanks(10L, java.util.List.of(99L)))
                .isInstanceOf(com.example.serverprovision.management.bmc.exception.BmcNotFoundException.class);
    }

    private static com.example.serverprovision.management.bmc.entity.BoardBMC bmcOf(
            com.example.serverprovision.management.board.entity.BoardModel board, long id, int rank) {
        return com.example.serverprovision.management.bmc.entity.BoardBMC.builder()
                .id(id).boardModel(board)
                .name("m" + id).version("13.06." + id)
                .treeRootPath("/tmp/m" + id).legacyFilePath("/tmp/m" + id).boardModelIdMirror(board.getId())
                .entrypointRelativePath("b.nsh").manifestHash("h" + id)
                .fileCount(1).totalBytes(10L)
                .versionRank(rank)
                .isEnabled(true).isDeleted(false).build();
    }
}
