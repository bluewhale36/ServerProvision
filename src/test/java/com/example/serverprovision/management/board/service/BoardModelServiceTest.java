package com.example.serverprovision.management.board.service;

import com.example.serverprovision.management.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.management.bios.entity.BoardBIOS;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bios.service.BiosService;
import com.example.serverprovision.management.bmc.entity.BoardBMC;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.bmc.service.BmcService;
import com.example.serverprovision.management.common.nudge.NudgeRegistry;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * BoardModelService 단위 테스트 — plan 규약 "happy 1 + 실패 1" 최소 범위.
 * Repository 는 Mockito 로 가짜 주입한다 (실제 DB 접근 없음).
 */
@ExtendWith(MockitoExtension.class)
class BoardModelServiceTest {

    @Mock BoardModelRepository boardModelRepository;
    @Mock BiosRepository biosRepository;
    @Mock BmcRepository bmcRepository;
    @Mock NudgeRegistry nudgeRegistry;
    @Mock BiosService biosService;
    @Mock BmcService bmcService;
    @InjectMocks BoardModelService boardModelService;

    @Test
    @DisplayName("create(happy) : 동일 (vendor, modelName) 활성 레코드가 없으면 저장 후 ID 반환")
    void create_whenNotDuplicated_returnsGeneratedId() {
        // given
        BoardModelCreateRequest request = new BoardModelCreateRequest(
                Vendor.GIGABYTE, "MS03-CE0-000", "AM5 소켓"
        );
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.GIGABYTE, "MS03-CE0-000"))
                .willReturn(false);
        given(boardModelRepository.save(any(BoardModel.class)))
                .willAnswer(invocation -> {
                    BoardModel arg = invocation.getArgument(0);
                    // JPA 가 id 할당한 상태를 흉내내기 위해 동일 필드로 id=1L 을 붙인 엔티티 재생성
                    return BoardModel.builder()
                            .id(1L)
                            .vendor(arg.getVendor())
                            .modelName(arg.getModelName())
                            .description(arg.getDescription())
                            .isEnabled(true)
                            .isDeleted(false)
                            .build();
                });

        // when
        Long generatedId = boardModelService.create(request);

        // then
        assertThat(generatedId).isEqualTo(1L);
        verify(boardModelRepository).save(any(BoardModel.class));
    }

    @Test
    @DisplayName("softDelete : BoardModel 이 soft 삭제되면 활성 하위 BIOS / BMC 도 함께 soft 삭제된다")
    void softDelete_cascadesToActiveChildren() {
        // given
        BoardModel board = BoardModel.builder()
                .id(7L).vendor(Vendor.ASUS).modelName("WS C621E SAGE")
                .isEnabled(true).isDeleted(false)
                .build();
        given(boardModelRepository.findByIdAndIsDeletedFalse(7L)).willReturn(Optional.of(board));

        BoardBIOS activeBios = BoardBIOS.builder()
                .id(101L).boardModel(board).name("WS BIOS").version("1.12")
                .isEnabled(true).isDeleted(false).build();
        BoardBMC activeBmc = BoardBMC.builder()
                .id(201L).boardModel(board).name("AST2600").version("12.61")
                .treeRootPath("/fw/bmc").legacyFilePath("/fw/bmc").boardModelIdMirror(7L)
                .entrypointRelativePath("flash.nsh")
                .manifestHash("hash").markerSignature("sig").fileCount(2).totalBytes(128L)
                .isEnabled(true).isDeleted(false).build();
        given(biosRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(7L))
                .willReturn(List.of(activeBios));
        given(bmcRepository.findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(7L))
                .willReturn(List.of(activeBmc));

        // when
        boardModelService.softDelete(7L);

        // then — Board 본인은 entity.softDelete() 직접 호출되어 state 변경.
        assertThat(board.isDeleted()).isTrue();
        // S5-2-3 정합화 — 하위 BIOS / BMC 는 service.softDelete 위임 (trashLifecycleService 통한 정상 trash 이동).
        verify(biosService).softDelete(7L, 101L);
        verify(bmcService).softDelete(7L, 201L);
    }

    @Test
    @DisplayName("create(fail) : 동일 (vendor, modelName) 활성 레코드가 있으면 DuplicateBoardModelException 으로 거절한다")
    void create_whenDuplicatedActive_throws() {
        // given
        BoardModelCreateRequest request = new BoardModelCreateRequest(
                Vendor.FUJITSU, "PRIMERGY RX2530 M6", null
        );
        given(boardModelRepository.existsByVendorAndModelNameAndIsDeletedFalse(Vendor.FUJITSU, "PRIMERGY RX2530 M6"))
                .willReturn(true);

        // when / then
        assertThatThrownBy(() -> boardModelService.create(request))
                .isInstanceOf(DuplicateBoardModelException.class);
        verify(boardModelRepository, never()).save(any());
    }
}
