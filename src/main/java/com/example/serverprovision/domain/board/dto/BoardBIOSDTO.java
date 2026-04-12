package com.example.serverprovision.domain.board.dto;

import com.example.serverprovision.domain.board.entity.BoardBIOS;
import lombok.Builder;

/**
 * {@code BoardBIOS} 엔티티의 읽기 전용 DTO이다.
 *
 * <p>역할: BIOS 버전 정보를 뷰 레이어 및 {@code BasicUpdate} 도메인 모델에 전달하는
 * 데이터 전달 객체이다. {@code compatibleModel}로 연결된 {@code BoardModelDTO}를 내포하여
 * BIOS와 메인보드의 호환 관계를 표현한다.</p>
 *
 * <p>유스케이스: {@code BoardModelService#getViewModelList}가 전체 {@code BoardBIOS}를
 * 이 DTO로 변환한 후 {@code BoardOptionSelectView#of}에서 메인보드 모델별로 필터링한다.
 * 세팅 주문서 생성 시 {@code BasicUpdateResolver}가 조회한 엔티티를 {@code from}으로
 * 변환하여 {@code BasicUpdate} 생성자에 전달한다. {@code BasicUpdate} 생성자 내부에서
 * {@code compatibleModel}이 선택된 {@code BoardModelDTO}와 일치하는지 동등성 검사에 사용된다.</p>
 *
 * <p>확장 가이드: {@code BoardBIOS}에 새 필드를 추가할 경우 이 DTO와 {@code from} 메소드,
 * 세팅 주문서 생성 폼의 BIOS 선택 UI도 함께 갱신한다.</p>
 */
@Builder
public record BoardBIOSDTO(
        Long id,
        BoardModelDTO compatibleModel,
        String version,
        String filePath,
        String description,
        Boolean isEnabled
) {
    public static BoardBIOSDTO from(BoardBIOS boardBIOS) {
        return BoardBIOSDTO.builder()
                .id(boardBIOS.getId())
                .compatibleModel(BoardModelDTO.from(boardBIOS.getCompatibleModel()))
                .version(boardBIOS.getVersion())
                .filePath(boardBIOS.getFilePath())
                .description(boardBIOS.getDescription())
                .isEnabled(boardBIOS.isEnabled())
                .build();
    }
}
