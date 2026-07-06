package com.example.serverprovision.provisioning.setting.dto.response;

import java.util.List;

/**
 * 보드 선택지의 제조사(Vendor) 그룹 — 폼 select 의 {@code <optgroup>} 1개에 대응한다.
 * (사용자 지시 2026-07-05: 보드/OS 선택지는 그룹핑하여 표시)
 */
public record SettingBoardOptionGroupResponse(
        String vendor,
        List<SettingBoardOptionResponse> boards
) {
}
