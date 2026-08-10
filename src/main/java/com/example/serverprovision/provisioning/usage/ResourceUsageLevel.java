package com.example.serverprovision.provisioning.usage;

import lombok.Getter;

/**
 * MK4-2 — 자원이 지금 얼마나 깊이 쓰이는 중인가. 처리 순서의 보정 축이다.
 *
 * <p>뒤로 갈수록 사용이 깊어지고 보정이 커진다. 선언 순서가 곧 그 깊이다({@code ordinal} 오름차순 =
 * 얕은 순). 다만 이 축은 <b>같은 위험도 급 안에서만</b> 순서를 바꾼다 — 급을 넘지 못하는 이유는
 * {@code DriftPriority} 의 사전식 비교에 있다.</p>
 *
 * <p>이 enum 은 답을 만드는 쪽인 provisioning 이 소유한다. 사용 여부를 알려면 세팅 정의서의 참조와
 * 할당 스냅샷을 함께 읽어야 하는데 그 지식이 이 영역에 있기 때문이다.</p>
 */
@Getter
public enum ResourceUsageLevel {

	/** 어느 세팅 정의서도 이 자원을 지정하지 않는다. */
	NONE("미사용", "이 자원을 지정한 세팅 정의서가 없습니다."),

	/** 세팅 정의서가 이 자원을 지정하고 있다. 아직 서버에 배정되지는 않았다. */
	DEFINED("정의서 지정", "세팅 정의서가 이 자원을 지정하고 있습니다."),

	/** 그 정의서가 서버에 할당되어 있다(대체되지 않은 활성 할당). */
	ASSIGNED("서버 할당", "이 자원을 쓰는 정의서가 서버에 할당되어 있습니다."),

	/** 그 할당을 게스트가 이미 가져가 프로비저닝이 진행 중이다. */
	RUNNING("진행 중", "이 자원을 쓰는 프로비저닝이 진행 중입니다.");

	private final String label;
	private final String description;

	ResourceUsageLevel(String label, String description) {
		this.label = label;
		this.description = description;
	}

	/** 두 수준 중 더 깊은 쪽. 한 자원이 여러 정의서 · 할당에 걸릴 때 가장 깊은 사용을 대표값으로 삼는다. */
	public ResourceUsageLevel deeper(ResourceUsageLevel other) {
		if (other == null) return this;
		return this.ordinal() >= other.ordinal() ? this : other;
	}
}
