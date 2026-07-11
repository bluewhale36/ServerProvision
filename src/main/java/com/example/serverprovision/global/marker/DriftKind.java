package com.example.serverprovision.global.marker;

import lombok.Getter;

/**
 * 자원 무결성 점검에서 감지되는 드리프트 종류.
 * <p>MK3 — Trash 패턴 도입으로 lifecycle / FS 정합화. 자동 적용 가능 분기는 운영자 의도가 단일화되는 경우만.
 * 이중 의도 검증 명제 — drift 가 lifecycle + path 두 변화 동시 포함하면 자동 제외.</p>
 *
 * <p>R9-2 — 사용자 노출 문구({@code label}/{@code description}/{@code recommendedAction})와
 * 해결 방식({@code mode} — S6-2-1 에서 {@link DriftResolutionMode} 3단으로 확장)을 순수 데이터 필드로 보유한다
 * ({@link IntegrityStatus#getDisplayMessage()} 선례). 서버 가드({@code PathReconciliationService.apply})와
 * 템플릿(배지 색 / apply 폼 노출)이 모두 이 enum 을 단일 소스로 참조한다 — 과거 템플릿 4종 하드코딩 ×3곳과
 * 서비스 5종 나열이 갈라져 있던 SSOT 위반의 해소. 이 문구 표는 추후 사용자 가이드의 원문이기도 하다.</p>
 *
 * <h3>MK1 (기존, 탐지 구현됨)</h3>
 * <ul>
 *   <li>{@code PATH_DRIFT} — DB.path 위치 마커 부재. 다른 active 위치에 (resourceType, resourceId) 마커 발견. 본체도 함께. <b>자동 ON</b></li>
 *   <li>{@code MISSING} — DB.path + 어디에도 매칭 마커 없음. 자원 분실. 자동 OFF</li>
 *   <li>{@code ORPHAN} — 마커는 디스크에 있지만 DB 매칭 자원 없음. 자동 OFF</li>
 *   <li>{@code SIGNATURE_INVALID} — HMAC 서명 깨짐. 변조 의심. 자동 OFF</li>
 *   <li>{@code HASH_MISMATCH} — deep scan 시 manifest 해시 불일치. 자동 OFF</li>
 * </ul>
 *
 * <h3>MK3 신규 (탐지 미구현 — S6-2 가 탐지 + 시스템 해결을 함께 구현 예정)</h3>
 * <ul>
 *   <li>{@code SOFTDEL_ESCAPE_TO_ORIGINAL} — DB.is_deleted=true, trash 부재, 원래 경로에 자원 복귀. <b>자동 ON</b></li>
 *   <li>{@code SOFTDEL_ESCAPE_TO_OTHER} — DB.is_deleted=true, trash 부재, active 트리의 다른 위치에서 자원+마커 발견. 의도 모호 — 사용자 확인 후 재격리 예정 (S6-2-2)</li>
 *   <li>{@code TRASH_LOST} — DB.is_deleted=true, DB.trashed_path 위치 부재. 외부 정리 의심 — 사용자 확인 후 row 정리 예정 (S6-2-3)</li>
 *   <li>{@code TRASH_MARKER_STALE} — trash 자원 옆에 마커 잔존 (soft-delete 시 정리됐어야 함). <b>자동 ON</b></li>
 * </ul>
 *
 * <p>S6-1 — {@code RESOURCE_RENAMED} / {@code RESOURCE_RENAMED_ORPHAN} 은 삭제했다 (재도입 금지 근거):
 * 파일+마커 동시 리네임은 디스크 마커 수집이 (resourceType, resourceId) 키 매칭이라 파일명과 무관하게
 * PATH_DRIFT 로 이미 감지·적용되고, 파일명 단독 변경(마커 잔존)은 4a 본체 존재 검사가 quick scan 에서
 * MISSING 으로 보고한다. 두 값 모두 탐지 로직이 생성한 적 없어 영속 이력 충돌도 없다.</p>
 *
 * <h3>MK3-1 신규 (탐지 구현됨)</h3>
 * <ul>
 *   <li>{@code GHOST_DB_ROW} — DB row 는 소프트삭제 상태이지만 FS 에 자원도 trash 도 없는 dead row.
 *   정의 : {@code is_deleted=true AND trashed_at=null AND trashed_path=null AND Files.notExists(DB.path)}.
 *   drift apply = DB row hard-delete. <b>자동 ON</b> (R9-2 — 서버 가드는 종전부터 허용, UI 만 4종 하드코딩으로 숨겨져 있던 것을 SSOT 승격으로 노출)</li>
 * </ul>
 */
@Getter
public enum DriftKind {

	PATH_DRIFT(
			"경로 이동됨",
			"자원 파일과 마커가 다른 위치에서 온전히 발견되었습니다. DB 가 옛 경로를 가리키고 있습니다.",
			"적용하면 DB 의 경로를 새 위치로 갱신합니다. 파일은 변경하지 않습니다.",
			DriftResolutionMode.AUTO,
			false
	),
	MISSING(
			"자원 소실",
			"DB 에 등록된 자원의 파일과 마커를 어디에서도 찾지 못했습니다.",
			"백업·이동·이름 변경 여부를 확인해 파일을 원래 경로·이름으로 복원한 뒤 다시 점검하세요. 시스템이 자동 복구할 수 없습니다.",
			DriftResolutionMode.NONE,
			true
	),
	ORPHAN(
			"미등록 마커",
			"디스크에 마커가 있으나 DB 에 매칭되는 자원이 없습니다.",
			"필요한 자원이면 관리 화면에서 다시 등록하고, 불필요하면 적용으로 격리 보관 구역에 회수합니다 (삭제가 아니라 이동 — 실물은 보존됩니다).",
			DriftResolutionMode.MANUAL,
			true
	),
	SIGNATURE_INVALID(
			"마커 서명 불일치",
			"마커 파일의 서명이 현재 서명 키와 맞지 않습니다. 마커 손상·외부 이식 또는 서명 키(secret) 변경이 원인일 수 있습니다.",
			"손상·이식이 원인임을 확인했다면 적용으로 이 자원의 마커만 재서명합니다 (내용 지문은 유지 — 실제 변조라면 다음 정밀 점검에서 드러납니다). 키 교체 직후의 일괄 발생이면 관리 도구의 '마커 서명 재발급'이 빠릅니다.",
			DriftResolutionMode.MANUAL,
			true
	),
	HASH_MISMATCH(
			"내용 변경 감지",
			"자원 파일의 내용이 등록 시점과 다릅니다 (정밀 점검에서만 감지).",
			"원본을 다시 업로드하는 것이 가장 안전합니다 (검증 재통과). 의도된 교체가 확실하면 자원명 확인 후 현재 내용을 정본으로 수용할 수 있습니다 (감사 기록에 남음).",
			DriftResolutionMode.NONE,
			false
	),
	// MK3 신규 — 탐지 미구현(S6-2 소관), 문구 선부여
	SOFTDEL_ESCAPE_TO_ORIGINAL(
			"삭제 자원 복귀",
			"휴지통에 있어야 할 자원이 원래 위치로 돌아와 있습니다.",
			"적용하면 자원을 복원해 DB 상태를 실제 위치에 맞게 정합합니다.",
			DriftResolutionMode.AUTO,
			false
	),
	SOFTDEL_ESCAPE_TO_OTHER(
			"삭제 자원 위치 이탈",
			"휴지통에 있어야 할 자원이 예상 밖의 위치에서 발견되었습니다. 운영자 의도를 알 수 없습니다.",
			"적용하면 발견 위치의 자원을 휴지통으로 회수합니다. 다시 쓸 자원이면 휴지통에서 복원하세요.",
			DriftResolutionMode.MANUAL,
			false
	),
	TRASH_LOST(
			"휴지통 자원 소실",
			"휴지통으로 이동된 자원이 그 위치에 없습니다. 외부에서 정리되었을 수 있습니다.",
			"적용하면 남은 기록을 정리하고 감사 기록(휴지통 정리 이력)에 남깁니다. 복구가 필요했던 자원이면 백업을 확인하세요.",
			DriftResolutionMode.MANUAL,
			false
	),
	TRASH_MARKER_STALE(
			"잔여 마커 정리 필요",
			"휴지통 자원 옆에 소프트 삭제 시 정리됐어야 할 마커가 남아 있습니다.",
			"적용하면 잔여 마커를 정리합니다.",
			DriftResolutionMode.AUTO,
			false
	),
	// MK3-1 신규
	GHOST_DB_ROW(
			"유령 DB 기록",
			"삭제 표시된 자원이 휴지통에도 디스크에도 없습니다. DB 기록만 남아 있습니다.",
			"적용하면 남은 DB 기록을 영구 삭제합니다. 복구할 파일이 없어 안전한 정리입니다.",
			DriftResolutionMode.AUTO,
			false
	);

	/**
	 * 사용자 노출 한 줄 명칭. 템플릿 배지·확인 문구가 사용한다. enum 원시명은 상세 화면의 보조 표기로만.
	 */
	private final String label;

	/**
	 * "무슨 일이 벌어졌나" — 처음 보는 사용자 기준 설명.
	 */
	private final String description;

	/**
	 * "무엇을 하면 되나" — 자동 적용 가능 종류는 적용의 실제 효과를, 불가 종류는 수동 조치를 안내.
	 */
	private final String recommendedAction;

	/**
	 * S6-2-1 — 시스템 해결 방식 3단 ({@link DriftResolutionMode}). 서버 가드·템플릿(버튼 노출·배지 색)·
	 * 스캔 무인 적용 자격이 공유하는 단일 소스. 전역 옵션({@code reconciliation.resolution-enabled})과는
	 * 별개 축 — 전역 OFF 는 허용 종류도 거절한다.
	 */
	private final DriftResolutionMode mode;

	/**
	 * S6-3-3 — [다시 점검](그 자원 하나만 즉시 재확인) 지원 여부. 해결 등급(mode)과 <b>독립인 별도 축</b> —
	 * 확인 액션이라 mode 승격과 무관하게 유지된다. 판정만 갱신하고 상태는 바꾸지 않으며,
	 * 해소 시 보고서에서 카드 제거만 한다(Drift 불변 설계 유지 — 재분류는 전체 점검의 몫).
	 * HASH_MISMATCH 는 재확인에 내용 지문 재계산(대용량 수 분)이 필요해 제외 — S6-3-4 의
	 * 백그라운드 배선과 함께 재검토.
	 */
	private final boolean recheckable;

	DriftKind(String label, String description, String recommendedAction, DriftResolutionMode mode,
			boolean recheckable) {
		this.label = label;
		this.description = description;
		this.recommendedAction = recommendedAction;
		this.mode = mode;
		this.recheckable = recheckable;
	}

	/**
	 * 스캔 무인 자동 적용 자격 (mode == AUTO). 실제 발동은 {@code reconciliation.auto-apply.kinds} 옵트인.
	 */
	public boolean isAutoApplicable() {
		return mode == DriftResolutionMode.AUTO;
	}

	/**
	 * [적용] 버튼 노출 + 수동 apply 가드 기준 (mode != NONE) — MANUAL 은 사용자 확인 후 시스템이 해결.
	 */
	public boolean isManuallyResolvable() {
		return mode != DriftResolutionMode.NONE;
	}
}
