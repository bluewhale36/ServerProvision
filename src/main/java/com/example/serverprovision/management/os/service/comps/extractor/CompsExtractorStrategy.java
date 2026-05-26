package com.example.serverprovision.management.os.service.comps.extractor;

import com.example.serverprovision.management.os.service.iso.IsoPreparationService;

import com.example.serverprovision.management.os.enums.OSName;

/**
 * OS 계열별 환경·패키지 그룹 추출 전략.
 * 새 OS 계열 지원이 필요하면 이 인터페이스를 구현하는 Spring 컴포넌트를 추가하면 된다.
 */
public interface CompsExtractorStrategy {

	/**
	 * 주어진 OSName 이 이 전략이 지원하는 계열에 속하는지 여부.
	 */
	boolean supports(OSName osName);

	/**
	 * 전략 고유 로직으로 comps.xml 을 찾아 파싱한다.
	 *
	 * @param preparedPath {@code IsoPreparationService} 가 마운트/해제 후 제공한 탐색 가능 경로 (로컬 디렉토리 또는 HTTP URL)
	 */
	CompsExtractionResult extract(String preparedPath);
}
