package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.marker.DriftKind;

/**
 * 자동 적용이 거절될 때. 정상 UX 에서는 UI 가 1차 차단(버튼 미노출 / disabled+tooltip)하므로
 * direct POST / stale 화면 안전망에서만 발동한다. → 409
 * <p>R9-2 — 발생 지점이 두 가지라 문구를 factory 로 분리 (과거 "PATH_DRIFT 종류에만 허용" 단일
 * 문구는 실허용 5종과도, 전역 OFF 거절과도 어긋나는 stale 메시지였다):</p>
 * <ul>
 *   <li>{@link #notApplicable(DriftKind)} — {@link DriftKind#isAutoApplicable()} false 인 종류</li>
 *   <li>{@link #globalOff()} — 전역 옵션({@code reconciliation.auto-apply}) 비활성</li>
 * </ul>
 */
public class DriftAutoApplyNotAllowedException extends ConflictException {

	private DriftAutoApplyNotAllowedException(String message) {
		super(message);
	}

	public static DriftAutoApplyNotAllowedException notApplicable(DriftKind kind) {
		return new DriftAutoApplyNotAllowedException(
				"'" + kind.getLabel() + "'(" + kind + ") 종류는 시스템이 자동 정합할 수 없습니다. "
						+ kind.getRecommendedAction()
		);
	}

	public static DriftAutoApplyNotAllowedException globalOff() {
		return new DriftAutoApplyNotAllowedException(
				"전역 자동 적용 옵션(reconciliation.auto-apply)이 꺼져 있어 적용이 거절되었습니다. 옵션을 켠 뒤 다시 시도하세요."
		);
	}
}
