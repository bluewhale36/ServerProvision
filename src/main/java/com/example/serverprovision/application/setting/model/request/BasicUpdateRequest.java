package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class BasicUpdateRequest extends AbstractProcessRequest {

    @NotNull(message = "보드 모델 ID는 필수 값입니다.")
    private final Long boardModelId;

    @NotNull(message = "BIOS ID는 필수 값입니다.")
    private final Long boardBIOSId;

    @NotNull(message = "BMC ID는 필수 값입니다.")
    private final Long boardBMCId;

    @JsonCreator
    public BasicUpdateRequest(
            @JsonProperty("boardModelId") Long boardModelId,
            @JsonProperty("boardBIOSId")  Long boardBIOSId,
            @JsonProperty("boardBMCId")   Long boardBMCId
    ) {
        this.boardModelId = boardModelId;
        this.boardBIOSId  = boardBIOSId;
        this.boardBMCId   = boardBMCId;
    }
}
