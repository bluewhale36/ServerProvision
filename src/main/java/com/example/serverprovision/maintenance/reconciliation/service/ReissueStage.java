package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.JobStage;

/**
 * R9-1 — 마커 서명 재발급 작업의 단계. 재발급은 전 자원 순회-재서명 단일 작업이라 1단계.
 * <p>기존에는 스캔용 {@link ReconciliationStage} 3단계("디스크 스캔/드리프트 분류/보고서 저장")를
 * 차용 등록해 알림 센터 진행바가 존재하지 않는 단계를 표시했다 — 그 결함의 수정.</p>
 */
public enum ReissueStage implements JobStage {

	RESIGNING("마커 재서명");

	private final String label;

	ReissueStage(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
