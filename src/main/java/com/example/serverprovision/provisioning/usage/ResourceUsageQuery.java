package com.example.serverprovision.provisioning.usage;

import com.example.serverprovision.global.trash.ResourceKey;

import java.util.Collection;
import java.util.Map;

/**
 * MK4-2 — "이 자원이 지금 쓰이는 중인가" 에 답하는 조회 계약.
 *
 * <p>이 인터페이스는 <b>답을 만드는 쪽인 provisioning 이 소유하고</b>, 묻는 쪽인 maintenance 가
 * 호출한다. 방향을 이렇게 잡은 근거는 실측이다 — {@code SettingAssignmentSnapshot} 가 실행 영역의
 * {@code GuestServer} 를 직접 참조해 {@code provisioning → execution} 의존이 이미 존재하므로,
 * provisioning 이 세 수준을 전부 답할 수 있고 maintenance 는 실행 영역을 볼 필요가 없다.
 * 새로 생기는 의존은 {@code maintenance → provisioning} 하나뿐이다.</p>
 *
 * <p>반대 방향(maintenance 가 인터페이스를 소유하고 provisioning 이 구현)은 채택하지 않았다.
 * 저장소의 선례({@code OwnedPhasesProvider} · {@code AssignmentUsageInspector})가 그 모양인 것은
 * <b>역방향 의존이 이미 있어 순환을 끊어야 했기 때문</b>인데, 여기는 어느 방향도 없어 순환 위험 자체가
 * 없다. 근거 없이 간접층을 먼저 넣지 않으며, 그 방향으로 가면 핵심 도메인이 보조 도메인에 의존하게
 * 되어 의존의 방향이 뒤집힌다. 나중에 provisioning 이 maintenance 를 부를 일이 생기면 그때 뒤집는다.</p>
 */
public interface ResourceUsageQuery {

	/**
	 * 자원들의 사용 깊이를 한 번에 조회한다.
	 *
	 * <p>낱개가 아니라 묶음으로 받는 이유는 호출부가 드리프트 목록이기 때문이다. 자원마다 부르면
	 * 목록 길이만큼 조회가 반복된다(N+1). 한 자원이 여러 정의서 · 할당에 걸리면 가장 깊은 사용을
	 * 대표값으로 삼는다.</p>
	 *
	 * @param keys 조회할 자원 식별자들. 비어 있으면 빈 맵을 돌려준다
	 * @return 자원별 사용 깊이. <b>요청한 키는 모두 담겨 있으며</b>, 쓰이지 않는 자원은
	 *         {@link ResourceUsageLevel#NONE} 이다(호출부가 null 을 다루지 않게 한다)
	 */
	Map<ResourceKey, ResourceUsageLevel> levelsOf(Collection<ResourceKey> keys);
}
