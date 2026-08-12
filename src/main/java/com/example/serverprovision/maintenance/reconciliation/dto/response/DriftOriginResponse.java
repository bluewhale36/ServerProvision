package com.example.serverprovision.maintenance.reconciliation.dto.response;

import com.example.serverprovision.global.marker.DriftKind;

import java.time.Instant;

/**
 * MK4-4-2 — 계보의 한 마디. 지금 보고 있는 문제가 <b>어느 문제에서 이어졌는가</b>를 가리킨다.
 *
 * <p>같은 자원의 문제가 종류를 바꿔 나타나는 일이 있다. 마커 서명 불일치를 고치자 그 자리에서
 * 자원 중복 존재가 드러나는 식이다. S11-2 가 이 사슬을 기록 계층으로 남겨 두었고
 * ({@code Drift.predecessor} — 그 필드 주석이 "표시는 MK4-4 소관" 이라고 적어 두었다) 이 슬라이스가
 * 화면으로 꺼낸다.</p>
 *
 * <p>사슬을 밝히지 않으면 화면이 <b>거짓을 말한다</b>. 승계된 문제는 새 기록이라 관측 횟수가 1 이고,
 * 그대로 두면 「최초 발견」 으로 표시된다 — 앞선 문제가 형태를 바꾼 것인데 방금 생겼다고 말하는
 * 셈이다. 그래서 {@link DriftResponse#isNew()} 도 계보를 함께 본다.</p>
 *
 * @param driftId         전임 문제의 식별자. 화면이 그리로 가는 링크를 만든다
 * @param kind            전임의 종류
 * @param firstDetectedAt 전임을 처음 본 시각 — 사슬을 거슬러 "이 사건이 언제 시작됐나" 에 답한다
 * @param resolvedAt      전임이 닫힌 시각. 이 문제가 나타난 때와 맞물린다
 */
public record DriftOriginResponse(
		Long driftId,
		DriftKind kind,
		Instant firstDetectedAt,
		Instant resolvedAt
) {

	/** 화면이 배지 tooltip 에 쓰는 이름. enum 이 문구의 단일 소스라 여기서 조립하지 않는다. */
	public String kindLabel() {
		return kind.getLabel();
	}
}
