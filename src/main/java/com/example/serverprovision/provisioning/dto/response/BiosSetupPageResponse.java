package com.example.serverprovision.provisioning.dto.response;

import com.example.serverprovision.provisioning.domain.BiosSetupMenu;

import java.util.List;

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

	public static BiosSetupPageResponse of(BiosSetupMenu menu) {
		List<BiosMenuTabResponse> tabs = menu.menuBar().stream()
				.map(p -> new BiosMenuTabResponse(p.pageId().hex(), p.title()))
				.toList();
		List<BiosPageResponse> pages = menu.pages().values().stream()
				.map(p -> BiosPageResponse.of(p, menu))
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
