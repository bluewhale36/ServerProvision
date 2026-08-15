package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.maintenance.reconciliation.dto.response.BulkApplyResponse;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftReportNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MK4-4-2 — 여러 문제를 한 번에 해결한다.
 *
 * <p>12 종이 섞인 목록에서 하나씩 눌러 내려가는 것이 실제 마찰이라는 것이 진단의 관찰이었다. 이
 * 서비스는 그 반복을 없앤다.</p>
 *
 * <h3>왜 별도 빈인가 — 건별 트랜잭션 격리</h3>
 * <p>일괄이 한 트랜잭션이면 마지막 한 건의 실패가 앞의 성공 전부를 되돌린다. 파일시스템을 건드리는
 * 해결에서 그것은 되돌릴 수 없는 되돌리기다 — 파일은 이미 옮겨졌는데 DB 만 원래대로 가므로 오히려
 * 새 불일치를 만든다. 그래서 <b>이 클래스의 실행 메서드에는 트랜잭션을 걸지 않는다.</b>
 * {@link PathReconciliationService#apply(Long)} 이 다른 빈이라 호출마다 프록시를 지나며 자기
 * 트랜잭션을 열고, 실패는 그 한 건 안에서만 되돌아간다.</p>
 *
 * <p>같은 클래스 안에 두면 이 격리가 성립하지 않는다. 자기 메서드 호출은 프록시를 지나지 않아
 * 트랜잭션이 하나로 합쳐지기 때문이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriftBulkApplyService {

	private final PathReconciliationService reconciliationService;
	private final DriftRepository driftRepository;
	private final DriftReportRepository driftReportRepository;

	/**
	 * 후보 A — 그 회차가 관측한 문제 중 지금 해결할 수 있는 것.
	 *
	 * <p>회차가 담는 것은 관측이고 한 문제가 여러 회차에 관측될 수 있으므로 신원 기준으로 한 번씩만
	 * 추린다. 지난 회차를 열어 누르면 그 회차에서 보였던 문제 중 아직 남은 것들이 대상이 된다 —
	 * 이미 닫힌 것은 {@link Drift#bulkResolvable()} 이 걸러 낸다.</p>
	 */
	@Transactional(readOnly = true)
	public List<Long> targetsInReport(Long reportId) {
		return driftReportRepository.findById(reportId)
				.orElseThrow(() -> new DriftReportNotFoundException(reportId))
				.observedDrifts().stream()
				.filter(Drift::bulkResolvable)
				.map(Drift::getId)
				.toList();
	}

	/**
	 * 후보 B — 지금 목록에 떠 있는 문제 중 해결할 수 있는 것 전부.
	 *
	 * <p>대상이 회차가 아니라 현재 상태라, 첫 화면이 미해결 목록인 배치에서 "보이는 것을 다 처리"
	 * 와 정확히 같은 범위가 된다.</p>
	 */
	@Transactional(readOnly = true)
	public List<Long> openTargets() {
		Instant now = Instant.now();
		return driftRepository.findByStatusNot(DriftStatus.RESOLVED).stream()
				.filter(drift -> drift.isListed(now))
				.filter(Drift::bulkResolvable)
				.map(Drift::getId)
				.toList();
	}

	/**
	 * 집어 온 대상을 하나씩 해결한다. <b>의도적으로 트랜잭션이 없다</b>(클래스 주석 참고).
	 *
	 * <p>한 건이 실패해도 멈추지 않는다. 남은 것을 마저 처리하는 편이 사용자에게 낫고, 실패한 건은
	 * 사유와 함께 돌려주므로 무엇이 남았는지 알 수 있다. 예외를 삼키는 것이 아니라 결과로 옮기는
	 * 것이다 — 삼켰다면 화면이 전부 성공했다고 말했을 것이다.</p>
	 */
	public BulkApplyResponse applyAll(List<Long> driftIds) {
		int applied = 0;
		List<String> failures = new ArrayList<>();
		for (Long driftId : driftIds) {
			try {
				reconciliationService.apply(driftId);
				applied++;
			} catch (RuntimeException e) {
				log.warn("[reconciliation] 일괄 해결 중 1 건 실패 — driftId={}", driftId, e);
				failures.add("#" + driftId + " — " + e.getMessage());
			}
		}
		return new BulkApplyResponse(driftIds.size(), applied, List.copyOf(failures));
	}
}
