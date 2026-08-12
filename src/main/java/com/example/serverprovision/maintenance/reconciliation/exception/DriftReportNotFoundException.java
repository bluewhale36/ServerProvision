package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * MK4-4-2 — 점검 회차를 찾지 못함. 보관 개수를 넘겨 정리됐거나(FIFO prune) 잘못된 ID 입력. → 404
 *
 * <p>정상 흐름에서는 목록의 행을 눌러 들어오므로 도달하지 않는다. 이 예외가 뜨는 경로는 주소창
 * 직접 입력과, 목록을 열어 둔 채 그 회차가 정리된 뒤 누르는 경우다 — 둘 다 UI 가 미리 막을 수 없는
 * 진짜 비정상이라 예외가 맞다.</p>
 */
public class DriftReportNotFoundException extends NotFoundException {

	public DriftReportNotFoundException(Long reportId) {
		super("점검 회차를 찾을 수 없습니다 : " + reportId);
	}
}
