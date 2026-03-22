package com.example.serverprovision.domain.board.dto;

import com.example.serverprovision.domain.board.entity.BoardBMC;
import lombok.Builder;

@Builder
public record BoardBMCDTO(
        Long id,
        BoardModelDTO compatibleModel,
        String version,
        String filePath,
        String description,
        boolean isEnabled
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
