package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.maintenance.reconciliation.vo.ScanPopulation;

import java.time.Instant;
import java.util.List;

/**
 * 스캔 1 회 결과 (보고서) 응답. 그 회차가 무엇을 봤는지의 사진이다.
 *
 * <p>MK4-1 — 담기는 것은 관측이고, 관측이 가리키는 문제의 최신 상태가 {@code drifts} 로 실린다.
 * 문제를 해결해도 관측은 남으므로 {@code detectedDriftCount} 와 {@code drifts.size()} 는 항상
 * 일치한다 — 지난 보고서의 건수가 사후에 줄던 현상이 여기서 사라졌다. 지금 남은 미해결 수는
 * 회차가 아니라 현재 상태의 값이라 {@code PathReconciliationService.openDriftCount()} 가 따로 센다.</p>
 *
 * <p>MK4-4-2 — 종전 {@code totalChecked}(활성 자원 수) 자리에 {@link ScanPopulation} 이 들어왔다.
 * 점검이 보는 모집단이 셋인데 하나만 세면 탐지 건수가 점검 대상 수를 넘는 화면이 만들어진다.</p>
 *
 * @param id                 DB PK
 * @param scannedAt          스캔 시작 시각
 * @param scanDuration       스캔 소요시간. ISO-8601 Duration 표기 ({@code "PT0.45S"} 등)
 * @param deep               deep scan 여부. true 면 manifestHash 재계산까지 수행한 결과
 * @param population         MK4-4-2 — 이 회차가 들여다본 세 모집단의 수
 * @param detectedDriftCount HF4-4 — 스캔 시점 탐지 건수 스냅샷 (해결로 감소하지 않음)
 * @param scannedRoots       MK4-4-2 — 이 회차가 뒤진 디렉토리. 도입 이전 회차는 비어 있다
 * @param failedScanRoots    (권고6) walk 실패 root 목록 — 비어있지 않으면 부분 결과 경고
 * @param drifts             드리프트 상세 (C2/C3 표시용)
 */
public record DriftReportResponse(
		Long id,
		Instant scannedAt,
		String scanDuration,
		boolean deep,
		ScanPopulation population,
		int detectedDriftCount,
		List<String> scannedRoots,
		List<String> failedScanRoots,
		List<DriftResponse> drifts
) {

	/**
	 * MK4-4-2 — 점검 범위가 기록되기 전의 회차인가. 화면이 "기록 없음" 과 "한 곳도 뒤지지 않음" 을
	 * 갈라 말할 근거다 — 둘 다 빈 목록이지만 뜻이 전혀 다르다.
	 */
	public boolean scanScopeUnknown() {
		return scannedRoots == null || scannedRoots.isEmpty();
	}
}
