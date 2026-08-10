package com.example.serverprovision.global.marker;

import lombok.Getter;

/**
 * MK4-2 — 드리프트 종류별 고정 위험도. 처리 순서를 정하는 두 축 중 큰 축이다.
 *
 * <p>등급을 가르는 기준은 하나다 — <b>이 자원으로 지금 프로비저닝하면 어떻게 되는가.</b>
 * 이 질문 하나로 모든 종류가 자리를 찾으므로 종류마다 따로 판단할 것이 없고, 새 종류가 생겨도
 * 같은 질문에 답하면 등급이 정해진다.</p>
 *
 * <p>해결 방식({@link DriftResolutionMode})을 등급에 섞지 않는다. 자동 처리는 운영 설정으로 켜고 끌 수
 * 있어, 섞으면 같은 종류의 급이 설정에 따라 달라진다. 되돌릴 수 있는가({@code reversible})도 축으로
 * 쓰지 않는다 — 되돌릴 수 없지만 방치해도 더 나빠지지 않는 종류(휴지통 자원 소실)가 최상위로 올라가
 * "급한 순서" 라는 이름과 어긋나기 때문이다.</p>
 *
 * <p>선언 순서가 곧 급의 순서다({@code ordinal} 오름차순 = 급한 순). 정렬은
 * {@code DriftPriority} 가 사전식으로 수행한다.</p>
 */
@Getter
public enum DriftSeverity {

	/** 프로비저닝이 실패하거나 잘못된 것이 설치된다. */
	IMMEDIATE("즉시", "이 상태로 프로비저닝하면 실패하거나 의도하지 않은 것이 설치됩니다."),

	/** 지금 당장은 동작하지만 방치하면 잘못된 것을 집는다. */
	ATTENTION("주의", "지금은 동작하지만 방치하면 시스템이 잘못된 파일을 정본으로 집을 수 있습니다."),

	/** 이미 삭제된 자원의 기록이 실물과 어긋나 있다. */
	RECORD("기록 정리", "이미 삭제된 자원의 기록이 실제 파일 상태와 어긋나 있습니다. 프로비저닝에는 영향이 없습니다."),

	/** 실물 손실이 없고 마커만 정리하면 된다. */
	TIDY("정리", "자원 실물은 온전하고 마커만 정리하면 됩니다.");

	private final String label;
	private final String description;

	DriftSeverity(String label, String description) {
		this.label = label;
		this.description = description;
	}
}
