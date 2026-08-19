package com.example.serverprovision.provisioning.setting.dto.response;

import java.util.List;

/** RAID 카드 선택지의 제조사 그룹 — {@code <optgroup>} 단위 ({@link SettingBoardOptionGroupResponse} 관례, U4-1-1). */
public record SettingRaidCardOptionGroupResponse(String vendorDisplay, List<SettingRaidCardOptionResponse> cards) {
}
