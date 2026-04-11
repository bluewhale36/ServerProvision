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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link BasicUpdateRequest} → {@link BasicUpdate} 해석.
 *
 * <p>선택된 BoardModel, BoardBIOS, BoardBMC 엔티티를 ID 로 조회하고,
 * {@link BasicUpdate} 생성자 내부에서 BIOS/BMC 가 선택된 BoardModel 과
 * 호환되는지 검증한다.
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

        BoardModel boardModel = boardModelRepository.findById(req.getBoardModelId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 보드 모델입니다. id=" + req.getBoardModelId()));

        BoardBIOS boardBIOS = boardBIOSRepository.findById(req.getBoardBIOSId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 BIOS 정보입니다. id=" + req.getBoardBIOSId()));

        BoardBMC boardBMC = boardBMCRepository.findById(req.getBoardBMCId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 BMC 정보입니다. id=" + req.getBoardBMCId()));

        log.info("[BasicUpdateResolver] DB 조회 완료. boardModel={}, bios={}, bmc={}",
                boardModel.getModelName(), boardBIOS.getVersion(), boardBMC.getVersion());

        BoardModelDTO boardModelDTO = BoardModelDTO.from(boardModel);
        BoardBIOSDTO  boardBIOSDTO  = BoardBIOSDTO.from(boardBIOS);
        BoardBMCDTO   boardBMCDTO   = BoardBMCDTO.from(boardBMC);

        // BasicUpdate 생성자에서 BIOS·BMC 가 선택된 BoardModel 과 호환되는지 검증
        BasicUpdate basicUpdate = new BasicUpdate(boardModelDTO, boardBIOSDTO, boardBMCDTO);
        log.info("[BasicUpdateResolver] BasicUpdate 도메인 모델 생성 완료 (호환성 검증 통과).");

        return basicUpdate;
    }
}
