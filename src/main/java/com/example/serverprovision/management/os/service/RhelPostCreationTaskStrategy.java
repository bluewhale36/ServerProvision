package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.os.entity.ISO;
import com.example.serverprovision.management.os.entity.OSImage;
import com.example.serverprovision.management.os.enums.OSFamily;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S5-7 — Rocky Linux / CentOS ISO 등록 후 comps.xml 자동 추출 트리거.
 *
 * <p>관리자가 ISO 를 등록하면 일반적으로 다음 작업으로 환경·패키지 그룹을 추출하게 된다. 본 strategy 가
 * 그 흐름을 자동화해 사용자 액션 1 단계를 줄인다. ISO_REGISTRATION Job 과 COMPS_EXTRACTION Job 이
 * 알림 패널에 함께 노출되며 D5 정책 A (병렬 실행) 에 따라 동시 진행된다 — COMPS_EXTRACTION 의
 * 첫 단계 (ISO 이미지 준비) 가 파일 존재 / 읽기 가능성을 다시 확인하므로 안전.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RhelPostCreationTaskStrategy implements PostCreationTaskStrategy {

	private final CompsExtractionLauncher compsExtractionLauncher;

	@Override
	public boolean supports(OSFamily family) {
		return family == OSFamily.RHEL_BASED;
	}

	@Override
	public String trigger(OSImage osImage, ISO iso) {
		String jobId = compsExtractionLauncher.startExtraction(osImage.getId(), iso.getId());
		log.info("[S5-7] RHEL ISO 등록 후 comps 추출 자동 트리거. osImageId={}, isoId={}, compsJobId={}",
				osImage.getId(), iso.getId(), jobId);
		return jobId;
	}
}
