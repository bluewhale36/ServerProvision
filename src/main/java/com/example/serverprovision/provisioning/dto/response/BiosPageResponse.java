package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosAttributeControl;
import com.example.serverprovision.provisioning.domain.BiosConditionalInjection;
import com.example.serverprovision.provisioning.domain.BiosControl;
import com.example.serverprovision.provisioning.domain.BiosPage;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.BiosSubmenuControl;

import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

	/**
	 * @param storedValues edit pre-fill 용 저장값 overlay (AttributeName → 폼 문자열). 생성 화면은 빈 맵.
	 */
	public static BiosPageResponse of(BiosPage page, BiosSetupMenu menu, Predicate<BiosAttribute> include,
	                                  Map<BiosAttributeName, String> storedValues) {
		List<BiosRowResponse> rows = new ArrayList<>(page.controls().size());
		Set<String> injectedOrphans = new HashSet<>(); // 페이지 내 orphan 중복 주입 방지.
		for (BiosControl control : page.controls()) {
			if (control instanceof BiosAttributeControl attributeControl) {
				BiosAttribute attr = menu.registry().get(attributeControl.name());
				if (attr == null || !include.test(attr)) {
					continue; // orphan(레지스트리 부재) 또는 필터 제외(예: 템플릿 편집기의 PASSWORD) — 미표시.
				}
				rows.add(BiosWidgetRowResponse.of(attributeControl, attr, storedValues.get(attributeControl.name())));
				injectConditionalOrphans(rows, attributeControl, menu, injectedOrphans, include, storedValues);
			} else if (control instanceof BiosSubmenuControl submenuControl) {
				rows.add(BiosSubmenuRowResponse.of(submenuControl));
			}
		}
		return new BiosPageResponse(page.pageId().hex(), page.parentId().hex(),
				page.title(), page.isTopLevel(), List.copyOf(rows));
	}

	// 예외: controller 위젯 바로 뒤에 조건부 orphan(XML 에 없으나 값에 종속해 활성화되는 속성)을 주입한다.
	private static void injectConditionalOrphans(List<BiosRowResponse> rows, BiosAttributeControl controller,
	                                             BiosSetupMenu menu, Set<String> injectedOrphans,
	                                             Predicate<BiosAttribute> include,
	                                             Map<BiosAttributeName, String> storedValues) {
		for (BiosConditionalInjection injection : BiosConditionalInjection.after(controller.name())) {
			BiosAttribute orphan = menu.registry().get(injection.orphanAttribute());
			if (orphan == null || !include.test(orphan) || !injectedOrphans.add(orphan.name().value())) {
				continue; // 레지스트리에 없거나 필터 제외거나 이미 같은 페이지에 주입됨 → skip.
			}
			rows.add(BiosWidgetRowResponse.conditionalOf(orphan, storedValues.get(injection.orphanAttribute()), injection));
		}
	}
}
