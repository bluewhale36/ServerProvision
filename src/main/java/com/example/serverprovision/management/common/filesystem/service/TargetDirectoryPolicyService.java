package com.example.serverprovision.management.common.filesystem.service;

import java.nio.file.Path;

/**
 * BIOS/BMC 번들 업로드 전 target directory 정책 계약.
 * 본체 구현과 feature wiring 은 MA4-1-3 CP4 에서 마무리한다.
 */
public interface TargetDirectoryPolicyService {

	void validateForIntent(Path targetDirectory, boolean allowCreateDirectory);

	void prepareForUpload(Path targetDirectory, boolean allowCreateDirectory);

	/**
	 * 기존 디렉토리 등록 모드 검증. 업로드 없이 이미 콘텐츠가 차 있는 디렉토리를 자원으로 claim 한다.
	 * <ul>
	 *   <li>타겟이 존재하고 디렉토리여야 한다 (없거나 파일이면 거절).</li>
	 *   <li>타겟에 무시 대상이 아닌 콘텐츠가 1 개 이상 있어야 한다 (빈 디렉토리는 거절).</li>
	 *   <li>{@code .provision.json} 마커가 이미 있어선 안 된다 (다른 등록 소유 — 재발급은 reconciliation 흐름).</li>
	 * </ul>
	 */
	void prepareForExistingDirectoryRegistration(Path targetDirectory);
}
