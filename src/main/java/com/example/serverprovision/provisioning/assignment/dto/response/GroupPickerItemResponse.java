package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;

/**
 * 그룹 일괄 할당 모달의 한 항목 (U3-5-c) — 좌측 목록 한 줄과 그것을 고르면 열리는 우측 패널 한 벌.
 *
 * <p>U3-5-b 의 {@code DefinitionPickerItemResponse} 와 모양이 닮았지만 <b>가운데 것이 다르다</b>.
 * 단건은 "붙는가/안 붙는가" 하나이고, 그룹은 <b>멤버마다 결과가 갈리는 미리보기</b>다. 그래서
 * {@code blockReason} 하나를 재사용하지 않고 {@link GroupApplyPreviewResponse} 를 싣는다 — 부분 적용
 * (10 대 중 8 대)을 {@code blockReason} 에 담으면 {@code blocked()} 가 참이 되어 이름이 거짓말을 한다.</p>
 */
public record GroupPickerItemResponse(
        GroupApplyPreviewResponse preview,
        SettingDetailResponse detail
) {

    public Long id() {
        return preview.definitionId();
    }

    public String name() {
        return preview.definitionName();
    }

    /** 아무에게도 붙지 않는가 — 좌측이 잠긴 모양이 되고 확정 버튼이 열리지 않는다. */
    public boolean blocked() {
        return preview.blocked();
    }

    public int willAssignCount() {
        return preview.willAssignCount();
    }

    public boolean deprecated() {
        return preview.deprecated();
    }
}
