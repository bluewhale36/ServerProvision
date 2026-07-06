package com.example.serverprovision.provisioning.biossetting.service;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.repository.BoardModelRepository;
import com.example.serverprovision.provisioning.biossetting.BiosSettingTemplateUsageChecker;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingBoardCardResponse;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateDetailResponse;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateEditViewResponse;
import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateSummaryResponse;
import com.example.serverprovision.provisioning.biossetting.entity.BiosSettingTemplate;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.provisioning.biossetting.repository.BiosSettingTemplateRepository;
import com.example.serverprovision.provisioning.config.BiosResourceProperties;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.enums.BiosAttributeType;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.dto.response.BiosSetupPageResponse;
import com.example.serverprovision.provisioning.exception.BiosBoardNotFoundException;
import com.example.serverprovision.provisioning.service.BiosRedfishPayloadAssembler;
import com.example.serverprovision.provisioning.service.BiosSetupLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.ObjectMapper;

/**
 * BIOS 세팅 템플릿 조회 — 목록 + 상세(재조인 그룹 + Redfish 프리뷰) + 작성/수정 편집기 뷰모델.
 *
 * <p>보드는 management {@link BoardModel} 실데이터(FK 전환)이며, BIOS 카탈로그(registry/SetupData 파일)
 * 해석 키는 {@code modelName} 이다. 카탈로그 트리는 {@link BiosSetupLoader} 단일 seam,
 * Redfish 조립은 {@link BiosRedfishPayloadAssembler} 를 소비한다.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BiosSettingTemplateQueryService {

    private final BiosSettingTemplateRepository repository;
    private final BiosSettingTemplateUsageChecker usageChecker;
    private final BoardModelRepository boardModelRepository;
    private final BiosResourceProperties biosResourceProperties;
    private final BiosSetupLoader loader;
    private final BiosRedfishPayloadAssembler assembler;
    private final ObjectMapper objectMapper;

    public List<BiosSettingTemplateSummaryResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(t -> BiosSettingTemplateSummaryResponse.from(t, usageChecker.isInUse(t.getId())))
                .toList();
    }

    /**
     * 작성 랜딩의 보드 카드 — BoardModel 실데이터(soft-delete 제외). 카탈로그(파일) 미보유 보드는
     * {@code catalogAvailable=false} 로 내려 UI 가 카드를 disabled(1차 차단)한다 — 판정은
     * 로더의 404 안전망과 같은 카탈로그 존재 확인({@code properties.findBoard}).
     */
    public List<BiosSettingBoardCardResponse> findBoards() {
        return boardModelRepository.findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc().stream()
                .map(board -> new BiosSettingBoardCardResponse(
                        board.getId(),
                        board.getModelName(),
                        board.getVendor().name(),
                        biosResourceProperties.findBoard(board.getModelName()).isPresent()))
                .toList();
    }

    /**
     * 템플릿 작성 편집기 뷰모델 — PASSWORD 등 비-템플릿 타입은 {@code templatable()} 판정으로
     * 뷰모델에서 제외한다(위젯 미출력 = 구조적 UI 차단, 서버 400 안전망과 단일 SSOT).
     */
    public BiosSetupPageResponse editorView(Long boardModelId) {
        BoardModel board = boardModelRepository.findByIdAndIsDeletedFalse(boardModelId)
                .orElseThrow(() -> new BoardModelNotFoundException(boardModelId));
        return editorView(board.getModelName(), Map.of());
    }

    /** 수정 편집기 뷰모델 — 생성과 동일 화면에 저장값 overlay(위젯 선택값만, diff 기준선 불변)를 주입. */
    public BiosSettingTemplateEditViewResponse editorViewFor(Long id) {
        BiosSettingTemplate template = findTemplate(id);
        // 카탈로그 미보유 보드는 상세의 catalogMissing 판정이 수정 버튼을 disabled 하므로(UI 1차 차단),
        // 이 로더 호출의 404 는 direct GET 안전망으로만 발동한다.
        BiosSetupPageResponse bios = editorView(template.getBoardModel().getModelName(), toFormValues(template));
        return new BiosSettingTemplateEditViewResponse(
                template.getId(), template.getName(), template.getDescription(),
                template.getBoardModel().getId(), template.getBoardModel().getModelName(), bios);
    }

    /**
     * 상세 뷰모델 — 저장 flat 값을 registry 와 재조인해 MenuPath 그룹으로 정리하고,
     * 실행 시 전송될 Redfish Request Body 를 함께 조립한다(조회 시점 조립 — 저장 안 함).
     */
    public BiosSettingTemplateDetailResponse findDetail(Long id) {
        BiosSettingTemplate template = findTemplate(id);
        BiosSetupMenu menu = loadCatalogOrNull(template.getBoardModel().getModelName());

        List<BiosSettingTemplateDetailResponse.StaleEntry> stale = new ArrayList<>();
        // MenuPath → entries (첫 등장 순서 보존).
        Map<String, List<BiosSettingTemplateDetailResponse.Entry>> groups = new LinkedHashMap<>();
        boolean resetRequired = false;

        for (Map.Entry<BiosAttributeName, BiosAttributeValue> e : template.getValues().entries().entrySet()) {
            String raw = String.valueOf(e.getValue().jsonValue());
            BiosAttribute attr = menu == null ? null : menu.registry().get(e.getKey());
            if (attr == null) {
                // 카탈로그 미보유(전건) 또는 BIOS 개정으로 사라진 속성(개별) — 경고 행으로 분리, raw 노출.
                stale.add(new BiosSettingTemplateDetailResponse.StaleEntry(e.getKey().value(), raw));
                continue;
            }
            groups.computeIfAbsent(groupPath(attr), k -> new ArrayList<>())
                    .add(new BiosSettingTemplateDetailResponse.Entry(
                            attr.name().value(),
                            attr.trimmedDisplayName(),
                            attr.type().widgetKind(),
                            displayOf(attr, attr.defaultValue()),
                            attr.defaultValue(),
                            displayOf(attr, raw),
                            raw,
                            attr.resetRequired()));
            resetRequired |= attr.resetRequired();
        }

        return new BiosSettingTemplateDetailResponse(
                template.getId(), template.getName(), template.getDescription(),
                template.getBoardModel().getId(), template.getBoardModel().getModelName(),
                usageChecker.isInUse(template.getId()),
                template.getCreatedAt(), template.getUpdatedAt(),
                menu == null,
                groups.entrySet().stream()
                        .map(g -> new BiosSettingTemplateDetailResponse.Group(g.getKey(), List.copyOf(g.getValue())))
                        .toList(),
                List.copyOf(stale),
                buildRedfishPreview(template, resetRequired));
    }

    /* ─────────────────────────── 내부 조립 ─────────────────────────── */

    private BiosSettingTemplate findTemplate(Long id) {
        return repository.findById(id).orElseThrow(() -> new BiosSettingTemplateNotFoundException(id));
    }

    private BiosSetupPageResponse editorView(String catalogKey, Map<BiosAttributeName, String> storedValues) {
        return BiosSetupPageResponse.of(
                loader.load(catalogKey),
                attr -> attr.type().templatable(),
                storedValues);
    }

    // 보드의 카탈로그(파일)가 미등록인 경우만 degraded(null) — 리소스 파일 파손은 기존 방침대로 500 전파.
    private BiosSetupMenu loadCatalogOrNull(String catalogKey) {
        try {
            return loader.load(catalogKey);
        } catch (BiosBoardNotFoundException e) {
            return null;
        }
    }

    private Map<BiosAttributeName, String> toFormValues(BiosSettingTemplate template) {
        Map<BiosAttributeName, String> form = new LinkedHashMap<>();
        template.getValues().entries().forEach((name, value) -> form.put(name, String.valueOf(value.jsonValue())));
        return form;
    }

    // MenuPath 가 비어있는 속성(이론상 예외)은 보드 최상위로 묶는다.
    private String groupPath(BiosAttribute attr) {
        return attr.menuPath() == null || attr.menuPath().isBlank() ? "(경로 미지정)" : attr.menuPath();
    }

    // ENUMERATION 은 옵션 표시명(valueDisplayName)으로, 그 외는 원시 값 그대로.
    private String displayOf(BiosAttribute attr, String raw) {
        if (raw == null) {
            return null;
        }
        if (attr.type() == BiosAttributeType.ENUMERATION) {
            for (BiosEnumOption option : attr.options()) {
                if (option.valueName().equals(raw)) {
                    return option.valueDisplayName();
                }
            }
        }
        return raw;
    }

    private BiosSettingTemplateDetailResponse.RedfishPreview buildRedfishPreview(
            BiosSettingTemplate template, boolean resetRequired) {
        Map<String, Object> attributes = assembler.assembleAttributes(template.getValues().entries());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Attributes", attributes);
        return new BiosSettingTemplateDetailResponse.RedfishPreview(
                BiosRedfishPayloadAssembler.BIOS_SETTINGS_METHOD,
                BiosRedfishPayloadAssembler.BIOS_SETTINGS_TARGET,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body),
                resetRequired,
                BiosRedfishPayloadAssembler.RESET_TARGET,
                "{\"ResetType\": \"" + BiosRedfishPayloadAssembler.RESET_TYPE + "\"}");
    }
}
