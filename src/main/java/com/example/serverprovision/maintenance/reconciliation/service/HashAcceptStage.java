package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.JobStage;

/**
 * S6-3-4 — 내용 변경 수용 작업의 단계. 지문 재계산과 정본 갱신이 한 흐름이라 1단계.
 */
public enum HashAcceptStage implements JobStage {

	ACCEPTING("지문 재계산·정본 갱신");

	private final String label;

	HashAcceptStage(String label) {
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
