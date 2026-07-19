package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * MK1 — 1 회 스캔 결과의 메타데이터. 자식 {@link Drift} 와 1:N 관계.
 * <p>이력 보관 정책 (D15): 최대 N건 (default 100) FIFO. 새 보고서 생성 시 카운트 초과면 가장 오래된 행 삭제.</p>
 *
 * <p>이 엔티티는 영속화되지만 도메인 로직은 거의 없다 — 주로 read-mostly. 생성은
 * {@link com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService} 가
 * 빌더로 한 번에 만들고 그 후엔 거의 변경되지 않는다 (자식 drift 의 dismiss 로 자식만 삭제될 뿐).</p>
 */
@Entity
@Table(name = "drift_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DriftReport extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * (권고3) 낙관적 락 — 동시 dismiss / apply / 자동 적용이 같은 보고서의 자식 drift 를 동시에 건드릴 때
	 * stale write 를 막는다. 충돌 발생 시 OptimisticLockException → WebExceptionHandler 가 409 매핑.
	 */
	@Version
	@Column(name = "version", nullable = false)
	@Builder.Default
	private Long version = 0L;

	/**
	 * 스캔이 시작된 시각. 정렬 기준 (DESC = 최근 우선).
	 */
	@Column(name = "scanned_at", nullable = false)
	private Instant scannedAt;

	/**
	 * 스캔 1회 소요시간. {@code Duration.ofMillis(...)} 등 그대로 보관.
	 */
	@Column(name = "scan_duration_ms", nullable = false)
	private long scanDurationMs;

	/**
	 * deep scan 여부. true 면 manifestHash 재계산을 포함한 결과.
	 */
	@Column(name = "deep", nullable = false)
	private boolean deep;

	/**
	 * 점검한 자원 총수 (드리프트 여부와 무관).
	 */
	@Column(name = "total_checked", nullable = false)
	private int totalChecked;

	/**
	 * HF4-4 — 스캔 시점 탐지 건수 스냅샷. 자식 drift 는 apply/dismiss 로 물리 삭제되어
	 * ({@code orphanRemoval=true}) {@link #getDriftCount()} 가 '미해결 잔수'로 줄어들지만,
	 * "그 스캔에서 몇 건이 탐지됐었는가"는 보고서 생성 시점에 확정되는 역사적 사실이라 여기 보존한다
	 * ({@code Drift.display_name} 스냅샷과 동일 개념 — R9-5 선례).
	 * <p>기록은 {@link #addDrift(Drift)} 내부 증가 — 저장 호출부(performScan / persistAndForcedApply)가
	 * 각각 기억할 필요가 없고, JPA 로딩은 컬렉션 직주입이라 재로딩 시 증가 부작용이 없다.
	 * 도입 이전 행은 0 (backfill 없음 — 화면이 {@link #getDetectedDriftCountForDisplay()} 로
	 * 미해결 수 대체 표기, FIFO retention 으로 자연 해소).</p>
	 */
	@Column(name = "detected_drift_count", nullable = false)
	@Builder.Default
	private int detectedDriftCount = 0;

	/**
	 * (권고6) 스캔 도중 walk 가 실패한 root 디렉토리 목록. 권한 부족 / 마운트 누락 / 디렉토리 부재 등.
	 * 줄바꿈(\n) 구분 — Linux 경로에 줄바꿈이 들어갈 수 없어 구분자로 안전. NULL 또는 빈 문자열이면 부분 실패 없음.
	 * UI / API 가 비어있지 않은 경우 운영자에게 경고 표시 — "보고서가 부분 결과일 수 있다".
	 */
	@Column(name = "failed_scan_roots", length = 4096)
	private String failedScanRoots;

	/**
	 * 자식 drift 들. cascade ALL + orphanRemoval 로 보고서 삭제 시 자식도 함께 삭제. dismiss 는 자식 1건 분리로 처리.
	 * detected_at ASC 로 정렬 — 스캔 안에서의 발견 순.
	 */
	@OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	@OrderBy("detectedAt ASC")
	@Builder.Default
	private List<Drift> drifts = new ArrayList<>();

	/**
	 * Service 가 빌더로 생성한 후 자식 drift 추가 시 호출. 양방향 매핑 동기화.
	 * <p>HF4-4 — 탐지 스냅샷도 여기서 함께 누적한다. {@link #removeDrift(Drift)} 는 감소시키지 않는다 —
	 * "탐지 수 = 이 보고서에 추가된 적 있는 drift 총수"가 스냅샷의 정의.</p>
	 */
	public void addDrift(Drift drift) {
		drifts.add(drift);
		drift.attachTo(this);
		this.detectedDriftCount++;
	}

	/**
	 * dismiss 시 자식 1건 분리 — orphanRemoval=true 라 DB 행도 삭제됨.
	 */
	public void removeDrift(Drift drift) {
		drifts.remove(drift);
	}

	/**
	 * UI 응답용 — Duration 으로 변환.
	 */
	public Duration getScanDuration() {
		return Duration.ofMillis(scanDurationMs);
	}

	public int getDriftCount() {
		return drifts.size();
	}

	/**
	 * HF4-4 — 화면·API 표기용 탐지 건수. 스냅샷 도입 이전 행(컬럼 default 0)은 미해결 잔수로 대체한다.
	 * 신행에서 스냅샷 0 은 "탐지 0건"이고 그때 잔수도 반드시 0(자식은 추가 없이 줄기만 한다)이라
	 * 이 대체는 항상 참 — 0 경계의 의미 충돌이 없다. 템플릿 2곳(C1 배지·요약줄)이 각자 fallback 을
	 * 복붙하지 않도록 이 메서드가 단일 SSOT.
	 */
	public int getDetectedDriftCountForDisplay() {
		return detectedDriftCount > 0 ? detectedDriftCount : drifts.size();
	}

	/**
	 * UI 응답용 — 실패 root 를 List 로. NULL/빈 → 빈 리스트.
	 */
	public List<String> getFailedScanRootList() {
		if (failedScanRoots == null || failedScanRoots.isEmpty()) return List.of();
		return List.of(failedScanRoots.split("\n"));
	}

	/**
	 * Service 가 스캔 결과 저장 시 호출. List → 줄바꿈 구분 문자열.
	 */
	public void recordFailedScanRoots(List<String> roots) {
		if (roots == null || roots.isEmpty()) {
			this.failedScanRoots = null;
			return;
		}
		this.failedScanRoots = String.join("\n", roots);
	}
}
