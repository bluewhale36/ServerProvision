package com.example.serverprovision.domain.board.service;

import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import com.example.serverprovision.domain.board.entity.BoardBIOS;
import com.example.serverprovision.domain.board.entity.BoardBMC;
import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.board.model.BoardOptionSelectView;
import com.example.serverprovision.domain.board.repository.BoardBIOSRepository;
import com.example.serverprovision.domain.board.repository.BoardBMCRepository;
import com.example.serverprovision.domain.board.repository.BoardModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 메인보드 모델 및 BIOS/BMC 정보를 조회하는 서비스이다.
 *
 * <p>역할: {@code BoardModelRepository}, {@code BoardBIOSRepository}, {@code BoardBMCRepository}를
 * 통해 보드 관련 엔티티를 조회하고, 뷰 레이어 또는 세팅 주문서 생성 폼에 필요한 형태로
 * 변환하여 제공한다.</p>
 *
 * <p>유스케이스: {@code SettingController#newSetting}이 {@code getViewModelList}를 호출하여
 * 세팅 주문서 생성 폼에 전달할 {@code BoardOptionSelectView} 목록을 가져온다. 이 목록은
 * Thymeleaf를 통해 {@code data-bios}, {@code data-bmc} 속성으로 JSON 직렬화되어
 * JS의 셀렉트 연동에 사용된다. {@code AdminController#settings}는 {@code getAllActiveBoardModel}을
 * 통해 노드 설정 화면의 활성 보드 모델 선택 목록을 구성한다.</p>
 *
 * <p>확장 가이드: BIOS/BMC 외 추가 호환 옵션(예: NIC 펌웨어) 조회가 필요해지면 해당
 * Repository를 이 서비스에 추가하고 {@code getViewModelList} 반환 타입인
 * {@code BoardOptionSelectView}에도 필드를 추가한다. 세팅 주문서 폼과 Thymeleaf
 * 직렬화 코드도 함께 수정해야 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BoardModelService {

    private final BoardModelRepository boardModelRepository;
    private final BoardBIOSRepository boardBIOSRepository;
    private final BoardBMCRepository boardBMCRepository;

    /**
     * 활성/비활성 구분 없이 전체 보드 모델 목록을 DTO로 반환한다.
     *
     * @return 전체 {@code BoardModelDTO} 목록
     */
    public List<BoardModelDTO> getAllBoardModel() {
        return boardModelRepository.findAll()
                .stream()
                .map(BoardModelDTO::from)
                .toList();
    }

    /**
     * 활성화된({@code isEnabled = true}) 보드 모델 목록만 DTO로 반환한다.
     *
     * @return 활성 {@code BoardModelDTO} 목록
     */
    public List<BoardModelDTO> getAllActiveBoardModel() {
        return boardModelRepository.findAllByEnabledIsTrue()
                .stream()
                .map(BoardModelDTO::from)
                .toList();
    }

    /**
     * 전체 보드 모델과 호환되는 BIOS/BMC 목록을 조합하여 세팅 주문서 생성 폼용
     * 뷰 모델 목록을 반환한다.
     *
     * <p>각 보드 모델별로 호환되는 BIOS/BMC만 필터링하여 {@code BoardOptionSelectView}를
     * 구성한다. 결과 목록은 Thymeleaf 템플릿에서 {@code data-*} 속성에 직렬화된다.</p>
     *
     * @return 보드 모델별 BIOS/BMC 뷰 모델 목록
     */
    public List<BoardOptionSelectView> getViewModelList() {
        List<BoardModelDTO> boardList = getAllBoardModel();
        List<BoardBIOSDTO> biosList = boardBIOSRepository.findAll().stream().map(BoardBIOSDTO::from).toList();
        List<BoardBMCDTO> bmcList = boardBMCRepository.findAll().stream().map(BoardBMCDTO::from).toList();

        List<BoardOptionSelectView> list = boardList.stream()
                .map(
                        board -> BoardOptionSelectView.of(board, biosList, bmcList)
                )
                .toList();
        list.forEach(System.out::println);

        return list;
    }
}
