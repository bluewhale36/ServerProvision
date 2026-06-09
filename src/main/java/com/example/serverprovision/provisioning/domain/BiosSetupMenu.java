package com.example.serverprovision.provisioning.domain;

import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.PageId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 파싱 완료된 불변 BIOS 셋업 모델 (aggregate root). 4 Platform 병합 결과인 전역 페이지 맵 + 메뉴바 순서 +
 * 레지스트리 맵 + 의존성을 담는다. GET 렌더와 POST 검증의 단일 소스(SSOT) — 같은 레지스트리 맵이 위젯 기본값과
 * 서버 값 인식을 함께 구동하므로 둘이 드리프트할 수 없다.
 *
 * @param menuBarOrder  최상위 탭(PageParentID=0x0) 의 문서 순서
 * @param pages         PageId → 페이지 (문서 순서 보존 LinkedHashMap)
 * @param registry      AttributeName → 속성 메타
 * @param currentValues AttributeName → 보드 실제 현재값(initial_settings). 비어있으면 레지스트리 기본값으로 표시.
 */
public record BiosSetupMenu(
		String boardKey,
		List<PageId> menuBarOrder,
		Map<PageId, BiosPage> pages,
		Map<BiosAttributeName, BiosAttribute> registry,
		List<BiosDependency> dependencies,
		Map<BiosAttributeName, String> currentValues
) {

	public Optional<BiosAttribute> attribute(BiosAttributeName name) {
		return Optional.ofNullable(registry.get(name));
	}

	public Optional<BiosPage> page(PageId id) {
		return Optional.ofNullable(pages.get(id));
	}

	/** 메뉴바 탭에 해당하는 페이지들(순서 보존). */
	public List<BiosPage> menuBar() {
		return menuBarOrder.stream()
				.map(pages::get)
				.filter(Objects::nonNull)
				.toList();
	}
}
