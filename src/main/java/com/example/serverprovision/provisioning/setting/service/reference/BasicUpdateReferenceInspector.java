package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.management.bios.exception.BiosNotFoundException;
import com.example.serverprovision.management.bios.repository.BiosRepository;
import com.example.serverprovision.management.bmc.exception.BmcNotFoundException;
import com.example.serverprovision.management.bmc.repository.BmcRepository;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.setting.dto.request.AbstractProcessRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * BASIC_UPDATE — 보드/BIOS/BMC selector 참조 검사. 내부 다형 없음(flat — 사용자 확정:
 * 단순 자원 업데이트라 계열 축이 존재하지 않는다).
 */
@Component
@RequiredArgsConstructor
public class BasicUpdateReferenceInspector implements ProcessReferenceInspector {

    private final BoardModelRepository boardModelRepository;
    private final BiosRepository biosRepository;
    private final BmcRepository bmcRepository;

    @Override
    public SettingProcessType target() {
        return SettingProcessType.BASIC_UPDATE;
    }

    @Override
    public void validateReferences(AbstractProcessRequest process, ProcessValidationContext context) {
        BasicUpdateRequest firmware = (BasicUpdateRequest) process;
        if (firmware.getBoardModel().isAuto()) {
            return; // AUTO ⇒ LATEST 강제(@AssertTrue)라 id 참조가 존재하지 않는다.
        }
        Long boardModelId = firmware.getBoardModel().boardModelId();
        var board = boardModelRepository.findByIdAndIsDeletedFalse(boardModelId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardModelId));
        // disabled(effective) 참조 거절 — UI 는 옵션 미렌더(1차 차단), 여기는 direct POST 안전망(409).
        if (!board.isEnabled()) {
            throw new DisabledResourceReferenceException("boardModel", "메인보드 " + board.getModelName());
        }
        if (!firmware.getBios().isLatest()) {
            Long biosId = firmware.getBios().firmwareId();
            // 실존 + 보드 소속 정합을 한 조회로(다른 보드의 펌웨어 id forging 차단).
            var bios = biosRepository.findByIdAndBoardModel_Id(biosId, boardModelId)
                    .filter(b -> !b.isDeleted())
                    .orElseThrow(() -> new BiosNotFoundException(boardModelId, biosId));
            if (!bios.isEnabled()) {
                throw new DisabledResourceReferenceException("bios", "BIOS " + bios.getVersion());
            }
        }
        if (!firmware.getBmc().isLatest()) {
            Long bmcId = firmware.getBmc().firmwareId();
            var bmc = bmcRepository.findByIdAndBoardModel_Id(bmcId, boardModelId)
                    .filter(b -> !b.isDeleted())
                    .orElseThrow(() -> new BmcNotFoundException(boardModelId, bmcId));
            if (!bmc.isEnabled()) {
                throw new DisabledResourceReferenceException("bmc", "BMC " + bmc.getVersion());
            }
        }
    }

    @Override
    public List<String> describeDeprecatedReferences(AbstractProcessRequest process) {
        BasicUpdateRequest firmware = (BasicUpdateRequest) process;
        List<String> names = new ArrayList<>();
        if (firmware.getBoardModel().isAuto()) {
            return names; // AUTO ⇒ LATEST — id 참조 없음.
        }
        boardModelRepository.findByIdAndIsDeletedFalse(firmware.getBoardModel().boardModelId())
                .filter(LifecycleEntity::isDeprecated)
                .ifPresent(b -> names.add("메인보드 " + b.getModelName()));
        if (!firmware.getBios().isLatest()) {
            biosRepository.findById(firmware.getBios().firmwareId())
                    .filter(b -> !b.isDeleted() && b.isDeprecated())
                    .ifPresent(b -> names.add("BIOS " + b.getVersion()));
        }
        if (!firmware.getBmc().isLatest()) {
            bmcRepository.findById(firmware.getBmc().firmwareId())
                    .filter(b -> !b.isDeleted() && b.isDeprecated())
                    .ifPresent(b -> names.add("BMC " + b.getVersion()));
        }
        return names;
    }
}
