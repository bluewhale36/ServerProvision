package com.example.serverprovision.domain.board.dto;

import com.example.serverprovision.domain.board.entity.BoardBMC;
import lombok.Builder;

/**
 * {@code BoardBMC} 엔티티의 읽기 전용 DTO이다.
 *
 * <p>역할: BMC 펌웨어 버전 정보를 뷰 레이어 및 {@code BasicUpdate} 도메인 모델에 전달하는
 * 데이터 전달 객체이다. {@code compatibleModel}로 연결된 {@code BoardModelDTO}를 내포하여
 * BMC 펌웨어와 메인보드의 호환 관계를 표현한다. {@code BoardBIOSDTO}와 구조가 동일하다.</p>
 *
 * <p>유스케이스: {@code BoardModelService#getViewModelList}가 전체 {@code BoardBMC}를
 * 이 DTO로 변환한 후 {@code BoardOptionSelectView#of}에서 메인보드 모델별로 필터링한다.
 * 세팅 주문서 생성 시 {@code BasicUpdateResolver}가 조회한 엔티티를 {@code from}으로
 * 변환하여 {@code BasicUpdate} 생성자에 전달한다.</p>
 *
 * <p>확장 가이드: {@code BoardBMC}에 새 필드를 추가할 경우 이 DTO와 {@code from} 메소드,
 * 세팅 주문서 생성 폼의 BMC 선택 UI도 함께 갱신한다. {@code BoardBIOSDTO}와 항상 구조를
 * 일치시켜 프론트엔드 폼의 BIOS/BMC 동시 처리 로직이 깨지지 않도록 한다.</p>
 */
@Builder
public record BoardBMCDTO(
        Long id,
        BoardModelDTO compatibleModel,
        String version,
        String filePath,
        String description,
        Boolean isEnabled
) {
    public static BoardBMCDTO from(BoardBMC boardBMC) {
        return BoardBMCDTO.builder()
                .id(boardBMC.getId())
                .compatibleModel(BoardModelDTO.from(boardBMC.getCompatibleModel()))
                .version(boardBMC.getVersion())
                .filePath(boardBMC.getFilePath())
                .description(boardBMC.getDescription())
                .isEnabled(boardBMC.isEnabled())
                .build();
    }
}
