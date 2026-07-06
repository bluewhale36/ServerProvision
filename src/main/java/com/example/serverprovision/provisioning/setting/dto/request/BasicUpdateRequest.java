package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 펌웨어 업데이트 단계 요청 ({@code "type": "BASIC_UPDATE"}).
 *
 * <p>보드는 자동 감지({@code AUTO}) 또는 직접 지정, BIOS/BMC 는 최신 버전({@code LATEST}) 또는
 * 직접 지정을 선택한다. 계약은 의도만 운반하며, 감지·최신 해석은 execution 도메인(Stage 4)이 수행한다.</p>
 */
@Getter
public class BasicUpdateRequest extends AbstractProcessRequest {

    @NotNull(message = "보드 모델 선택은 필수 값입니다.")
    @Valid
    private final BoardModelSelectionRequest boardModel;

    @NotNull(message = "BIOS 선택은 필수 값입니다.")
    @Valid
    private final FirmwareSelectionRequest bios;

    @NotNull(message = "BMC 선택은 필수 값입니다.")
    @Valid
    private final FirmwareSelectionRequest bmc;

    @JsonCreator
    public BasicUpdateRequest(
            @JsonProperty("boardModel") BoardModelSelectionRequest boardModel,
            @JsonProperty("bios")       FirmwareSelectionRequest bios,
            @JsonProperty("bmc")        FirmwareSelectionRequest bmc
    ) {
        this.boardModel = boardModel;
        this.bios       = bios;
        this.bmc        = bmc;
    }

    /**
     * 보드 자동 감지 ⇒ BIOS/BMC 는 최신 버전만 허용 — 감지될 보드를 모르는 채 특정 펌웨어 id 를
     * 지정하는 모순을 막는다. <b>이 판정이 단일 SSOT</b>: UI 는 같은 규칙으로 펌웨어 select 를
     * '최신 버전' 고정 + disabled 처리(1차 차단)하고, 본 가드는 direct POST 안전망으로만 발동한다.
     */
    @AssertTrue(message = "보드 모델 자동 감지 시 BIOS/BMC 는 최신 버전만 선택할 수 있습니다.")
    public boolean isFirmwareSelectionCoherent() {
        if (boardModel == null || bios == null || bmc == null) return true;  // @NotNull 위반이 이미 보고된다.
        if (!boardModel.isAuto()) return true;
        return bios.isLatest() && bmc.isLatest();
    }

    @Override
    public SettingProcessType processType() {
        return SettingProcessType.BASIC_UPDATE;
    }
}
