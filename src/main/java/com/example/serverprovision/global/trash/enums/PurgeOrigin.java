package com.example.serverprovision.global.trash.enums;

/**
 * S5-2-4 — 휴지통 자원 hard-delete 의 3 진입경로.
 *
 * <p>enum-per-method 다형성으로 origin 별 정책 차이 (retry / typed-name / Job 제목) 를 응집한다.
 * switch / if-else 대신 각 상수가 자기 동작을 보유 — CLAUDE.md "조건 분기문 무분별 확장 절대 지양" 준수.</p>
 *
 * <table>
 *   <tr><th>origin</th><th>typed-name</th><th>retry</th><th>Job 제목</th></tr>
 *   <tr><td>USER_DIRECT</td><td>O 필수</td><td>X (사용자가 재시도)</td><td>—</td></tr>
 *   <tr><td>NUDGE_REPLACE</td><td>O (v3-1 신설)</td><td>X</td><td>—</td></tr>
 *   <tr><td>TTL_AUTO</td><td>X (시스템)</td><td>O 3회 backoff</td><td>"🗑 자동 영구삭제"</td></tr>
 * </table>
 */
public enum PurgeOrigin {

	/**
	 * 5 list 페이지 / 휴지통의 사용자 직접 영구삭제 진입. typed-name 검증 통과 후 호출.
	 */
	USER_DIRECT {
		@Override
		public boolean retriesAllowed() {
			return false;
		}

		@Override
		public boolean requiresTypedName() {
			return true;
		}

		@Override
		public String jobTitle(String displayName) {
			return "자원 영구삭제 — " + displayName;
		}

		@Override
		public String displayName() {
			return "사용자 직접";
		}
	},

	/**
	 * nudge 충돌 modal 에서 사용자가 "REPLACE" 클릭 후 typed-name 입력 통과.
	 */
	NUDGE_REPLACE {
		@Override
		public boolean retriesAllowed() {
			return false;
		}

		@Override
		public boolean requiresTypedName() {
			return true;
		}

		@Override
		public String jobTitle(String displayName) {
			return "nudge 교체 — " + displayName;
		}

		@Override
		public String displayName() {
			return "충돌 교체";
		}
	},

	/**
	 * TrashTtlWorker 의 cron 진입 — 시스템 자동. retry + 사전 알림 + 실패 격상.
	 */
	TTL_AUTO {
		@Override
		public boolean retriesAllowed() {
			return true;
		}

		@Override
		public boolean requiresTypedName() {
			return false;
		}

		@Override
		public String jobTitle(String displayName) {
			return "🗑 자동 영구삭제 — " + displayName;
		}

		@Override
		public String displayName() {
			return "자동 (TTL 만료)";
		}
	},

	/**
	 * S6-2-3 — 자원 무결성 점검의 "휴지통 자원 소실"(TRASH_LOST) 정리 진입. 사용자가 drift 카드에서
	 * [적용]으로 확인한 뒤 호출된다. 실물이 이미 유실되어 잃을 것이 없는 기록 정리라 typed-name 을
	 * 요구하지 않는다(유령 기록 정리 선례). 사용자 확인 기반 1회 실행 — retry 없음.
	 */
	DRIFT_TRASH_LOST {
		@Override
		public boolean retriesAllowed() {
			return false;
		}

		@Override
		public boolean requiresTypedName() {
			return false;
		}

		@Override
		public String jobTitle(String displayName) {
			return "휴지통 소실 정리 — " + displayName;
		}

		@Override
		public String displayName() {
			return "점검 — 휴지통 소실 정리";
		}
	};

	/**
	 * 본 cron tick 내에서 retry 가 허용되는가. TTL_AUTO 만 true.
	 */
	public abstract boolean retriesAllowed();

	/**
	 * PurgeExecutor 진입 전 typed-name 검증이 필요한가. 사용자 진입 2 경로만 true.
	 */
	public abstract boolean requiresTypedName();

	/**
	 * BackgroundJob 카드 제목 합성 — displayName 을 합성식에 끼움.
	 */
	public abstract String jobTitle(String displayName);

	/**
	 * S5-2-4 — UI 표시용 사용자 친화 이름. select option / 테이블 셀에 사용.
	 */
	public abstract String displayName();
}
