package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * MK4-1 — 문제 저장소. 점검이 신원으로 기존 문제를 찾아 잇고, 화면이 열린 문제를 센다.
 */
public interface DriftRepository extends JpaRepository<Drift, Long> {

	/**
	 * 신원(자원 종류 · 자원 번호 · 종류)으로 아직 닫히지 않은 문제를 찾는다. 해결된 문제는 재사용하지
	 * 않으므로(같은 신원이 다시 발견되면 새 문제) 제외한다.
	 *
	 * <p>유일 제약을 걸지 않고 조회로 보장하는 이유는 동시 점검이 이미 실행 중 예외로 막혀 있어
	 * 문제 생성 경로가 단일화돼 있기 때문이다. 여러 인스턴스로 늘어나는 시점이 이 판단을 다시 볼
	 * 시점이다(CP1 결정 D2).</p>
	 */
	Optional<Drift> findFirstByResourceTypeAndResourceIdAndKindAndStatusNot(
			ResourceType resourceType, Long resourceId, DriftKind kind, DriftStatus status);

	/**
	 * 아직 닫히지 않은 문제 전부. 점검이 "이번에 안 보인 것" 을 가려내고, 화면이 목록을 만든다.
	 */
	List<Drift> findByStatusNot(DriftStatus status);

	/**
	 * MK4-5-1 — 특정 종류의 아직 닫히지 않은 문제 전부. 휴지통 화면이 막힌 행에서 점검으로 가는
	 * 링크를 붙일 때 쓴다.
	 *
	 * <p>행마다 조회하지 않고 한 번에 가져와 화면이 지도를 만든다 — 목록이 N 행이면 조회도
	 * N 번이 되는 것을 피한다. 보관 중인 문제도 포함한다({@code StatusNot(RESOLVED)}) — 운영자가
	 * 알면서 미룬 상태여도 휴지통 행이 막힌 이유를 설명하는 것은 그 문제이기 때문이다.</p>
	 */
	List<Drift> findByKindAndStatusNot(DriftKind kind, DriftStatus status);

	/**
	 * MK4-4-3 — 보관 화면이 쓴다. 만료 여부는 시각 비교라 도메인이 판정하므로
	 * ({@code Drift.isSnoozeExpired}) 여기서는 상태만 걸러 온다.
	 */
	List<Drift> findByStatus(DriftStatus status);

	/**
	 * 지금 목록에 떠야 하는 문제의 수. 종전에는 보고서의 자식 수를 셌으나 그 값은 사후에 변했다.
	 */
	long countByStatus(DriftStatus status);

	/**
	 * MK4-4-2 — 이 드리프트를 이어받은 것들. 계보의 <b>반대 방향</b> 조회다.
	 *
	 * <p>{@code Drift.predecessor} 는 후임 → 전임 단방향이라 전임 쪽에서는 자기 뒤에 무엇이
	 * 왔는지 알 수 없었다. 그래서 닫힌 드리프트를 열면 과거만 보이고, 그것이 <b>정말 끝난 것인지
	 * 뒤에 더 생긴 문제가 방치된 것인지</b> 구분할 방법이 없었다.</p>
	 *
	 * <p>여럿일 수 있다 — 하나가 닫히며 여러 종류가 함께 드러나는 fan-out 을 계보가 허용한다.</p>
	 */
	List<Drift> findByPredecessor(Drift predecessor);
}
