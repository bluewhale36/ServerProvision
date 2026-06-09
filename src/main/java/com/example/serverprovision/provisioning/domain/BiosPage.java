package com.example.serverprovision.provisioning.domain;

import com.example.serverprovision.provisioning.domain.vo.PageId;

import java.util.List;

/**
 * 1개 페이지 노드. 컨트롤 목록은 XML 문서 순서를 보존한다 (leaf/submenu 가 의미 있게 교차 배치됨).
 */
public record BiosPage(
		PageId pageId,
		PageId parentId,
		String title,
		String pageFlags,
		List<BiosControl> controls
) {

	public boolean isTopLevel() {
		return parentId.isRoot();
	}

	public boolean isEmpty() {
		return controls.isEmpty();
	}
}
