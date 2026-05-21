package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 보고서에서 해당 driftId 행을 찾지 못함. 보고서 prune 또는 잘못된 ID 입력. → 404
 */
public class DriftNotFoundException extends NotFoundException {

	public DriftNotFoundException(Long driftId) {
		super("드리프트를 찾을 수 없습니다 : " + driftId);
	}
}
