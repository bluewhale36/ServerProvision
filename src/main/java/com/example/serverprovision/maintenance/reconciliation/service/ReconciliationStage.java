package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.JobStage;

/**
 * MK1 스캔 / 마커 재발급 작업의 단계. ordinal 이 chunk 인덱스로 사용됨.
 */
public enum ReconciliationStage implements JobStage {

	SCANNING("디스크 스캔"),
	CLASSIFYING("드리프트 분류"),
	PERSISTING("보고서 저장");

	private final String label;

	ReconciliationStage(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
