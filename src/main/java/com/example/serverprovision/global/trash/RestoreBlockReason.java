package com.example.serverprovision.global.trash;

import lombok.Getter;

/**
 * MK4-5-1 — 휴지통 자원의 복원을 막는 사유. 값 하나가 라벨과 안내 문구를 함께 든다.
 *
 * <p>종전에는 복원을 막는 사유가 이미 셋이었는데 표현이 제각각이었다. 유령 기록은 {@code th:if} 로
 * 액션 세트를 통째로 갈아치웠고, 부모 삭제와 연장 미지원은 {@code th:disabled} 에 tooltip 을 달되
 * <b>안내 문구가 템플릿에 하드코딩</b>돼 있었다. 네 번째 사유(휴지통 자원 소실)를 같은 방식으로
 * 더하면 분기가 또 늘고 문구는 또 한 곳에 흩어진다.</p>
 *
 * <p>그래서 사유를 값으로 만든다. 템플릿은 "막혔는가" 와 "무엇이라 말할 것인가" 를 모두 이 값에서
 * 읽고, 서버 가드는 같은 판정({@link TrashRestoreEvaluator})을 불러 같은 결론에 이른다.
 * {@code childEnableBlockReason()} 과 {@code SettingAssignment.reassignBlockReason()} 이 같은 형태다.</p>
 *
 * <p>사용자 노출 문구를 enum 이 드는 것은 R9-2 가 {@code DriftKind} 로 세운 관례를 따른 것이다.</p>
 */
@Getter
public enum RestoreBlockReason {

	/**
	 * 부모가 삭제 상태라 자식만 단독으로 되살릴 수 없다. 실물과 무관한 기록 축의 사유다.
	 */
	PARENT_DELETED(
			"부모 삭제 상태",
			"부모 자원이 삭제 상태라 자식 단독 복구가 불가능합니다. 부모부터 복구해 주세요.",
			null,
			"gray",
			false
	),

	/**
	 * 기록만 남고 실물이 어디에도 없다. 복구할 대상 자체가 없으므로 이 행에서 [정리] 로 끝난다.
	 */
	GHOST(
			"복구 불가",
			"기록만 남고 복구할 파일이 없습니다. 이 행의 [정리] 로 기록을 지울 수 있습니다.",
			null,
			"red",
			true
	),

	/**
	 * 휴지통 기록은 살아 있는데 그 자리의 실물이 없다. 이 행에는 처리할 버튼이 없고 자원 무결성
	 * 점검이 「휴지통 자원 소실」 로 다룬다 — 처리 이력이 그쪽 원장에 쌓이기 때문이다.
	 */
	TRASH_LOST(
			"휴지통 자원 소실",
			"휴지통에 보관돼 있어야 할 파일이 그 자리에 없습니다. 자원 무결성 점검에서 「휴지통 자원 소실」 로 처리해 주세요.",
			"자원 무결성 점검",
			"red",
			true
	);

	/**
	 * 목록 배지에 뜨는 한 줄 명칭.
	 */
	private final String label;

	/**
	 * 막힌 버튼의 tooltip 문구. <b>왜 막혔는지와 다음에 무엇을 할지를 한 문장 안에 함께 담는다</b> —
	 * 거절만 하고 갈 곳을 말하지 않으면 막다른 길이 된다.
	 */
	private final String guidance;

	/**
	 * 이 사유가 사용자를 다른 화면으로 보내는가. 보내지 않으면 {@code null} 이고, 그 경우 이 행
	 * 안에서 끝낼 수 있다는 뜻이다({@link #GHOST} 의 [정리]).
	 */
	private final String nextScreen;

	/**
	 * 배지 색. 사유가 스스로 든다 — 화면이 조건식으로 색을 고르면 사유가 늘 때마다 그 식이 함께
	 * 자라고, 여러 화면에 복붙되면 한 곳만 고쳐져 색이 갈린다({@code DriftStatus} 와
	 * {@code DriftResolutionMode} 가 같은 이유로 색을 들고 있다 — R9-2).
	 */
	private final String badgeColor;

	/**
	 * 행 왼쪽에 배지를 띄울 사유인가.
	 *
	 * <p>부모 삭제는 {@code false} 다 — 자원 이름 셀의 부모 줄에 이미 「부모 삭제 상태」 배지가
	 * 붙어 있고, 그 사유는 자식 자원에서만 나오므로 둘이 <b>항상 함께</b> 뜬다. 왼쪽에 또 띄우면
	 * 같은 문구가 한 행에 두 번 나온다. 막혔다는 사실은 흐려진 [복원] 과 그 tooltip 이 말한다.</p>
	 */
	private final boolean rowBadge;

	RestoreBlockReason(
			String label, String guidance, String nextScreen, String badgeColor, boolean rowBadge
	) {
		this.label = label;
		this.guidance = guidance;
		this.nextScreen = nextScreen;
		this.badgeColor = badgeColor;
		this.rowBadge = rowBadge;
	}

	/**
	 * 다른 화면으로 건너가야 하는 사유인가. 템플릿이 링크를 붙일지 판단한다.
	 */
	public boolean hasNextScreen() {
		return nextScreen != null;
	}
}
