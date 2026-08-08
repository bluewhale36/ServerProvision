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
	 * 지금 목록에 떠야 하는 문제의 수. 종전에는 보고서의 자식 수를 셌으나 그 값은 사후에 변했다.
	 */
	long countByStatus(DriftStatus status);
}
