package com.example.serverprovision.management.os.enums;

/**
 * OS 카탈로그에서 상수가 차지하는 자리(S23-1). 지원 상수만 새로 등록할 수 있고, 미지원 상수는 이전할 곳이 없는
 * 옛 행을 읽기 위해서만 남는다(S23-1-a 에서 제거).
 */
public enum OSCatalogStatus {

	SUPPORTED,
	LEGACY
}
