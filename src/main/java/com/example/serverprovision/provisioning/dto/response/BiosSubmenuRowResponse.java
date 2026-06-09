package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.provisioning.domain.BiosSubmenuControl;

/**
 * submenu 행. {@code navigable=false} 이면 dead link / self-ref 액션이라 클릭 비활성으로 렌더한다.
 */
public record BiosSubmenuRowResponse(
		String label,
		String helpText,
		String destPageId,
		boolean navigable,
		String complex
) implements BiosRowResponse {

	@Override
	public String kind() {
		return "submenu";
	}

	public static BiosSubmenuRowResponse of(BiosSubmenuControl control) {
		return new BiosSubmenuRowResponse(
				control.label() == null ? "" : control.label(),
				control.helpText(),
				control.dest().hex(),
				control.navigable(),
				control.complex().name()
		);
	}
}
