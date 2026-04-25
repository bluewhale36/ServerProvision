package com.example.serverprovision.management.board.dto.response;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.enums.Vendor;

/**
 * 메인보드 모델 단일 응답. Miller Columns 의 C2 요약 + C3 상세를 한 타입으로 서빙.
 * A3/A4/A5 합류 뒤 호환 펌웨어/드라이버 개수 필드를 확장할 수 있다.
 */
public record BoardModelResponse(
        Long id,
        Vendor vendor,
        String modelName,
        String description,
        boolean isEnabled,
        boolean isDeleted
) {
    public static BoardModelResponse of(BoardModel entity) {
        return new BoardModelResponse(
                entity.getId(),
                entity.getVendor(),
                entity.getModelName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.isDeleted()
        );
    }
}
