package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class BasicUpdate extends AbstractSettingProcess {

    @NotNull(message = "메인보드 모델은 필수 값입니다.")
    private final BoardModelDTO boardModel;
    @NotNull(message = "업데이트 할 BIOS 버전은 필수 값입니다.")
    private final BoardBIOSDTO boardBIOS;
    @NotNull(message = "업데이트 할 BMC 버전은 필수 값입니다.")
    private final BoardBMCDTO boardBMC;

    public BasicUpdate(BoardModelDTO boardModel, BoardBIOSDTO boardBIOS, BoardBMCDTO boardBMC) {
        super(SettingProcessStep.BASIC_UPDATE);

        if (
                !boardBIOS.compatibleModel().equals(boardModel) ||
                !boardBMC.compatibleModel().equals(boardModel)
        ) {
            throw new IllegalArgumentException("BoardModel 과 호환되지 않는 BoardBIOS 또는 BoardBMC 가 제공되었습니다.");
        }

        this.boardModel = boardModel;
        this.boardBIOS = boardBIOS;
        this.boardBMC = boardBMC;
    }
}
