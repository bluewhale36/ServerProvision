package com.example.serverprovision.management.os.dto.response;

/**
 * 설치 환경·패키지 그룹을 제공한 ISO 한 건의 뷰 모델.
 * 동일 OSImage 범위 안에서도 osVersion + isoPath 를 같이 노출하는 것이 리스트 판독성을 높인다.
 */
public record IsoProvisionView(
		Long isoId,
		String osVersion,
		String isoPath
) {

}
