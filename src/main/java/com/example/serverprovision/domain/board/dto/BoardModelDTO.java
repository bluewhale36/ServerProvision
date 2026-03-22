package com.example.serverprovision.domain.board.dto;

import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.node.model.enums.Vendor;
import lombok.Builder;

@Builder
public record BoardModelDTO(
        Long id,
        Vendor vendor,
        String modelName,
        String description,
        boolean isEnabled
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
