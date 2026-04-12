package com.example.serverprovision.domain.board.model;

import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import lombok.*;

import java.util.List;

/**
 * 세팅 주문서 생성 폼의 보드 모델 선택 UI에 필요한 복합 뷰 모델이다.
 *
 * <p>역할: {@code BoardModelDTO} 하나와 해당 모델에 호환되는 {@code BoardBIOSDTO} 목록,
 * {@code BoardBMCDTO} 목록을 하나의 뷰 객체로 묶는다. 세팅 주문서 생성 폼에서 보드 모델
 * 선택 셀렉트 변경 시 JS가 이 객체의 BIOS/BMC 목록을 파싱하여 선택 항목을 갱신한다.</p>
 *
 * <p>유스케이스: {@code BoardModelService#getViewModelList}가 전체 보드 모델과 BIOS/BMC를
 * 조회한 뒤 이 클래스의 {@code of} 팩토리 메소드를 사용해 모델별 뷰 객체를 생성한다.
 * Thymeleaf가 {@code th:data-bios="${#json.serialize(view.boardBIOSList)}"}형태로
 * BIOS 목록을 {@code data-*} 속성에 직렬화하고, JS가 셀렉트 변경 이벤트 시 파싱하여
 * UI를 동적으로 갱신한다.</p>
 *
 * <p>확장 가이드: BIOS/BMC 외 추가 호환 정보(예: 네트워크 카드 목록)가 필요해지면
 * 필드를 추가하고 {@code of} 팩토리 메소드에서 필터링 로직을 추가한다. Thymeleaf
 * 템플릿({@code setting/new.html})의 {@code data-*} 직렬화 항목과 JS 파싱 로직도
 * 함께 수정해야 한다.</p>
 */
@Getter
@Builder(access = AccessLevel.PRIVATE)
@ToString
public class BoardOptionSelectView {

    /** 이 뷰가 대표하는 메인보드 모델 DTO이다. */
    private final BoardModelDTO boardModel;
    /** 이 모델과 호환되는 BIOS 버전 목록. {@code BoardBIOSDTO#compatibleModel}으로 필터링된다. */
    private final List<BoardBIOSDTO> boardBIOSList;
    /** 이 모델과 호환되는 BMC 펌웨어 버전 목록. {@code BoardBMCDTO#compatibleModel}으로 필터링된다. */
    private final List<BoardBMCDTO> boardBMCList;

    /**
     * 전체 BIOS/BMC 목록에서 해당 보드 모델에 호환되는 항목만 필터링하여 뷰 객체를 생성한다.
     *
     * @param boardModel      기준이 되는 보드 모델 DTO (non-null)
     * @param boardBIOSList   전체 BIOS DTO 목록 (non-null)
     * @param boardBMCList    전체 BMC DTO 목록 (non-null)
     * @return 해당 모델에 해당하는 BIOS/BMC만 포함된 뷰 객체
     */
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
