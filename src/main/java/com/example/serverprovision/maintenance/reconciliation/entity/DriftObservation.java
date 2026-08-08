package com.example.serverprovision.maintenance.reconciliation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * MK4-1 — 한 점검 회차가 한 문제를 "또 봤다" 는 기록.
 *
 * <p>종전에는 {@link Drift} 한 행이 문제와 관측을 겸했다. 같은 문제가 세 번의 점검에서 발견되면
 * 서로 무관한 세 행이 되어, 언제부터 있었는지 · 이번에 새로 생겼는지를 알 방법이 없었다. 문제는
 * {@link Drift} 가 지속으로 들고, 회차별 사실은 여기 쌓인다.</p>
 *
 * <p>보고서가 담는 것이 드리프트에서 관측으로 바뀌면서, 지난 보고서의 기록이 사후에 줄어들던
 * 현상(진단 후보 2-4)도 함께 사라진다 — 해소는 문제 쪽 상태 전이일 뿐 관측을 지우지 않는다.</p>
 */
@Entity
@Table(
		name = "drift_observation",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_drift_observation_drift_report",
				columnNames = {"drift_id", "drift_report_id"}
		)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class DriftObservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "drift_id", nullable = false)
	private Drift drift;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "drift_report_id", nullable = false)
	private DriftReport report;

	@Column(name = "observed_at", nullable = false)
	private Instant observedAt;

	/**
	 * 그 회차 시점의 스냅샷. 문제 쪽에는 최신 값이 유지되고, 여기에는 당시 값이 남는다 —
	 * 경로가 여러 번 옮겨 다닌 문제의 이동 경로를 나중에 되짚을 수 있다.
	 */
	@Column(name = "old_path", nullable = false, length = 1024)
	private String oldPath;

	@Column(name = "new_path", length = 1024)
	private String newPath;

	@Column(name = "detail", length = 1024)
	private String detail;

	@Column(name = "observed_hash", length = 64)
	private String observedHash;

	/**
	 * 양방향 매핑 동기화 — {@code DriftReport.addObservation()} 이 호출한다.
	 */
	void attachTo(DriftReport report) {
		this.report = report;
	}
}
