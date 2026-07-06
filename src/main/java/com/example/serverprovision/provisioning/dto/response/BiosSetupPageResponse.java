package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.domain.BiosSetupMenu;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * BIOS 셋업 전체 화면 뷰모델: 보드 + 메뉴바 + 모든 페이지(클라이언트 토글) + 의존성(클라이언트 동적 가시성용).
 */
public record BiosSetupPageResponse(
		String boardKey,
		List<BiosMenuTabResponse> menuBar,
		List<BiosPageResponse> pages,
		List<DependencyView> dependencies
) {

	/** 클라이언트 의존성 엔진이 소비하는 평면 뷰. */
	public record DependencyView(String fromAttribute, String fromValue, String toAttribute, String toProperty) {
	}

	/**
	 * 속성 필터 + 저장값 overlay 포함 조립.
	 * <ul>
	 *   <li>{@code include} — 템플릿 편집기는 {@code attr.type().templatable()} 판정으로 PASSWORD 위젯을
	 *       뷰모델에서 아예 내보내지 않는다(구조적 UI 차단, 서버 400 안전망과 같은 SSOT).</li>
	 *   <li>{@code storedValues} — edit pre-fill: 위젯 선택값만 저장값으로 세팅(diff 기준선 불변). 생성은 빈 맵.</li>
	 * </ul>
	 */
	public static BiosSetupPageResponse of(BiosSetupMenu menu, Predicate<BiosAttribute> include,
	                                       Map<BiosAttributeName, String> storedValues) {
		List<BiosMenuTabResponse> tabs = menu.menuBar().stream()
				.map(p -> new BiosMenuTabResponse(p.pageId().hex(), p.title()))
				.toList();
		List<BiosPageResponse> pages = menu.pages().values().stream()
				.map(p -> BiosPageResponse.of(p, menu, include, storedValues))
				.toList();
		List<DependencyView> deps = menu.dependencies().stream()
				.map(d -> new DependencyView(
						d.fromAttribute().value(),
						d.fromValueRaw(),
						d.toAttribute().value(),
						d.toProperty().name()))
				.toList();
		return new BiosSetupPageResponse(menu.boardKey(), tabs, pages, deps);
	}
}
