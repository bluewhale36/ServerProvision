package com.example.serverprovision.global.marker;

import lombok.Getter;

/**
 * 자원 무결성 점검에서 감지되는 드리프트 종류.
 * <p>MK3 — Trash 패턴 도입으로 lifecycle / FS 정합화. 자동 적용 가능 분기는 운영자 의도가 단일화되는 경우만.
 * 이중 의도 검증 명제 — drift 가 lifecycle + path 두 변화 동시 포함하면 자동 제외.</p>
 *
 * <p>R9-2 — 사용자 노출 문구({@code label}·{@code description}·{@code resolveAction}·{@code operatorAction})와
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
 *
 * <h3>HF4-5 신규 (탐지 구현됨)</h3>
 * <ul>
 *   <li>{@code RESOURCE_REPLICA} — 원본(DB.path)이 완전 정상인데 동일 (resourceType, resourceId) 마커와
 *   본체가 다른 위치에서도 발견됨. 방치하면 원본 유실 시 그 사본이 PATH_DRIFT 로 오인되어 정본 자리를
 *   차지할 수 있다 (2026-07-19 전수 점검 O-2). 해결은 택일 입력(survivor)을 동반하므로 표준 [적용] 이 아닌
 *   전용 endpoint ({@code DuplicateResolveService}) — HASH_MISMATCH 선례와 같은 mode=NONE.</li>
 * </ul>
 *
 * <h3>S11-1 신규 (탐지 구현됨)</h3>
 * <ul>
 *   <li>{@code SOFTDEL_MARKER_STRAY} — 삭제 자원의 마커가 <b>본체 없이</b> 활성 트리에서 발견됨.
 *   판정 원칙(마커 = 신원 증명, 본체 = 존재 증명)에 따라 자원 상태 판정(복귀 · 소실 · 유령)을 대체하지
 *   못하며, 그 판정과 <b>병행 보고</b>되는 독립 신호다({@code TRASH_MARKER_STALE} 의 활성 트리 판).
 *   종전에는 이 상태가 {@code SOFTDEL_ESCAPE_TO_OTHER} 의 catch-all 로 흡수되어 원 판정을 억제하고
 *   거짓 {@code SCAN_UNOBSERVED} 해결 이력을 만들었다. <b>자동 ON</b> — 마커는 재발급 가능해 파괴 비용이 없다.</li>
 * </ul>
 */
@Getter
public enum DriftKind {

	PATH_DRIFT(
			"경로 이동됨",
			"자원 파일과 마커가 다른 위치에서 온전히 발견되었습니다. DB 가 옛 경로를 가리키고 있습니다.",
			"데이터베이스의 경로를 현재 실제 파일의 경로로 갱신합니다. 파일은 건드리지 않습니다.",
			null,
			DriftResolutionMode.AUTO,
			DriftResolveEntry.STANDARD,
			false,
			false,
			true
	),
	MISSING(
			"자원 소실",
			"DB 에 등록된 자원의 파일과 마커를 어디에서도 찾지 못했습니다.",
			// HF4-4 (O-1) — 스캔 범위(활성 자원 폴더 + extra-roots) 밖으로 통째 이동한 자원은 '경로 이동'이
			// 아니라 이 종류로 보고된다. extra-roots 보강 안내를 권장 조치에 병기.
			null,
			"백업 · 이동 · 이름 변경 여부를 확인해 파일을 원래 경로 · 이름으로 복원한 뒤 다시 점검하세요. "
					+ "점검 범위 밖(백업 폴더 등)으로 옮긴 경우라면 reconciliation.scan.extra-roots 설정에 해당 경로를 등록하면 "
					+ "다음 점검부터 '경로 이동'으로 탐지됩니다.",
			DriftResolutionMode.NONE,
			DriftResolveEntry.NONE,
			true,
			false,
			false
	),
	ORPHAN(
			"미등록 마커",
			"디스크에 마커가 있으나 DB 에 매칭되는 자원이 없습니다.",
			"발견된 마커와 파일을 격리 보관 구역으로 회수합니다. 삭제가 아니라 이동이라 실물은 보존됩니다.",
			"계속 쓸 자원이라면 [해결] 을 누르지 말고 관리 화면에서 다시 등록하세요 — 등록하면 주인이 생겨 이 문제가 사라집니다.",
			DriftResolutionMode.MANUAL,
			DriftResolveEntry.STANDARD,
			true,
			false,
			true
	) {
		@Override
		public String getOldPathLabel() {
			// DB 에 주인이 없는 종류라 '기록' 이 존재하지 않는다. 이 값은 디스크에서 마커를 발견한 자리다.
			return "감지된 실제 파일 경로";
		}
	},
	SIGNATURE_INVALID(
			"마커 서명 불일치",
			"마커 파일의 서명이 현재 서명 키와 맞지 않습니다. 마커 손상 · 외부 이식 또는 서명 키(secret) 변경이 원인일 수 있습니다.",
			"이 자원의 마커만 현재 서명 키로 다시 서명합니다. 내용 지문은 그대로 두므로, 실제 변조였다면 다음 정밀 점검에서 그대로 드러납니다.",
			"[해결] 전에 변조가 아니라 손상 · 외부 이식이 원인인지 먼저 확인하세요. 서명 키 교체 직후 여러 자원에서 한꺼번에 발생한 것이라면, 자원마다 누르는 대신 하단 관리 도구의 '마커 서명 재발급' 이 빠릅니다.",
			DriftResolutionMode.MANUAL,
			DriftResolveEntry.STANDARD,
			true,
			false,
			true
	),
	HASH_MISMATCH(
			"내용 변경 감지",
			"자원 파일의 내용이 등록 시점과 다릅니다 (정밀 점검에서만 감지).",
			"[현재 내용을 정본으로 수용] 에서 자원명을 입력해 확인하면, 지금 파일 내용의 지문을 정본으로 등록해 마커를 갱신합니다. 감사 기록에 남습니다.",
			"의도한 교체가 아니라면 원본을 다시 업로드하는 것이 가장 안전합니다 (검증 재통과). 수용은 의도한 교체가 확실할 때만 하세요 — 변조였다면 변조된 내용이 정본이 됩니다.",
			DriftResolutionMode.MANUAL,
			DriftResolveEntry.DEDICATED,
			false,
			true,
			true
	),
	// MK3 신규 — 탐지 미구현(S6-2 소관), 문구 선부여
	SOFTDEL_ESCAPE_TO_ORIGINAL(
			"삭제 자원 복귀",
			"휴지통에 있어야 할 자원이 원래 위치로 돌아와 있습니다.",
			"자원을 복원해 DB 상태를 실제 위치에 맞게 맞춥니다.",
			null,
			DriftResolutionMode.AUTO,
			DriftResolveEntry.STANDARD,
			false,
			false,
			true
	),
	SOFTDEL_ESCAPE_TO_OTHER(
			"삭제 자원 위치 이탈",
			"휴지통에 있어야 할 자원이 예상 밖의 위치에서 발견되었습니다. 운영자 의도를 알 수 없습니다.",
			"발견 위치의 자원을 휴지통으로 회수합니다. 회수까지가 [해결] 의 범위이고, 그 뒤의 복원은 자동으로 일어나지 않습니다.",
			"다시 쓸 자원이라면 [해결] 로 회수한 뒤 휴지통 화면에서 직접 복원하세요. 발견된 그 위치에서 계속 쓰려면 [해결] 대신 관리 화면에서 새로 등록해야 합니다.",
			DriftResolutionMode.MANUAL,
			DriftResolveEntry.STANDARD,
			false,
			false,
			true
	),
	// S11-1 신규 — 본체 없는 마커. 자원 상태 판정과 병행 보고되는 독립 신호 (TRASH_MARKER_STALE 의 활성 트리 판)
	SOFTDEL_MARKER_STRAY(
			"미아 마커",
			"삭제 자원의 마커가 본체 파일 없이 발견되었습니다. 마커만으로는 자원 상태를 바꾸지 않으며, "
					+ "자원 상태에 대한 판정(복귀 · 소실 · 유령 등)은 별도 문제로 함께 보고됩니다.",
			"발견 위치의 미아 마커를 정리합니다. 자원 실물과 기록은 건드리지 않으며, 마커는 재발급할 수 있습니다.",
			null,
			DriftResolutionMode.AUTO,
			DriftResolveEntry.STANDARD,
			false,
			false,
			true
	) {
		@Override
		public String getOldPathLabel() {
			// DB 경로도 아니고 이동 전 자리도 아니다 — 기록 기준으로 자원이 있어야 할 자리다.
			return "자원 기대 경로 (기록 기준)";
		}

		@Override
		public String getNewPathLabel() {
			return "마커 발견 경로";
		}
	},
	TRASH_LOST(
			"휴지통 자원 소실",
			"휴지통으로 이동된 자원이 그 위치에 없습니다. 외부에서 정리되었을 수 있습니다.",
			"실물 없이 남은 기록을 정리하고 감사 기록(휴지통 정리 이력)에 남깁니다.",
			"복구가 필요한 자원이었다면 [해결] 전에 백업을 먼저 확인하세요 — 기록을 정리하고 나면 되돌릴 수 없습니다.",
			DriftResolutionMode.MANUAL,
			DriftResolveEntry.STANDARD,
			false,
			false,
			false
	) {
		@Override
		public String getOldPathLabel() {
			// 기록상 여기 있어야 하는데 실물이 없다 — 그 '기록상 자리' 다.
			return "휴지통 기록 경로";
		}
	},
	TRASH_MARKER_STALE(
			"잔여 마커 정리 필요",
			"휴지통 자원 옆에 소프트 삭제 시 정리됐어야 할 마커가 남아 있습니다.",
			"휴지통 실물 옆에 남은 마커를 정리합니다. 자원 실물은 건드리지 않습니다.",
			null,
			DriftResolutionMode.AUTO,
			DriftResolveEntry.STANDARD,
			false,
			false,
			true
	) {
		@Override
		public String getOldPathLabel() {
			// DB 경로도 발견 위치도 아니다 — 잔여 마커가 붙어 있는 휴지통 실물의 자리다.
			return "휴지통 보관 경로";
		}
	},
	// MK3-1 신규
	GHOST_DB_ROW(
			"유령 DB 기록",
			"삭제 표시된 자원이 휴지통에도 디스크에도 없습니다. DB 기록만 남아 있습니다.",
			"남은 DB 기록을 영구 삭제합니다. 복구할 파일이 애초에 없어 안전한 정리입니다.",
			null,
			DriftResolutionMode.AUTO,
			DriftResolveEntry.STANDARD,
			false,
			false,
			false
	) {
		@Override
		public String getAbsentNewPathText() {
			// 파일이 어디에도 없다는 사실이 이 문제의 전부다 — 줄을 지우면 그 사실이 화면에서 사라진다.
			return "(찾을 수 없습니다.)";
		}
	},
	// HF4-5 신규 — 원본 정상 + 동일 신원 사본. 해결은 택일 입력 동반이라 전용 endpoint (mode=NONE — HASH_MISMATCH 선례)
	RESOURCE_REPLICA(
			"자원 중복 존재",
			"등록 경로의 원본이 정상인 상태에서, 같은 신원(자원 종류 · 번호)의 마커와 파일이 다른 위치에서도 발견되었습니다. "
					+ "방치하면 원본 유실 시 이 사본이 '경로 이동됨'으로 오인되어 정본 자리를 차지할 수 있습니다.",
			"[해결] 을 누르면 원본과 복제본 중 남길 쪽을 고르는 창이 뜹니다. 고른 뒤에는 선택하지 않은 쪽 파일을 삭제하고, "
					+ "복제본을 남겼다면 등록 경로(DB)도 복제본 위치로 갱신합니다.",
			"어느 쪽이 최신인지 두 경로의 파일을 먼저 확인하세요 — 선택하지 않은 쪽은 즉시 삭제되어 되돌릴 수 없습니다.",
			DriftResolutionMode.MANUAL,
			DriftResolveEntry.DEDICATED,
			false,
			false,
			false
	) {
		@Override
		public String getOldPathLabel() {
			return "원본 경로 (DB 기록)";
		}

		@Override
		public String getNewPathLabel() {
			return "복제본 경로";
		}
	};

	/**
	 * 사용자 노출 한 줄 명칭. 템플릿 배지·확인 문구가 사용한다. enum 원시명은 상세 화면의 보조 표기로만.
	 */
	private final String label;

	/**
	 * "무슨 일이 벌어졌나" — 처음 보는 사용자 기준 설명.
	 */
	private final String description;

	/**
	 * "[해결] 을 누르면 시스템이 무엇을 하나" — 버튼이 실제로 일으키는 변화. 시스템이 대신 처리할 수
	 * 없는 종류는 {@code null} 이고, 그 경우 화면은 이 줄을 아예 띄우지 않는다.
	 *
	 * <p>{@link #operatorAction} 과 한 문장에 섞여 있던 것을 갈랐다. 섞여 있으면 "회수합니다. 다시 쓸
	 * 자원이면 복원하세요" 처럼 <b>버튼이 해 주는 일</b>과 <b>사람이 해야 할 일</b>이 나란히 놓여,
	 * 버튼 하나로 다 끝나는 것처럼 읽힌다.</p>
	 */
	private final String resolveAction;

	/**
	 * "사람이 직접 해야 할 일" — 버튼을 누르기 전에 확인할 것, 누른 뒤에 이어서 할 것, 또는 버튼
	 * 대신 택할 길. 없으면 {@code null}.
	 */
	private final String operatorAction;

	/**
	 * S6-2-1 — 시스템 해결 방식 3단 ({@link DriftResolutionMode}). 서버 가드·템플릿(버튼 노출·배지 색)·
	 * 스캔 무인 적용 자격이 공유하는 단일 소스. 전역 옵션({@code reconciliation.resolution-enabled})과는
	 * 별개 축 — 전역 OFF 는 허용 종류도 거절한다.
	 */
	private final DriftResolutionMode mode;

	/**
	 * 어느 입구로 해결하는가({@link DriftResolveEntry}). {@code mode} 가 "얼마나 자동으로 되는가" 라면
	 * 이 축은 "어느 폼으로 처리되는가" 다. 둘을 겸하던 종전 구조에서는 전용 폼을 쓰는 종류가
	 * 사람 확인만으로 시스템이 처리해 주는데도 '해결 없음' 으로 분류될 수밖에 없었다.
	 */
	private final DriftResolveEntry resolveEntry;

	/**
	 * S6-3-3 — [다시 점검](그 자원 하나만 즉시 재확인) 지원 여부. 해결 등급(mode)과 <b>독립인 별도 축</b> —
	 * 확인 액션이라 mode 승격과 무관하게 유지된다. 판정만 갱신하고 상태는 바꾸지 않으며,
	 * 해소 시 보고서에서 카드 제거만 한다(Drift 불변 설계 유지 — 재분류는 전체 점검의 몫).
	 * HASH_MISMATCH 는 재확인에 내용 지문 재계산(대용량 수 분)이 필요해 제외 — S6-3-4 의
	 * 백그라운드 배선과 함께 재검토.
	 */
	private final boolean recheckable;

	/**
	 * MK4-1 — 정밀 점검에서만 감지되는 종류인가. 일반 점검은 파일 내용을 보지 않으므로
	 * {@code HASH_MISMATCH} 를 관측할 수도, 사라졌다고 판정할 수도 없다. 이 성질이 없으면
	 * 점검 로직에 {@code if (kind == HASH_MISMATCH)} 분기가 생기고, 그 분기를 빠뜨리면
	 * 일반 점검마다 내용 변경 문제가 사라졌다 정밀 점검마다 되살아나기를 반복한다.
	 */
	private final boolean deepOnly;

	/**
	 * MK4-1 — 이 종류의 처리를 되돌릴 수 있는가. 기록을 지우는 종류(유령 기록 · 휴지통 소실)는
	 * 복구할 근거가 없어 불가다. {@code DriftHandlingAction.reversibleFor} 가 이 값을 참조한다.
	 */
	private final boolean reversible;

	DriftKind(String label, String description, String resolveAction, String operatorAction,
			DriftResolutionMode mode, DriftResolveEntry resolveEntry,
			boolean recheckable, boolean deepOnly, boolean reversible) {
		this.label = label;
		this.description = description;
		this.resolveAction = resolveAction;
		this.operatorAction = operatorAction;
		this.mode = mode;
		this.resolveEntry = resolveEntry;
		this.recheckable = recheckable;
		this.deepOnly = deepOnly;
		this.reversible = reversible;
	}

	/**
	 * MK4-1 — 이번 점검이 이 종류를 관측 범위에 담았는가. 자동 해소 판정의 전제이기도 하다 —
	 * 커버하지 않은 종류를 "이번에 안 보였다" 는 이유로 닫으면 안 된다.
	 */
	public boolean coveredBy(boolean deep) {
		return deep || !deepOnly;
	}

	/**
	 * 스캔 무인 자동 적용 자격 (mode == AUTO). 실제 발동은 {@code reconciliation.auto-apply.kinds} 옵트인.
	 */
	public boolean isAutoApplicable() {
		return mode == DriftResolutionMode.AUTO;
	}

	/**
	 * 표준 [해결] 버튼 노출 + 공용 apply 가드 기준. 판정을 {@code mode} 가 아니라 처리 입구로 하는 이유는
	 * 전용 폼을 쓰는 종류(자원 중복 존재 · 내용 변경 감지)도 사람 확인 뒤 시스템이 처리해 주어
	 * 등급은 MANUAL 이지만, 표준 폼으로 처리되지는 않기 때문이다 — 등급으로 판정하면 전용 폼 옆에
	 * 표준 버튼이 하나 더 뜨고 공용 apply 는 전략 bean 이 없어 거절한다.
	 */
	public boolean isManuallyResolvable() {
		return resolveEntry == DriftResolveEntry.STANDARD;
	}

	/**
	 * 상세 화면의 {@code old_path} 필드 라벨. 종류별 의미가 다를 때만 상수가 재정의한다.
	 * 사용자 문구의 SSOT 를 enum 에 모으는 R9-2 원칙 — 템플릿 kind 분기 금지.
	 *
	 * <p>기본값이 '이전 경로' 였을 때는 무엇과 무엇을 견주는 것인지가 드러나지 않았다. 대부분의
	 * 종류에서 이 두 값은 <b>DB 가 안다고 하는 위치</b>와 <b>디스크에서 실제로 발견된 위치</b>라,
	 * 그 뜻을 이름에 그대로 적는다.</p>
	 */
	public String getOldPathLabel() {
		return "DB 기록";
	}

	/**
	 * 상세 화면의 {@code new_path} 필드 라벨. {@link #getOldPathLabel()} 과 동일 원칙.
	 */
	public String getNewPathLabel() {
		return "실제 파일 경로";
	}

	/**
	 * {@code new_path} 가 비었을 때 그 자리에 대신 보일 문구. {@code null} 이면 줄 자체를 띄우지 않는다
	 * (기본값 — 견줄 대상이 애초에 없는 종류).
	 *
	 * <p>'없다' 는 사실 자체가 이 문제의 핵심인 종류에서는 줄을 지우면 오히려 정보가 사라진다.
	 * 유령 기록이 그렇다 — DB 만 있고 파일이 어디에도 없다는 것이 문제의 전부다.</p>
	 */
	public String getAbsentNewPathText() {
		return null;
	}
}
