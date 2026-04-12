package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;

/**
 * BIOS/BMC 업데이트 단계를 표현하는 {@link AbstractSettingProcess} 구현체이다.
 *
 * <p>역할: PXE 부팅 후 첫 번째로 실행되는 BIOS/BMC 펌웨어 업데이트 단계를 나타낸다.
 * 선택된 {@link BoardModelDTO}, {@link BoardBIOSDTO}, {@link BoardBMCDTO}를 보유하며,
 * 생성 시점에 BIOS/BMC가 BoardModel과 호환되는지 검증한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.BasicUpdateResolver}가
 * {@link com.example.serverprovision.application.setting.model.request.BasicUpdateRequest}로부터
 * 각 엔티티를 조회하고 DTO로 변환한 뒤 이 생성자를 호출한다.
 * {@code boardBIOS.compatibleModel()} 또는 {@code boardBMC.compatibleModel()}이 선택된
 * {@code boardModel}과 불일치하면 {@link IllegalArgumentException}이 발생하며,
 * Resolver가 이를 {@link com.example.serverprovision.global.exception.FieldValidationException}으로 변환한다.
 * 직렬화 시 {@code "type": "BASIC_UPDATES"} 판별자가 JSON에 포함된다.</p>
 *
 * <p>확장 가이드: 새 하드웨어 속성(예: 네트워크 카드 펌웨어)을 추가할 경우 이 클래스에 필드를
 * 추가하고, {@link com.example.serverprovision.application.setting.model.request.BasicUpdateRequest}와
 * {@link com.example.serverprovision.application.setting.service.resolver.BasicUpdateResolver}에도
 * 대응하는 변경을 적용한다. 호환성 검증 조건을 변경할 때는 생성자 내부의 {@code if} 블록을 수정하며,
 * 오류 메시지가 사용자에게 표시되므로 구체적인 원인을 명시하는 것을 권장한다.</p>
 */
@Getter
@ToString
public class BasicUpdate extends AbstractSettingProcess {

    /**
     * 업데이트 대상 메인보드 모델 정보이다.
     * BIOS/BMC와의 호환성 교차 검증 기준이 된다.
     */
    @NotNull(message = "메인보드 모델은 필수 값입니다.")
    private final BoardModelDTO boardModel;

    /**
     * 적용할 BIOS 펌웨어 버전 정보이다.
     * {@code compatibleModel()}이 {@code boardModel}과 일치해야 한다.
     */
    @NotNull(message = "업데이트 할 BIOS 버전은 필수 값입니다.")
    private final BoardBIOSDTO boardBIOS;

    /**
     * 적용할 BMC 펌웨어 버전 정보이다.
     * {@code compatibleModel()}이 {@code boardModel}과 일치해야 한다.
     */
    @NotNull(message = "업데이트 할 BMC 버전은 필수 값입니다.")
    private final BoardBMCDTO boardBMC;

    /**
     * BoardModel-BIOS-BMC 호환성을 검증하고 인스턴스를 생성한다.
     *
     * @param boardModel 대상 메인보드 모델 DTO
     * @param boardBIOS  적용할 BIOS 버전 DTO
     * @param boardBMC   적용할 BMC 버전 DTO
     * @throws IllegalArgumentException {@code boardBIOS} 또는 {@code boardBMC}의
     *         {@code compatibleModel()}이 {@code boardModel}과 불일치할 때
     */
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
