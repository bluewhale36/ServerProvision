package com.example.serverprovision.domain.board.dto;

import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.node.model.enums.Vendor;
import lombok.Builder;

/**
 * {@code BoardModel} 엔티티의 읽기 전용 DTO이다.
 *
 * <p>역할: {@code BoardModel} 엔티티를 HTTP 응답 및 뷰 레이어에 노출하기 위한 데이터 전달 객체이다.
 * {@code BoardBIOSDTO}, {@code BoardBMCDTO}에서 {@code compatibleModel} 필드로 참조되어
 * BIOS/BMC와 메인보드의 호환 관계를 표현한다.</p>
 *
 * <p>유스케이스: {@code BoardModelService#getAllBoardModel}, {@code getAllActiveBoardModel}이
 * {@code BoardModel} 엔티티 리스트를 이 DTO로 변환하여 반환한다. 세팅 주문서 생성 폼에서
 * {@code BoardOptionSelectView}에 포함되어 보드 모델 선택 UI를 구성하며, {@code BasicUpdate}
 * 생성자에서 BIOS/BMC의 {@code compatibleModel}과 동등성 비교({@code equals})에 사용된다.</p>
 *
 * <p>확장 가이드: {@code BoardModel}에 새 필드를 추가할 경우 이 DTO에도 동일 필드를 추가하고,
 * {@code from} 팩토리 메소드에서 매핑 로직을 갱신한다. {@code BoardOptionSelectView}와 프론트엔드
 * {@code data-*} 속성 직렬화 로직도 함께 확인한다.</p>
 */
@Builder
public record BoardModelDTO(
        Long id,
        Vendor vendor,
        String modelName,
        String description,
        Boolean isEnabled
) {
    public static BoardModelDTO from(BoardModel boardModel) {
        return BoardModelDTO.builder()
                .id(boardModel.getId())
                .vendor(boardModel.getVendor())
                .modelName(boardModel.getModelName())
                .description(boardModel.getDescription())
                .isEnabled(boardModel.isEnabled())
                .build();
    }
}
