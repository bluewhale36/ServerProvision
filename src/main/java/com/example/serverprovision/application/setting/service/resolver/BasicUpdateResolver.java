package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.BasicUpdate;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.BasicUpdateRequest;
import com.example.serverprovision.domain.board.dto.BoardBIOSDTO;
import com.example.serverprovision.domain.board.dto.BoardBMCDTO;
import com.example.serverprovision.domain.board.dto.BoardModelDTO;
import com.example.serverprovision.domain.board.entity.BoardBIOS;
import com.example.serverprovision.domain.board.entity.BoardBMC;
import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.board.repository.BoardBIOSRepository;
import com.example.serverprovision.domain.board.repository.BoardBMCRepository;
import com.example.serverprovision.domain.board.repository.BoardModelRepository;
import com.example.serverprovision.global.exception.FieldValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link BasicUpdateRequest}를 {@link BasicUpdate} 도메인 모델로 변환하는 Resolver이다.
 *
 * <p>역할: {@link BasicUpdateRequest}에 담긴 {@code boardModelId}, {@code boardBIOSId},
 * {@code boardBMCId}로 각 엔티티를 조회하고 DTO로 변환한 뒤, {@link BasicUpdate} 생성자의
 * BoardModel-BIOS-BMC 호환성 검증을 통과한 도메인 모델을 반환한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService}가
 * {@link BasicUpdateRequest} 타입의 요청을 만나면 이 Resolver를 선택한다.
 * {@link com.example.serverprovision.domain.board.repository.BoardModelRepository},
 * {@link com.example.serverprovision.domain.board.repository.BoardBIOSRepository},
 * {@link com.example.serverprovision.domain.board.repository.BoardBMCRepository}에서
 * 각 ID로 엔티티를 조회하며, 존재하지 않으면 해당 필드에 귀속된
 * {@link com.example.serverprovision.global.exception.FieldValidationException}을 던진다.
 * {@link BasicUpdate} 생성자에서 BIOS/BMC가 선택된 BoardModel과 호환되지 않으면
 * {@link IllegalArgumentException}이 발생하고, 이를 {@code boardBIOSId} 필드의
 * {@link com.example.serverprovision.global.exception.FieldValidationException}으로 변환한다.</p>
 *
 * <p>확장 가이드: 호환성 검증 로직을 변경할 때는 {@link BasicUpdate} 생성자를 수정한다.
 * 이 Resolver 자체에는 DB 조회와 예외 변환 로직만 유지한다.
 * BIOS/BMC 외에 새 하드웨어 속성이 {@link BasicUpdate}에 추가되면 여기서 해당 Repository를
 * 주입하고 동일한 패턴으로 조회·검증을 추가한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BasicUpdateResolver implements SettingProcessResolver {

    private final BoardModelRepository boardModelRepository;
    private final BoardBIOSRepository boardBIOSRepository;
    private final BoardBMCRepository boardBMCRepository;

    @Override
    public boolean supports(AbstractProcessRequest request) {
        return request instanceof BasicUpdateRequest;
    }

    @Override
    public AbstractSettingProcess resolve(AbstractProcessRequest request) {
        BasicUpdateRequest req = (BasicUpdateRequest) request;

        log.info("[BasicUpdateResolver] BasicUpdate ID 조회 시작. boardModelId={}, boardBIOSId={}, boardBMCId={}",
                req.getBoardModelId(), req.getBoardBIOSId(), req.getBoardBMCId());

        // 엔티티 lookup 실패는 각 ID 필드에 귀속되는 에러이므로 FieldValidationException
        // 으로 승급. Service 에서 processList[i]. 프리픽스를 붙여 Bean Validation 경로와
        // 동일한 포맷으로 직렬화된다.
        BoardModel boardModel = boardModelRepository.findById(req.getBoardModelId())
                .orElseThrow(() -> new FieldValidationException("boardModelId",
                        "존재하지 않는 보드 모델입니다. id=" + req.getBoardModelId()));

        BoardBIOS boardBIOS = boardBIOSRepository.findById(req.getBoardBIOSId())
                .orElseThrow(() -> new FieldValidationException("boardBIOSId",
                        "존재하지 않는 BIOS 정보입니다. id=" + req.getBoardBIOSId()));

        BoardBMC boardBMC = boardBMCRepository.findById(req.getBoardBMCId())
                .orElseThrow(() -> new FieldValidationException("boardBMCId",
                        "존재하지 않는 BMC 정보입니다. id=" + req.getBoardBMCId()));

        log.info("[BasicUpdateResolver] DB 조회 완료. boardModel={}, bios={}, bmc={}",
                boardModel.getModelName(), boardBIOS.getVersion(), boardBMC.getVersion());

        BoardModelDTO boardModelDTO = BoardModelDTO.from(boardModel);
        BoardBIOSDTO  boardBIOSDTO  = BoardBIOSDTO.from(boardBIOS);
        BoardBMCDTO   boardBMCDTO   = BoardBMCDTO.from(boardBMC);

        // BasicUpdate 생성자에서 BIOS·BMC 가 선택된 BoardModel 과 호환되는지 검증.
        // 호환성 실패는 BIOS/BMC 중 어느 쪽이 문제인지 단정할 수 없어 두 필드 모두에 표시되도록
        // 상위 컨테이너 필드(스텝 전체)로 매핑하기보다 BIOS 필드에 귀속시킨다 — 사용자 관점에서
        // 대부분 "BIOS 를 잘못 골랐다" 가 원인이고 BMC 는 ID 로 묶여 함께 제약되는 경우가 많다.
        try {
            BasicUpdate basicUpdate = new BasicUpdate(boardModelDTO, boardBIOSDTO, boardBMCDTO);
            log.info("[BasicUpdateResolver] BasicUpdate 도메인 모델 생성 완료 (호환성 검증 통과).");
            return basicUpdate;
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("boardBIOSId", ex.getMessage(), ex);
        }
    }
}
