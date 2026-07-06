package com.example.serverprovision.provisioning.setting.dto.response;

import java.util.List;

/**
 * OS 선택지의 OS 유형(OSName 표시명) 그룹 — 폼 select 의 {@code <optgroup>} 1개에 대응한다.
 * 같은 OS 의 버전들이 한 그룹에 묶인다(예: "Rocky Linux" ← 9.4 / 9.3).
 */
public record SettingOSOptionGroupResponse(
        String osLabel,
        List<SettingOSOptionResponse> osList
) {
}
