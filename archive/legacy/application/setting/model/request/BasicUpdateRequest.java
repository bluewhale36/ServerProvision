package com.example.serverprovision.application.setting.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * BIOS/BMC 업데이트 단계에 대한 프론트엔드 요청 DTO이다.
 *
 * <p>역할: 관리자가 폼에서 선택한 BoardModel, BIOS, BMC의 DB 식별자를 담아 전송하는
 * Request 객체이다. {@code "type": "BASIC_UPDATES"}로 Jackson 다형성 역직렬화에 사용된다.</p>
 *
 * <p>유스케이스: {@code POST /pxe/v1/setting/api/new} 요청의 {@code processList} 항목 중
 * {@code "type": "BASIC_UPDATES"}에 해당하는 항목으로 역직렬화된다.
 * {@link com.example.serverprovision.application.setting.service.resolver.BasicUpdateResolver}가
 * 이 Request를 받아 각 ID로 DB에서 엔티티를 조회하고
 * {@link com.example.serverprovision.application.setting.model.BasicUpdate} 도메인 모델을 생성한다.</p>
 *
 * <p>확장 가이드: 새 하드웨어 속성이 {@link com.example.serverprovision.application.setting.model.BasicUpdate}에
 * 추가되면 이 클래스에도 동일한 ID 필드를 추가하고 {@code @JsonCreator} 생성자에 파라미터를 추가한다.
 * 폼 UI에도 해당 선택 항목을 추가해야 한다.</p>
 */
@Getter
public class BasicUpdateRequest extends AbstractProcessRequest {

    /** 대상 메인보드 모델의 DB 기본키이다. {@code board_model.id}에 해당한다. */
    @NotNull(message = "보드 모델 ID는 필수 값입니다.")
    private final Long boardModelId;

    /** 적용할 BIOS 펌웨어 버전의 DB 기본키이다. {@code board_bios.id}에 해당한다. */
    @NotNull(message = "BIOS ID는 필수 값입니다.")
    private final Long boardBIOSId;

    /** 적용할 BMC 펌웨어 버전의 DB 기본키이다. {@code board_bmc.id}에 해당한다. */
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
