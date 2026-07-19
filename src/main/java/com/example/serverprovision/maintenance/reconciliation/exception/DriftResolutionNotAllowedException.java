package com.example.serverprovision.maintenance.reconciliation.exception;

import com.example.serverprovision.global.exception.ConflictException;
import com.example.serverprovision.global.marker.DriftKind;

/**
 * drift 의 시스템 해결이 거절될 때. 정상 UX 에서는 UI 가 1차 차단(버튼 미노출 / disabled+tooltip)하므로
 * direct POST / stale 화면 안전망에서만 발동한다. → 409
 *
 * <p>S6-2-1 — {@code DriftAutoApplyNotAllowedException} 에서 개명. mode 3단(NONE/MANUAL/AUTO) 도입으로
 * "자동 적용" 어휘가 수동(MANUAL) 해결 거절까지 덮지 못하게 되어 설정 키
 * ({@code reconciliation.resolution-enabled})·가드·문구와 함께 "시스템 해결" 어휘로 정합했다.
 * 발생 지점 3곳이 factory 2종으로 수렴한다:</p>
 * <ul>
 *   <li>{@link #notApplicable(DriftKind)} — {@link DriftKind#isManuallyResolvable()} false 인 종류,
 *       또는 해결 로직({@code DriftResolution} bean) 미등록 kind (forced 도 우회 불가)</li>
 *   <li>{@link #globalOff()} — 전역 옵션({@code reconciliation.resolution-enabled}) 비활성</li>
 * </ul>
 */
public class DriftResolutionNotAllowedException extends ConflictException {

	private DriftResolutionNotAllowedException(String message) {
		super(message);
	}

	public static DriftResolutionNotAllowedException notApplicable(DriftKind kind) {
		return new DriftResolutionNotAllowedException(
				"'" + kind.getLabel() + "'(" + kind + ") 종류는 시스템이 해결할 수 없습니다. "
						+ kind.getRecommendedAction()
		);
	}

	/**
	 * S6-2-2 — 재격리(위치 이탈 회수) 시 휴지통에 기존 사본이 살아 있는 경우. 그대로 회수하면
	 * 기록이 새 파일만 가리켜 기존 사본이 참조를 잃는다(휴지통은 스캔 범위 밖 — 영구 미발견).
	 */
	public static DriftResolutionNotAllowedException trashCopyConflict(String trashedPath) {
		return new DriftResolutionNotAllowedException(
				"휴지통에 이미 이 자원의 사본이 남아 있어 회수를 진행할 수 없습니다. "
						+ "휴지통의 기존 사본을 확인해 정리한 뒤 다시 점검하세요. (기존 사본 : " + trashedPath + ")"
		);
	}

	/**
	 * S6-2-2 — 회수 시점에 발견 위치의 파일이 이미 사라진 경우(stale 화면 / 외부 재이동 / 마커만
	 * 발견된 상태). 동반 마커를 지우기 전에 검증해 비가역 정리와 mv 실패 500 을 막는다.
	 */
	public static DriftResolutionNotAllowedException escapedFileMissing(String foundPath) {
		return new DriftResolutionNotAllowedException(
				"발견 위치에 파일이 더 이상 없습니다. 상태가 바뀐 것 같으니 다시 점검한 뒤 진행하세요. (발견 위치 : "
						+ foundPath + ")"
		);
	}

	/**
	 * S6-2-3 — 점검 보고(스냅샷)와 실제 상태가 달라져 이 보고로는 해결을 진행할 수 없는 경우.
	 * 해결 bean 이 실행 직전 재검증에서 사용 (stale 화면 / 상태 재변화 안전망).
	 */
	public static DriftResolutionNotAllowedException staleState() {
		return new DriftResolutionNotAllowedException(
				"이미 상태가 바뀌어 이 보고로는 처리할 수 없습니다. 다시 점검한 뒤 새 보고에서 진행하세요."
		);
	}

	/**
	 * S6-3-1 — 마커가 파싱 불가 수준으로 손상되어 재서명(기존 내용 유지 + 서명만 교체)이 불가능한 경우.
	 * 마커 재구성은 자원 정보 합성이 필요한 다른 등급의 작업이라 본 액션의 범위 밖.
	 */
	public static DriftResolutionNotAllowedException markerUnreadable() {
		return new DriftResolutionNotAllowedException(
				"마커가 심하게 손상되어 재서명할 수 없습니다. 파일 경위를 확인하거나 자원을 다시 등록하세요."
		);
	}

	/**
	 * HF4-5 — 복제본 유지(정본 승격)가 안전 가드에 걸려 거절된 경우. 서명 무효 · 지문 불일치(낡은 사본 —
	 * O-2 위험의 본질) · 원본/사본 포함 관계가 해당한다. 원본 유지(사본 삭제) 갈래에는 적용되지 않는다.
	 */
	public static DriftResolutionNotAllowedException duplicateNotPromotable(String reason) {
		return new DriftResolutionNotAllowedException(
				"복제본을 정본으로 승격할 수 없습니다 — " + reason + ". 상태를 확인한 뒤 다시 점검하세요."
		);
	}

	/**
	 * S6-3-4 — 같은 카드의 수용 작업이 이미 진행 중인 경우 (중복 시작 차단).
	 */
	public static DriftResolutionNotAllowedException acceptInProgress() {
		return new DriftResolutionNotAllowedException(
				"이 자원의 수용 작업이 이미 진행 중입니다. 완료 알림을 기다려 주세요."
		);
	}

	public static DriftResolutionNotAllowedException globalOff() {
		return new DriftResolutionNotAllowedException(
				"시스템 해결 옵션(reconciliation.resolution-enabled)이 꺼져 있어 거절되었습니다. 옵션을 켠 뒤 다시 시도하세요."
		);
	}
}
