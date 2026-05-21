package com.example.serverprovision.global.marker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 마커가 부착될 수 있는 자원 종류. 각 상수는 default {@link MarkerLayout} 을 보유한다.
 * <ul>
 *   <li>{@code BIOS_BUNDLE} — BIOS 번들 디렉토리. IN_TREE 마커.</li>
 *   <li>{@code OS_ISO} — OS ISO 단일 파일. SIDECAR 마커.</li>
 *   <li>{@code BMC_FIRMWARE} — BMC 펌웨어 번들 디렉토리 (MA4). IN_TREE 마커.</li>
 *   <li>{@code SUBPROGRAM} — Driver / Utility 통합 번들 디렉토리 (MA5). IN_TREE 마커. kind 는 마커 attributes 로 분기.</li>
 * </ul>
 * <p>새 자원 종류 추가 시 본 enum 에 상수 + 도메인의 {@code MarkableScanner} 구현 1 종을 함께 등록한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ResourceType {
	BIOS_BUNDLE(MarkerLayout.IN_TREE, false, "BIOS 파일"),
	OS_ISO(MarkerLayout.SIDECAR, false, "OS ISO"),
	BMC_FIRMWARE(MarkerLayout.IN_TREE, false, "BMC 파일"),
	SUBPROGRAM(MarkerLayout.IN_TREE, false, "Driver/Utility"),
	// S5-2-3+ — 메타 자원 (디렉토리/파일 없음). layout 은 형식상 placeholder.
	// marker / reconciliation / trash 이동 흐름과 무관 — lifecycle 메타 (is_deleted / trashed_at) 만 활용.
	OS_IMAGE(MarkerLayout.SIDECAR, true, "OS 버전"),
	BOARD_MODEL(MarkerLayout.SIDECAR, true, "메인보드 모델");

	private final MarkerLayout defaultLayout;
	/**
	 * true 면 디렉토리/파일 없는 메타 자원 — 마커 미발급, trash 이동 미수행.
	 */
	private final boolean metadata;
	/**
	 * S5-2-4 — UI 표시용 사용자 친화 이름. select option / 테이블 셀에 사용.
	 */
	private final String displayName;
}
