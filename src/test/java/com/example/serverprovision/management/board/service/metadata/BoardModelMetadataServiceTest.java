package com.example.serverprovision.management.board.service.metadata;

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
import com.example.serverprovision.management.board.exception.BoardModelNudgeRequiredException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.common.nudge.ContentNudgePayload;
import com.example.serverprovision.management.common.nudge.IntentMetaNudgePayload;
import com.example.serverprovision.management.common.nudge.NudgePayload;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
import com.example.serverprovision.management.common.nudge.NudgeResourceType;
import com.example.serverprovision.management.common.nudge.NudgeSession;
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
 * <p>구 {@code BoardModelServiceTest} 의 메타 CRUD / 생성 / nudge confirm 시나리오를 흡수했다.
 * 자식 자원 repository 는 개수 집계용으로만 사용하므로 mock 으로 빈 리스트 stub 한다(실제 DB 접근 없음).</p>
 */
@ExtendWith(MockitoExtension.class)
class BoardModelMetadataServiceTest {

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock SubprogramRepository subprogramRepository;
    @Mock NudgeRegistry nudgeRegistry;
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
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(3L))
                .willReturn(List.of(bios));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(3L))
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

        List<VendorGroupResponse> groups = boardModelService.findAllGrouped(false);

        assertThat(groups).hasSize(2);
        // ASUS 그룹의 보드 1건 — BIOS 1, BMC 0
        VendorGroupResponse asusGroup = groups.stream()
                .filter(g -> g.vendor() == Vendor.ASUS).findFirst().orElseThrow();
        assertThat(asusGroup.items()).hasSize(1);
        assertThat(asusGroup.items().get(0).biosCount()).isEqualTo(1);
        assertThat(asusGroup.items().get(0).bmcCount()).isZero();
    }

    // ==== create ====================================================

    @Test
    @DisplayName("create(happy) : 동일 (vendor, modelName) 활성/후보 0 → save 후 ID 반환")
    void create_whenNotDuplicated_returnsGeneratedId() {
        BoardModelCreateRequest request = new BoardModelCreateRequest(
                Vendor.GIGABYTE, "MS03-CE0-000", "AM5 소켓");
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS03-CE0-000"))
                .willReturn(false);
        given(boardModelRepository.findAllByVendorAndModelNameAndIsDeletedTrue(Vendor.GIGABYTE, "MS03-CE0-000"))
                .willReturn(List.of());
        given(boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(Vendor.GIGABYTE, "MS03-CE0-000"))
                .willReturn(List.of());
        given(boardModelRepository.save(any(BoardModel.class)))
                .willAnswer(invocation -> {
                    BoardModel arg = invocation.getArgument(0);
                    return BoardModel.builder()
                            .id(1L)
                            .vendor(arg.getVendor())
                            .modelName(arg.getModelName())
                            .description(arg.getDescription())
                            .isEnabled(true).isDeleted(false)
                            .build();
                });

        Long generatedId = boardModelService.create(request);

        assertThat(generatedId).isEqualTo(1L);
        verify(boardModelRepository).save(any(BoardModel.class));
    }

    @Test
    @DisplayName("create(fail) : 동일 (vendor, modelName) 순수 활성 레코드(Deprecated 아님)가 있으면 DuplicateBoardModelException")
    void create_whenDuplicatedActive_throws() {
        BoardModelCreateRequest request = new BoardModelCreateRequest(
                Vendor.FUJITSU, "PRIMERGY RX2530 M6", null);
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.FUJITSU, "PRIMERGY RX2530 M6"))
                .willReturn(true);
        given(boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(Vendor.FUJITSU, "PRIMERGY RX2530 M6"))
                .willReturn(List.of());

        assertThatThrownBy(() -> boardModelService.create(request))
                .isInstanceOf(DuplicateBoardModelException.class);
        verify(boardModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("create(nudge) : soft-deleted/deprecated 후보가 있으면 nudge 세션 발급 후 BoardModelNudgeRequiredException")
    void create_whenCandidatesExist_throwsNudgeRequired() {
        BoardModelCreateRequest request = new BoardModelCreateRequest(
                Vendor.ASUS, "P13R-E", "재등록 시도");
        // 순수 활성 충돌은 없으나(existsFalse) soft-deleted 후보가 존재 → nudge.
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);
        BoardModel softDeleted = BoardModel.builder()
                .id(8L).vendor(Vendor.ASUS).modelName("P13R-E")
                .isEnabled(false).isDeprecated(false).isDeleted(true).build();
        given(boardModelRepository.findAllByVendorAndModelNameAndIsDeletedTrue(Vendor.ASUS, "P13R-E"))
                .willReturn(List.of(softDeleted));
        given(boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(List.of());
        given(nudgeRegistry.register(any(), any(), anyList(), any()))
                .willReturn(boardSession(UUID.randomUUID(),
                        new IntentMetaNudgePayload(Map.of("modelName", "P13R-E", "vendor", "ASUS", "description", ""))));

        assertThatThrownBy(() -> boardModelService.create(request))
                .isInstanceOf(BoardModelNudgeRequiredException.class);
        // nudge 분기에서는 신규 row 를 영속화하지 않는다.
        verify(boardModelRepository, never()).save(any());
    }

    // ==== update ====================================================

    @Test
    @DisplayName("update(happy) : modelName/description 갱신 — modelName 변경 없으면 중복 재검증 생략")
    void update_appliesChanges() {
        BoardModel board = activeBoard(3L, Vendor.ASUS, "P13R-E");
        given(boardModelRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(board));
        BoardModelUpdateRequest request = new BoardModelUpdateRequest("P13R-E", "새 설명");

        boardModelService.update(3L, request);

        assertThat(board.getDescription()).isEqualTo("새 설명");
        // modelName 동일 → 중복 재검증 쿼리 미호출
        verify(boardModelRepository, never())
                .existsByVendorAndModelNameAndIsDeletedFalse(any(), any());
    }

    @Test
    @DisplayName("update(중복) : modelName 변경 시 동일 (vendor, modelName) 활성 충돌 → DuplicateBoardModelException")
    void update_whenRenamedToDuplicate_throws() {
        BoardModel board = activeBoard(3L, Vendor.ASUS, "P13R-E");
        given(boardModelRepository.findByIdAndIsDeletedFalse(3L)).willReturn(Optional.of(board));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E-rev2"))
                .willReturn(true);
        BoardModelUpdateRequest request = new BoardModelUpdateRequest("P13R-E-rev2", "new");

        assertThatThrownBy(() -> boardModelService.update(3L, request))
                .isInstanceOf(DuplicateBoardModelException.class);
        // 충돌 거절 → 엔티티는 갱신되지 않는다.
        assertThat(board.getModelName()).isEqualTo("P13R-E");
    }

    @Test
    @DisplayName("update(404) : 활성 보드가 없으면 BoardModelNotFoundException")
    void update_notFound_throws() {
        given(boardModelRepository.findByIdAndIsDeletedFalse(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> boardModelService.update(999L, new BoardModelUpdateRequest("x", "y")))
                .isInstanceOf(BoardModelNotFoundException.class);
    }

    // ==== completePendingBoardFromNudge ==============================

    @Test
    @DisplayName("completePendingBoardFromNudge(happy) : intent payload 로 신규 보드 영속화 후 ID 반환")
    void completePendingBoardFromNudge_persistsNewBoard() {
        NudgeSession session = boardSession(UUID.randomUUID(),
                new IntentMetaNudgePayload(Map.of("modelName", "P13R-E", "vendor", "ASUS", "description", "복원 등록")));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(false);
        given(boardModelRepository.save(any(BoardModel.class)))
                .willAnswer(inv -> {
                    BoardModel arg = inv.getArgument(0);
                    return BoardModel.builder().id(50L)
                            .vendor(arg.getVendor()).modelName(arg.getModelName())
                            .description(arg.getDescription()).isEnabled(true).isDeleted(false).build();
                });

        Long newId = boardModelService.completePendingBoardFromNudge(session);

        assertThat(newId).isEqualTo(50L);
        verify(boardModelRepository).save(any(BoardModel.class));
    }

    @Test
    @DisplayName("completePendingBoardFromNudge(payload 타입오류) : IntentMetaNudgePayload 가 아니면 IllegalBoardModelStateException")
    void completePendingBoardFromNudge_wrongPayloadType_throws() {
        NudgeSession session = boardSession(UUID.randomUUID(),
                new ContentNudgePayload("name", "1.0", "hash", "/tmp/x", Map.of()));

        assertThatThrownBy(() -> boardModelService.completePendingBoardFromNudge(session))
                .isInstanceOf(IllegalBoardModelStateException.class);
        verify(boardModelRepository, never()).save(any());
    }

    @Test
    @DisplayName("completePendingBoardFromNudge(race) : confirm 직전 다른 트랜잭션이 동일 활성 메타 생성 → DuplicateBoardModelException")
    void completePendingBoardFromNudge_race_throwsDuplicate() {
        NudgeSession session = boardSession(UUID.randomUUID(),
                new IntentMetaNudgePayload(Map.of("modelName", "P13R-E", "vendor", "ASUS", "description", "")));
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(true);
        given(boardModelRepository.findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(Vendor.ASUS, "P13R-E"))
                .willReturn(List.of());

        assertThatThrownBy(() -> boardModelService.completePendingBoardFromNudge(session))
                .isInstanceOf(DuplicateBoardModelException.class);
        verify(boardModelRepository, never()).save(any());
    }

    // ==== purgeBoardForNudge =========================================

    @Test
    @DisplayName("purgeBoardForNudge(happy) : soft-deleted 후보를 영구 삭제(replace 교체)")
    void purgeBoardForNudge_deletesCandidate() {
        BoardModel target = BoardModel.builder()
                .id(8L).vendor(Vendor.ASUS).modelName("P13R-E")
                .isEnabled(false).isDeprecated(false).isDeleted(true).build();

        boardModelService.purgeBoardForNudge(target);

        verify(boardModelRepository).delete(target);
    }

    @Test
    @DisplayName("purgeBoardForNudge(활성 거절) : 활성 자원(not deleted/deprecated)은 replace 대상 불가 → IllegalBoardModelStateException")
    void purgeBoardForNudge_activeTarget_throws() {
        BoardModel active = activeBoard(8L, Vendor.ASUS, "P13R-E");

        assertThatThrownBy(() -> boardModelService.purgeBoardForNudge(active))
                .isInstanceOf(IllegalBoardModelStateException.class);
        verify(boardModelRepository, never()).delete(any());
    }

    // ==== helper =====================================================

    private NudgeSession boardSession(UUID nudgeId, NudgePayload payload) {
        return new NudgeSession(
                nudgeId,
                NudgeResourceType.BOARD_MODEL,
                null,
                List.of(8L),
                payload,
                Instant.now(),
                Instant.now().plusSeconds(300)
        );
    }
}
