package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.maintenance.reconciliation.enums.DriftTimelineKind;

import java.time.Instant;

/**
 * MK4-4-2 — 드리프트 하나에 일어난 일 한 줄.
 *
 * <p>한 시간축에 넷이 섞인다. 점검의 <b>관측</b>, 사람이나 시스템의 <b>처리</b>, 앞선 드리프트가
 * 닫히며 <b>이어진</b> 자리, 그리고 이 드리프트가 닫히며 <b>이어 준</b> 자리다. 넷을 나눠 놓으면
 * 읽는 사람이 시간을 머릿속에서 다시 맞춰야 한다.</p>
 *
 * <p>뒤로 이어 준 것까지 싣는 이유는, 닫힌 드리프트를 열었을 때 <b>정말 끝난 것인지 뒤에 더 생긴
 * 문제가 방치된 것인지</b> 구분할 방법이 없었기 때문이다. 계보 링크가 후임 → 전임 단방향이라
 * 전임 쪽에서는 자기 뒤를 볼 수 없었다.</p>
 *
 * @param at        일어난 시각. 이어짐은 앞선 드리프트가 닫힌 때, 이어 줌은 후임이 처음 감지된 때다
 * @param kind      무엇이 일어났는가. 링크 대상이 여기서 갈린다
 * @param label     화면에 적히는 한 줄. 문구의 단일 소스는 각 enum 이다
 * @param reportId  관측이면 그 회차. 그 밖에는 비어 있다
 * @param driftId   이어짐 · 이어 줌이면 그쪽 드리프트. 그 밖에는 비어 있다
 * @param current   이 드리프트 자신에게 일어난 일인가. 그래프에서 마름모로 그린다
 * @param resolved  이 줄이 가리키는 드리프트가 닫혔는가. 그래프의 색이 여기서 온다
 * @param fromPath  그때 알고 있던 경로
 * @param toPath    옮겨 간 곳. 옮기지 않은 일에서는 비어 있다
 */
public record DriftTimelineEntry(
		Instant at,
		DriftTimelineKind kind,
		String label,
		Long reportId,
		Long driftId,
		boolean current,
		boolean resolved,
		String fromPath,
		String toPath
) {

	/**
	 * 그래프 노드에 붙일 class. 두 축이 곱해진다.
	 *
	 * <ul>
	 *   <li><b>모양</b> — 이 드리프트 자신의 일이면 마름모, 다른 드리프트를 가리키면 원.
	 *       지금 무엇을 보고 있는지가 한눈에 드러난다</li>
	 *   <li><b>색</b> — 닫혔으면 파랗게 채우고, 열려 있으면 붉은 테두리만 남긴다.
	 *       사슬 어딘가에 아직 처리되지 않은 것이 있으면 그 자리가 눈에 띈다</li>
	 * </ul>
	 *
	 * <p>화면이 이 조합을 조립하지 않는 이유는, 축이 늘 때마다 템플릿의 조건식이 함께 자라기
	 * 때문이다. 여기 한 곳만 고치면 이력이 나오는 모든 자리가 같이 바뀐다.</p>
	 */
	public String nodeClass() {
		return (current ? "is-current " : "") + (resolved ? "is-resolved" : "is-open");
	}

	/**
	 * 이 줄을 <b>지금 서 있는 자리</b>로 세운다.
	 *
	 * <p>마름모는 사슬 안에서 "지금 보고 있는 드리프트가 여기 있다" 를 가리키는 표식이라 하나여야
	 * 한다. 자기 줄 전체를 마름모로 그렸더니 관측이 쌓인 드리프트에서 화면이 온통 마름모가 되어,
	 * 표식이 아무것도 가리키지 않게 됐다.</p>
	 */
	public DriftTimelineEntry asCurrent() {
		return new DriftTimelineEntry(at, kind, label, reportId, driftId, true, resolved, fromPath, toPath);
	}

	/** 이 줄에서 갈 수 있는 곳이 있는가 — 관측은 그 회차로, 이어짐 · 이어 줌은 그 드리프트로. */
	public boolean linked() {
		return reportId != null || driftId != null;
	}

	/**
	 * 이 줄이 가리키는 대상의 식별자. 관측은 회차, 이어짐 · 이어 줌은 그쪽 드리프트다.
	 *
	 * <p>처리는 비어 있다 — 가리키는 대상이 이 드리프트 자신이라 제목 옆에 이미 적힌 번호를
	 * 줄마다 되풀이하게 된다. 없는 것을 없다고 두는 편이 같은 값을 반복해 열을 채우는 것보다 낫다.</p>
	 */
	public Long targetId() {
		return reportId != null ? reportId : driftId;
	}

	/** 이 일로 자원이 옮겨졌는가. 화면이 화살표를 그릴지 판단한다. */
	public boolean moved() {
		return toPath != null && !toPath.equals(fromPath);
	}
}
