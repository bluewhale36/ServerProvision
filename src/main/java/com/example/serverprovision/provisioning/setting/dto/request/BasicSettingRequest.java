package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;

/**
 * BIOS 설정 단계 요청 ({@code "type": "BASIC_SETTING"}) — BIOS 세팅 템플릿 참조로 실체화(U2-2-3).
 *
 * <p>자체 보드 selector 를 갖는다(사용자 확정 2026-07-07): BIOS 설정만 있는 정의서도 보드 의도를
 * 명시할 수 있어야 하기 때문. BASIC_UPDATE 와 공존하면 두 selector 는 동일해야 하며
 * ({@link SettingSaveRequest} Layer A + UI 고정 동기화), 템플릿 규칙(SPECIFIED ⇒ 그 보드 1개 /
 * AUTO ⇒ 보드당 1개씩 N개 — 실행 시 감지 보드 일치 1개 적용·나머지 skip)의 판정 기준은 이
 * 자체 selector 다.</p>
 */
@Getter
public class BasicSettingRequest extends AbstractProcessRequest {

    @NotNull(message = "메인보드 선택 정보는 필수 값입니다.")
    @Valid
    private final BoardModelSelectionRequest boardModel;

    @NotEmpty(message = "BIOS 세팅 템플릿을 1개 이상 선택해야 합니다.")
    private final List<Long> biosSettingTemplateIds;

    /** 편의 생성자 — 자동 감지 + 템플릿 목록(테스트·프로그램 조립용). */
    public BasicSettingRequest(List<Long> biosSettingTemplateIds) {
        this(new BoardModelSelectionRequest(
                com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode.AUTO, null),
                biosSettingTemplateIds);
    }

    @JsonCreator
    public BasicSettingRequest(
            @JsonProperty("boardModel")             BoardModelSelectionRequest boardModel,
            @JsonProperty("biosSettingTemplateIds") List<Long> biosSettingTemplateIds
    ) {
        this.boardModel = boardModel;
        this.biosSettingTemplateIds = biosSettingTemplateIds != null ? biosSettingTemplateIds : List.of();
    }

    @AssertTrue(message = "같은 BIOS 세팅 템플릿을 중복 선택할 수 없습니다.")
    public boolean isTemplateIdsUnique() {
        return biosSettingTemplateIds.size() == new HashSet<>(biosSettingTemplateIds).size();
    }

    @Override
    public SettingProcessType processType() {
        return SettingProcessType.BASIC_SETTING;
    }

    @Override
    public List<Long> referencedBiosSettingTemplateIds() {
        return biosSettingTemplateIds;
    }
}
