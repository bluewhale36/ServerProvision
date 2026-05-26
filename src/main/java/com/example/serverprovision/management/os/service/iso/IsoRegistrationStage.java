package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.global.job.JobStage;

/**
 * ISO 등록 후처리 파이프라인 단계.
 * 파일 업로드 바이트 수신은 foreground 요청에서 끝나고, 이후 무거운 작업만 background 로 돈다.
 */
public enum IsoRegistrationStage implements JobStage {

	COMPUTE_HASH("해시 계산"),
	CHECK_DUPLICATE("중복 검사"),
	PERSIST_METADATA("메타데이터 저장");

	private final String label;

	IsoRegistrationStage(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
