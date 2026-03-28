package com.example.serverprovision.domain.board.model;

import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import lombok.*;

import java.util.List;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@ToString
public class BoardOptionSelectView {

    private final BoardModelDTO boardModel;
    private final List<BoardBIOSDTO> boardBIOSList;
    private final List<BoardBMCDTO> boardBMCList;

    public static BoardOptionSelectView of(
            @NonNull BoardModelDTO boardModel,
            @NonNull List<BoardBIOSDTO> boardBIOSList,
            @NonNull List<BoardBMCDTO> boardBMCList
    ) {

        return BoardOptionSelectView.builder()
                .boardModel(boardModel)
                .boardBIOSList(
                        boardBIOSList.stream().filter(bios -> bios.compatibleModel().equals(boardModel)).toList()
                )
                .boardBMCList(
                        boardBMCList.stream().filter(bmc -> bmc.compatibleModel().equals(boardModel)).toList()
                )
                .build();
    }
}
