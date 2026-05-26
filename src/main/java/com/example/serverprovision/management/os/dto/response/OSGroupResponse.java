package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.os.enums.OSName;

import java.util.List;

/**
 * Miller Columns 의 C1(OS 이름) + C2(버전 목록) 데이터 단위.
 * 같은 OSName 을 가진 OS 메타데이터들을 묶어 뷰에서 그룹 단위로 렌더한다.
 */
public record OSGroupResponse(
		OSName osName,
		String displayName,
		List<OSMetadataResponse> items
) {

	public static OSGroupResponse of(OSName osName, List<OSMetadataResponse> items) {
		return new OSGroupResponse(osName, osName.getDisplayName(), items);
	}
}
