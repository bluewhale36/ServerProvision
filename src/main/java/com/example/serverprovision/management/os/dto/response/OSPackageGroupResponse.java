package com.example.serverprovision.management.os.dto.response;

import java.util.List;

/**
 * 패키지 그룹 응답 DTO.
 * Step 4 에서 Service 층의 정식 factory 와 연결될 예정.
 */
public record OSPackageGroupResponse(
		Long id,
		String groupCode,
		String displayName,
		String description,
		boolean isDefault,
		List<IsoProvisionView> providers
) {

}
