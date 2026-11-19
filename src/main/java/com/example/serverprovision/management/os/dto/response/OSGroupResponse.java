package com.example.serverprovision.management.os.dto.response;

import com.example.serverprovision.management.os.enums.OSName;

import java.util.List;

/**
 * Miller Columns 의 C1(OS 이름) + C2(버전 목록) 데이터 단위. 같은 OS 상수의 메타데이터를 한 그룹으로 묶는다.
 * {@code label} 은 C1 에 보이는 이름("Rocky Linux 9"), {@code displayName} 은 버전과 이어 쓰는 배포판 이름("Rocky Linux")이다.
 * {@code unsupported} 는 옛 행을 읽기 위해서만 남은 미지원 상수의 그룹이다(S23-1).
 */
public record OSGroupResponse(
		OSName osName,
		String displayName,
		String label,
		boolean unsupported,
		List<OSMetadataResponse> items
) {

	public static OSGroupResponse of(OSName osName, List<OSMetadataResponse> items) {
		return new OSGroupResponse(osName, osName.getDisplayName(), osName.label(), !osName.registrable(), items);
	}
}
