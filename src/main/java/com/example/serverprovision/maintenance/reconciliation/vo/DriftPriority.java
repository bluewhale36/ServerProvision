package com.example.serverprovision.maintenance.reconciliation.vo;

import com.example.serverprovision.global.marker.DriftSeverity;
import com.example.serverprovision.provisioning.usage.ResourceUsageLevel;

import java.time.Instant;
import java.util.Comparator;

/**
 * MK4-2 — 드리프트 하나의 처리 순서. 세 값의 묶음이며 비교 규칙이 이 타입 안에 산다.
 *
 * <p><b>사전식(lexicographic)으로 비교한다.</b> 위험도가 급을 가르고, 같은 급 안에서 사용 중이
 * 순서를 바꾸며, 그래도 같으면 오래 방치된 것이 앞선다. 가중합({@code 위험도 × 가중치 + 사용 중})을
 * 쓰지 않는 이유는 둘이다 — 가중치가 작으면 사용 중이 급을 뒤집고, 뒤집히지 않을 만큼 크게 잡으면
 * 결국 사전식과 같은 동작을 매직 상수로 흉내 내는 것이 된다.</p>
 *
 * <p>사전식의 결과로 <b>급 보존</b>이 보장된다 — 사용 중을 아무리 올려도 더 급한 급의 항목을
 * 넘어서지 못한다. 화면이 "왜 이 순서인가" 를 "위험도 → 사용 중 → 오래된 순" 세 마디로 설명할 수
 * 있는 것도 이 성질 덕분이다.</p>
 *
 * <p>이 값은 저장하지 않고 조회 시점에 만든다. 위험도는 종류에서 파생되고, 사용 여부는 드리프트와
 * 무관하게 변하기 때문이다(정의서 수정 · 할당 · 소비). 스캔 시점에 굳히면 그 값이 곧 낡는다.</p>
 *
 * @param severity        종류가 결정하는 고정 위험도 — 큰 축
 * @param usage           자원이 지금 쓰이는 깊이 — 같은 급 안의 보정
 * @param firstDetectedAt 이 문제가 처음 관측된 시각 — 동률을 깨는 마지막 기준
 */
public record DriftPriority(
		DriftSeverity severity,
		ResourceUsageLevel usage,
		Instant firstDetectedAt
) implements Comparable<DriftPriority> {

	/**
	 * 급한 순. 위험도 오름차순({@code IMMEDIATE} 가 앞) → 사용 중 내림차순({@code RUNNING} 이 앞)
	 * → 최초 발견 오름차순(오래된 것이 앞).
	 */
	private static final Comparator<DriftPriority> ORDER =
			Comparator.comparing(DriftPriority::severity)
					.thenComparing(DriftPriority::usage, Comparator.reverseOrder())
					.thenComparing(DriftPriority::firstDetectedAt,
							Comparator.nullsLast(Comparator.naturalOrder()));

	public DriftPriority {
		if (severity == null) throw new IllegalArgumentException("위험도 없이 순서를 정할 수 없습니다.");
		if (usage == null) throw new IllegalArgumentException("사용 수준 없이 순서를 정할 수 없습니다.");
	}

	@Override
	public int compareTo(DriftPriority other) {
		return ORDER.compare(this, other);
	}
}
