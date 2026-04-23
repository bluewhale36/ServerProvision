package com.example.serverprovision.maintenance.board.service;

import com.example.serverprovision.maintenance.board.dto.request.BoardModelCreateRequest;
import com.example.serverprovision.maintenance.board.entity.BoardModel;
import com.example.serverprovision.maintenance.board.enums.Vendor;
import com.example.serverprovision.maintenance.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.maintenance.board.repository.BoardModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
