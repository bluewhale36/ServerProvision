package com.example.serverprovision.domain.board.dto;

import com.example.serverprovision.domain.board.entity.BoardBIOS;
import lombok.Builder;

@Builder
public record BoardBIOSDTO(
        Long id,
        BoardModelDTO compatibleModel,
        String version,
        String filePath,
        String description,
        boolean isEnabled
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
