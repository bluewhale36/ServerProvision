package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosAttributeControl;
import com.example.serverprovision.provisioning.domain.BiosConditionalInjection;
import com.example.serverprovision.provisioning.domain.BiosControl;
import com.example.serverprovision.provisioning.domain.BiosPage;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.BiosSubmenuControl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 1개 페이지 노드와 문서 순서를 보존한 행 목록. leaf 컨트롤은 레지스트리와 조인해 위젯 행으로,
 * 레지스트리에 없는 orphan 은 방어적으로 건너뛴다 (MS74-HB0 에는 0건).
 */
public record BiosPageResponse(
		String pageId,
		String parentId,
		String title,
		boolean topLevel,
		List<BiosRowResponse> rows
) {

	public static BiosPageResponse of(BiosPage page, BiosSetupMenu menu) {
		List<BiosRowResponse> rows = new ArrayList<>(page.controls().size());
		Set<String> injectedOrphans = new HashSet<>(); // 페이지 내 orphan 중복 주입 방지.
		for (BiosControl control : page.controls()) {
			if (control instanceof BiosAttributeControl attributeControl) {
				BiosAttribute attr = menu.registry().get(attributeControl.name());
				if (attr == null) {
					continue; // orphan — 레지스트리에 없는 leaf 는 화면에 표시하지 않음.
				}
				String currentValue = menu.currentValues().get(attributeControl.name());
				rows.add(BiosWidgetRowResponse.of(attributeControl, attr, currentValue));
				injectConditionalOrphans(rows, attributeControl, menu, injectedOrphans);
			} else if (control instanceof BiosSubmenuControl submenuControl) {
				rows.add(BiosSubmenuRowResponse.of(submenuControl));
			}
		}
		return new BiosPageResponse(page.pageId().hex(), page.parentId().hex(),
				page.title(), page.isTopLevel(), List.copyOf(rows));
	}

	// 예외: controller 위젯 바로 뒤에 조건부 orphan(XML 에 없으나 값에 종속해 활성화되는 속성)을 주입한다.
	private static void injectConditionalOrphans(List<BiosRowResponse> rows, BiosAttributeControl controller,
	                                             BiosSetupMenu menu, Set<String> injectedOrphans) {
		for (BiosConditionalInjection injection : BiosConditionalInjection.after(controller.name())) {
			BiosAttribute orphan = menu.registry().get(injection.orphanAttribute());
			if (orphan == null || !injectedOrphans.add(orphan.name().value())) {
				continue; // 레지스트리에 없거나 이미 같은 페이지에 주입됨 → skip.
			}
			String orphanCurrent = menu.currentValues().get(injection.orphanAttribute());
			rows.add(BiosWidgetRowResponse.conditionalOf(orphan, orphanCurrent, injection));
		}
	}
}
