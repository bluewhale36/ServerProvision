package com.example.serverprovision.global.orphan.enums;

/**
 * 등록 실패가 INFRA/TRANSIENT 로 분류돼 격리(quarantine)될 때의 원인 분류.
 *
 * <p>분류는 등록 실패 예외의 {@code RegistrationFailure.disposition()} (다형성)으로 결정되며, 본 enum 은
 * 그 결과를 도메인 값으로 보존하고 사용자 표시 문구를 상수별 메서드로 제공한다(분기문 대신 다형성).</p>
 *
 * <p>R1-4-4 — {@code management/os/enums} 에서 {@code global/orphan/enums} 로 승격
 * (도메인 무관 오펀 saga 인프라의 일부).</p>
 */
public enum OrphanFailureClass {

	STORAGE_IO {
		@Override
		public String displayReason() {
			return "디스크 IO / 해시 계산 실패";
		}
	},
	DB_CONSTRAINT {
		@Override
		public String displayReason() {
			return "DB 저장 실패 (제약 위반 등)";
		}
	},
	MARKER_WRITE {
		@Override
		public String displayReason() {
			return "마커(.provision.json) 기록 실패";
		}
	},
	UNEXPECTED {
		@Override
		public String displayReason() {
			return "예기치 못한 오류";
		}
	};

	/** 사용자에게 보여줄 실패 사유 한 줄. */
	public abstract String displayReason();
}
