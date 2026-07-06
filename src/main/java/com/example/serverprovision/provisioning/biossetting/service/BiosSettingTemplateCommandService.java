package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.biossetting.BiosSettingTemplateUsageChecker;
import com.example.serverprovision.provisioning.biossetting.dto.request.BiosSettingTemplateCreateRequest;
import com.example.serverprovision.provisioning.biossetting.dto.request.BiosSettingTemplateUpdateRequest;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateSummaryResponse;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateInUseException;
import com.example.serverprovision.provisioning.biossetting.exception.DuplicateBiosSettingTemplateNameException;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.biossetting.vo.BiosSettingValues;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.exception.InvalidBiosValueException;
import com.example.serverprovision.provisioning.exception.UnknownBiosAttributeException;
import com.example.serverprovision.provisioning.service.BiosSetupLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BIOS 세팅 템플릿 쓰기. 검증·coerce 루프는 구 무상태 PoC({@code BiosSetupService.save})에서
 * <b>이동</b>해 왔다 — PoC 저장 경로는 같은 슬라이스에서 폐지되어 소비자가 이곳 하나다(중복 0).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class BiosSettingTemplateCommandService {

    private final BiosSettingTemplateRepository repository;
    private final BoardModelRepository boardModelRepository;
    private final BiosSetupLoader loader;
    private final BiosSettingTemplateUsageChecker usageChecker;

    public BiosSettingTemplateSummaryResponse create(BiosSettingTemplateCreateRequest request) {
        if (repository.existsByName(request.name().trim())) {
            throw new DuplicateBiosSettingTemplateNameException(request.name().trim());
        }
        // 보드는 management 실데이터(FK) — soft-delete 보드로는 생성 불가(404).
        BoardModel board = boardModelRepository.findByIdAndIsDeletedFalse(request.boardModelId())
                .orElseThrow(() -> new BoardModelNotFoundException(request.boardModelId()));
        // registry 로 전건 재검증 — 카탈로그 미보유 보드는 로더가 BiosBoardNotFoundException(404, UI 차단의 안전망).
        BiosSetupMenu menu = loader.load(board.getModelName());
        BiosSettingValues values = validateAndCoerce(menu, request.attributes());

        BiosSettingTemplate saved = repository.save(BiosSettingTemplate.builder()
                .name(request.name().trim())
                .description(request.description())
                .boardModel(board)
                .values(values)
                .build());
        return BiosSettingTemplateSummaryResponse.from(saved, false); // 방금 생성 — 사용처 없음
    }

    /** 수정 — boardKey 불변, 검증·coerce 는 생성과 동일 루프 재사용, name 중복은 자기 제외. */
    public BiosSettingTemplateSummaryResponse update(Long id, BiosSettingTemplateUpdateRequest request) {
        BiosSettingTemplate template = repository.findById(id)
                .orElseThrow(() -> new BiosSettingTemplateNotFoundException(id));
        String name = request.name().trim();
        if (repository.existsByNameAndIdNot(name, id)) {
            throw new DuplicateBiosSettingTemplateNameException(name);
        }
        BiosSetupMenu menu = loader.load(template.getBoardModel().getModelName());
        BiosSettingValues values = validateAndCoerce(menu, request.attributes());

        template.update(name, request.description(), values);
        return BiosSettingTemplateSummaryResponse.from(template, usageChecker.isInUse(template.getId()));
    }

    /** 삭제 — hard delete. 사용중(guest 정의서 참조) 차단 가드는 참조가 생기는 U2-2-3 에서 도입한다. */
    public void delete(Long id) {
        // 사용중 삭제 차단(U2-2-3 D4) — UI disabled 의 direct DELETE 안전망(DB RESTRICT 는 레이스 방어).
        if (usageChecker.isInUse(id)) {
            throw new BiosSettingTemplateInUseException(id);
        }
        BiosSettingTemplate template = repository.findById(id)
                .orElseThrow(() -> new BiosSettingTemplateNotFoundException(id));
        repository.delete(template);
    }

    /**
     * 변경쌍 검증·coerce — 타입 판정은 전부 {@code BiosAttributeType} 상수별 다형에 위임한다.
     * readOnly 는 스킵(UI 차단의 direct POST 안전망), 비-템플릿 타입(PASSWORD)은 400 거절.
     */
    private BiosSettingValues validateAndCoerce(BiosSetupMenu menu, Map<String, String> attributes) {
        Map<BiosAttributeName, BiosAttributeValue> coerced = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw InvalidBiosValueException.blankKey();
            }
            BiosAttributeName name = BiosAttributeName.of(key);
            BiosAttribute attr = menu.attribute(name)
                    .orElseThrow(() -> new UnknownBiosAttributeException(name));

            if (attr.readOnly()) {
                continue; // 안전망 — UI 가 수집하지 않지만 direct POST 대비.
            }
            if (!attr.type().templatable()) {
                throw InvalidBiosValueException.notTemplatable(name);
            }
            attr.type().validate(attr, entry.getValue());
            coerced.put(name, attr.type().coerce(attr, entry.getValue()));
        }
        if (coerced.isEmpty()) {
            throw InvalidBiosValueException.emptyDiff();
        }
        return new BiosSettingValues(coerced);
    }
}
