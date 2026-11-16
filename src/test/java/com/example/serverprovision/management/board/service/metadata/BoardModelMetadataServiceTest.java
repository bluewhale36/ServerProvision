package com.example.serverprovision.management.board.service.metadata;

import com.example.serverprovision.global.lifecycle.dto.ResourceListQuery;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.dto.request.BoardModelUpdateRequest;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.dto.response.VendorGroupResponse;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.board.service.purge.BoardModelPurgeGuard;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * R3-3 — {@link BoardModelMetadataService} 단위 테스트.
 *
 * <p>구 {@code BoardModelServiceTest} 의 메타 CRUD / 생성 시나리오를 흡수했다. HF26 에서 등록 중복 확인 창(nudge)을 없애고
 * (제조사, 모델명) 이 어떤 상태로든 있으면 상태별 안내와 함께 거절하도록 바꿨다.
 * 자식 자원 repository 는 개수 집계용으로만 사용하므로 mock 으로 빈 리스트 stub 한다(실제 DB 접근 없음).</p>
 */
@ExtendWith(MockitoExtension.class)
class BoardModelMetadataServiceTest {

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock SubprogramRepository subprogramRepository;
    @Mock BoardModelPurgeGuard purgeGuard;
    @InjectMocks BoardModelMetadataService boardModelService;

    private BoardModel activeBoard(Long id, Vendor vendor, String modelName) {
        return BoardModel.builder()
                .id(id).vendor(vendor).modelName(modelName).description("desc")
                .isEnabled(true).isDeprecated(false).isDeleted(false)
                .build();
    }

    // ==== findById ====================================================

    @Test
    @DisplayName("findById(happy) : 활성 보드 + 자식 BIOS/BMC/Subprogram 개수를 집계해 응답한다")
    void findById_aggregatesChildCounts() {
        BoardModel board = activeBoard(3L, Vendor.ASUS, "P13R-E");
        given(boardModelRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(board));
        BoardBIOS bios = BoardBIOS.builder().id(11L).boardModel(board).name("b").version("1.0")
                .isEnabled(true).isDeleted(false).build();
        BoardBMC bmc = BoardBMC.builder().id(21L).boardModel(board).name("m").version("2.0")
                .treeRootPath("/fw").legacyFilePath("/fw").boardModelIdMirror(3L)
                .entrypointRelativePath("flash.nsh").manifestHash("h").markerSignature("s")
                .fileCount(1).totalBytes(1L).isEnabled(true).isDeleted(false).build();
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalse(3L))
                .willReturn(List.of(bios));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalse(3L))
                .willReturn(List.of(bmc));
        given(subprogramRepository.findAllByBoardModel_IdAndIsDeletedFalse(3L))
                .willReturn(List.of());

        BoardModelResponse response = boardModelService.findById(3L);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.vendor()).isEqualTo(Vendor.ASUS);
        assertThat(response.biosCount()).isEqualTo(1);
        assertThat(response.bmcCount()).isEqualTo(1);
        assertThat(response.subprogramCount()).isZero();
    }

    @Test
    @DisplayName("findById(404) : 활성 보드가 없으면 BoardModelNotFoundException")
    void findById_notFound_throws() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.findById(999L))
                .isInstanceOf(BoardModelNotFoundException.class);
    }

    // ==== findAllGrouped =============================================

    @Test
    @DisplayName("findAllGrouped : vendor 별 그룹핑 + 자식 개수 집계(boardId 매칭)")
    void findAllGrouped_groupsByVendorWithCounts() {
        BoardModel asus = activeBoard(1L, Vendor.ASUS, "P13R-E");
        BoardModel giga = activeBoard(2L, Vendor.GIGABYTE, "MS03");
        given(boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc())
                .willReturn(List.of(asus, giga));

        BoardBIOS asusBios = BoardBIOS.builder().id(11L).boardModel(asus).name("b").version("1.0")
                .isEnabled(true).isDeleted(false).build();
        given(biosRepository.findAllByBoardModel_IdIn(anyList())).willReturn(List.of(asusBios));
        given(bmcRepository.findAllByBoardModel_IdIn(anyList())).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_IdIn(anyList())).willReturn(List.of());

        List<VendorGroupResponse> groups = boardModelService.findAllGrouped(ResourceListQuery.unfiltered(false));

        assertThat(groups).hasSize(2);
        // ASUS 그룹의 보드 1건 — BIOS 1, BMC 0
        VendorGroupResponse asusGroup = groups.stream()
                .filter(g -> g.vendor() == Vendor.ASUS).findFirst().orElseThrow();
        assertThat(asusGroup.items()).hasSize(1);
        assertThat(asusGroup.items().get(0).biosCount()).isEqualTo(1);
        assertThat(asusGroup.items().get(0).bmcCount()).isZero();
    }

    // ==== create (HF26 — 같은 (제조사, 모델명) 은 상태와 무관하게 거절) ====

    private BoardModel board(Long id, Vendor vendor, String modelName, boolean deprecated, boolean deleted) {
        return BoardModel.builder()
                .id(id).vendor(vendor).modelName(modelName)
                .isEnabled(!deleted).isDeprecated(deprecated).isDeleted(deleted)
                .build();
    }

    @Test
    @DisplayName("create(happy) : 같은 (제조사, 모델명) 이 없으면 save 후 ID 반환")
    void create_whenNotDuplicated_returnsGeneratedId() {
        BoardModelCreateRequest request = new BoardModelCreateRequest(Vendor.GIGABYTE, "MS03-CE0-000", "AM5 소켓");
        given(boardModelRepository.findByVendorAndModelName(Vendor.GIGABYTE, "MS03-CE0-000")).willReturn(Optional.empty());
        given(boardModelRepository.save(any(BoardModel.class)))
                .willAnswer(invocation -> {
                    BoardModel arg = invocation.getArgument(0);
                    return BoardModel.builder().id(1L).vendor(arg.getVendor()).modelName(arg.getModelName())
                            .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
                });

        assertThat(boardModelService.create(request)).isEqualTo(1L);
        verify(boardModelRepository).save(any(BoardModel.class));
    }

    @Test
    @DisplayName("create(활성 동명) : 이미 등록된 모델 → DuplicateBoardModelException, 저장 없음")
    void create_whenActiveExists_throws() {
        given(boardModelRepository.findByVendorAndModelName(Vendor.FUJITSU, "PRIMERGY RX2530 M6"))
                .willReturn(Optional.of(board(5L, Vendor.FUJITSU, "PRIMERGY RX2530 M6", false, false)));

        assertThatThrownBy(() -> boardModelService.create(new BoardModelCreateRequest(Vendor.FUJITSU, "PRIMERGY RX2530 M6", null)))
                .isInstanceOf(DuplicateBoardModelException.class)
                .hasMessageContaining("이미 등록된 메인보드 모델입니다");
        verify(boardModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("create(Deprecated 동명) : 활성 중복을 만들던 경로(A-8) → 거절 + Deprecated 해제 안내")
    void create_whenDeprecatedExists_throwsWithUndeprecateGuidance() {
        given(boardModelRepository.findByVendorAndModelName(Vendor.GIGABYTE, "MS74-HB0"))
                .willReturn(Optional.of(board(6L, Vendor.GIGABYTE, "MS74-HB0", true, false)));

        assertThatThrownBy(() -> boardModelService.create(new BoardModelCreateRequest(Vendor.GIGABYTE, "MS74-HB0", null)))
                .isInstanceOf(DuplicateBoardModelException.class)
                .hasMessageContaining("목록에서 Deprecated 를 해제하십시오");
        verify(boardModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("create(휴지통 동명) : 거절 + 복구 · 영구 삭제 안내(휴지통 행도 정체를 점유)")
    void create_whenTrashedExists_throwsWithRestoreGuidance() {
        given(boardModelRepository.findByVendorAndModelName(Vendor.ASUS, "P13R-E"))
                .willReturn(Optional.of(board(8L, Vendor.ASUS, "P13R-E", false, true)));

        assertThatThrownBy(() -> boardModelService.create(new BoardModelCreateRequest(Vendor.ASUS, "P13R-E", "재등록")))
                .isInstanceOf(DuplicateBoardModelException.class)
                .hasMessageContaining("휴지통에서 복구하거나 영구 삭제한 뒤 등록하십시오");
        verify(boardModelRepository, never()).save(any());
    }

    // ==== update ====================================================

    @Test
    @DisplayName("update(happy) : modelName/description 갱신 — modelName 변경 없으면 중복 재검증 생략")
    void update_appliesChanges() {
        BoardModel board = activeBoard(3L, Vendor.ASUS, "P13R-E");
        given(boardModelRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(board));

        boardModelService.update(3L, new BoardModelUpdateRequest("P13R-E", "새 설명"));

        assertThat(board.getDescription()).isEqualTo("새 설명");
        verify(boardModelRepository, never()).findByVendorAndModelName(any(), any());
    }

    @Test
    @DisplayName("update(중복) : 바꾸려는 이름이 휴지통에 있어도 거절(등록과 같은 규칙) — 엔티티는 그대로")
    void update_whenRenamedToTrashedName_throws() {
        BoardModel board = activeBoard(3L, Vendor.ASUS, "P13R-E");
        given(boardModelRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(board));
        given(boardModelRepository.findByVendorAndModelName(Vendor.ASUS, "P13R-E-rev2"))
                .willReturn(Optional.of(board(9L, Vendor.ASUS, "P13R-E-rev2", false, true)));

        assertThatThrownBy(() -> boardModelService.update(3L, new BoardModelUpdateRequest("P13R-E-rev2", "new")))
                .isInstanceOf(DuplicateBoardModelException.class);
        assertThat(board.getModelName()).isEqualTo("P13R-E");
    }

    @Test
    @DisplayName("update(404) : 활성 보드가 없으면 BoardModelNotFoundException")
    void update_notFound_throws() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.update(999L, new BoardModelUpdateRequest("x", "y")))
                .isInstanceOf(BoardModelNotFoundException.class);
    }

    @Test
    @DisplayName("HF26 findAllGrouped : 휴지통 행에만 영구 삭제 차단 사유를 묻는다(활성 행은 영구 삭제 버튼이 없다)")
    void findAllGrouped_purgeReasonOnlyForDeletedRows() {
        BoardModel active = activeBoard(1L, Vendor.GIGABYTE, "MS04-CE0");
        BoardModel trashed = board(2L, Vendor.GIGABYTE, "MS74-HB0", false, true);
        given(boardModelRepository.findAllByOrderByVendorAscCreatedAtDesc()).willReturn(List.of(active, trashed));
        given(biosRepository.findAllByBoardModel_IdIn(anyList())).willReturn(List.of());
        given(bmcRepository.findAllByBoardModel_IdIn(anyList())).willReturn(List.of());
        given(subprogramRepository.findAllByBoardModel_IdIn(anyList())).willReturn(List.of());
        given(purgeGuard.reasonFor(2L)).willReturn("이 메인보드 모델을 참조하는 다음 항목이 남아 있어 영구 삭제할 수 없습니다: 등록된 서버 1 대.");

        List<BoardModelResponse> items = boardModelService.findAllGrouped(ResourceListQuery.unfiltered(true)).getFirst().items();

        assertThat(items).filteredOn(BoardModelResponse::isDeleted).singleElement()
                .satisfies(b -> assertThat(b.purgeBlockReason()).contains("등록된 서버 1 대"));
        assertThat(items).filteredOn(b -> !b.isDeleted()).singleElement()
                .satisfies(b -> assertThat(b.purgeBlocked()).isFalse());
        verify(purgeGuard, never()).reasonFor(1L);
    }

    @Test
    @DisplayName("HF26 CP5 D-2 renameConflictReason : 같은 이름이면 null, 휴지통 동명이면 이름 수정용 문구(등록 문구와 다른 다음 행동)")
    void renameConflictReason_usesRenameCopy() {
        BoardModel board = activeBoard(3L, Vendor.ASUS, "P13R-E");
        given(boardModelRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(board));
        given(boardModelRepository.findByVendorAndModelName(Vendor.ASUS, "P13R-E-rev2"))
                .willReturn(Optional.of(board(9L, Vendor.ASUS, "P13R-E-rev2", false, true)));

        assertThat(boardModelService.renameConflictReason(3L, "P13R-E")).isNull();
        assertThat(boardModelService.renameConflictReason(3L, "P13R-E-rev2"))
                .contains("다른 이름을 쓰거나 휴지통의 모델을 영구 삭제하십시오")
                .doesNotContain("등록하십시오");
    }
}
