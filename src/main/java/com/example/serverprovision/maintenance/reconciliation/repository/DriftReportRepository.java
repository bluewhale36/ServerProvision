package com.example.serverprovision.maintenance.reconciliation.repository;

import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriftReportRepository extends JpaRepository<DriftReport, Long> {

	/**
	 * 가장 최근 1 건. {@code findFirstBy...OrderBy} 로 LIMIT 1.
	 */
	Optional<DriftReport> findFirstByOrderByScannedAtDesc();

	/**
	 * 페이지네이션 이력. Pageable 의 sort 가 scannedAt DESC 인 게 일반적.
	 */
	Page<DriftReport> findAllBy(Pageable pageable);

	/**
	 * FIFO prune 용 — 특정 보관 한도를 넘는 오래된 보고서 N 건 삭제.
	 * 호출 측에서 {@code count() - retentionCount} 만큼 삭제 — 본 인터페이스는 가장 오래된 N 건 조회만 제공.
	 */
	Page<DriftReport> findAllByOrderByScannedAtAsc(Pageable pageable);

	/**
	 * MK4-2 — 마지막으로 파일 내용까지 확인한 점검. 화면이 "언제 이후로 내용을 안 봤는지" 를
	 * 항상 함께 보여 주기 위한 값이다. 정밀 점검 이력이 없으면 비어 있다.
	 */
	Optional<DriftReport> findFirstByDeepTrueOrderByScannedAtDesc();

	/**
	 * MK4-3-2 — 마지막 일반 점검. 주기가 실측 소요 시간보다 짧은지 알리기 위해 쓴다.
	 *
	 * <p>가장 최근 점검({@link #findFirstByOrderByScannedAtDesc})으로 대신할 수 없다. 그것이 정밀
	 * 점검이면 소요 시간이 일반 점검의 몇 배라 없는 경고를 띄우게 된다.</p>
	 */
	Optional<DriftReport> findFirstByDeepFalseOrderByScannedAtDesc();

	/**
	 * MK4-4-3 — 어떤 시각 이후로 <b>내용을 보지 않고 지나간</b> 점검 횟수.
	 *
	 * <p>드리프트 상세가 "이 판정을 언제 확인했고, 그 뒤로 몇 번이나 안 봤는가" 를 말하는 데 쓴다.
	 * 횟수가 시각보다 잘 읽히는 자리다 — 「01:26 기준」 만으로는 그것이 방금인지 한참 전인지
	 * 가늠하려면 현재 시각과 점검 주기를 함께 알아야 한다.</p>
	 */
	long countByDeepFalseAndScannedAtAfter(java.time.Instant after);

}
