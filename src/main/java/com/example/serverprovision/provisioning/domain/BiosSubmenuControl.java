package com.example.serverprovision.provisioning.domain;

import com.example.serverprovision.provisioning.domain.enums.BiosComplexHint;
import com.example.serverprovision.provisioning.domain.vo.PageId;

/**
 * submenu / action control — 하위 페이지로의 네비게이션 링크.
 * <p>라벨은 Control 의 {@code DisplayName}(원본 그대로; PageTitle 과 다를 수 있음), 목적지는 {@code ControlDestPageID}.
 * {@code navigable} 은 (dest 가 실존 페이지 && dest != 자기 페이지) 로 계산되어, dead link(목적지 페이지 없음)와
 * self-ref BIOS 액션 항목(예: "Restore Factory Keys")을 한 boolean 으로 흡수한다.</p>
 */
public record BiosSubmenuControl(
		PageId dest,
		String label,
		String helpText,
		String refGuid,
		BiosComplexHint complex,
		boolean navigable
) implements BiosControl {
}
