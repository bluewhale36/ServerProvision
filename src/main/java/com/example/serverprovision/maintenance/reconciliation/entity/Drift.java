package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * MK1 — 단건 드리프트. {@link DriftReport} 의 1:N 자식. 보고서가 삭제되면 함께 사라진다.
 *
 * <p>도메인 메서드는 의도적으로 최소화 — 한번 기록된 드리프트는 (a) 자동 적용 후 보고서에서 제거 되거나,
 * (b) 사용자가 dismiss 로 무시 처리(보고서에서 제거) 되거나, (c) 보고서 prune 으로 함께 삭제 된다. 수정은 없다.</p>
 */
@Entity
@Table(name = "drift")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Drift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * (권고3) 낙관적 락. 동시 apply / dismiss 시 한 트랜잭션이 읽고 갱신하는 사이 다른 트랜잭션이
	 * 같은 행을 변경했다면 OptimisticLockException → 409 ConflictException 매핑.
	 */
	@Version
	@Column(name = "version", nullable = false)
	@Builder.Default
	private Long version = 0L;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "drift_report_id", nullable = false)
	private DriftReport report;

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 32)
	private ResourceType resourceType;

	/**
	 * 자원 PK (도메인별). 외부 식별자 — DB 무결성 제약 없음(여러 도메인 공용 칼럼).
	 */
	@Column(name = "resource_id", nullable = false)
	private Long resourceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 24)
	private DriftKind kind;

	/**
	 * DB 가 알고 있던 경로 (스캔 시점 기준).
	 */
	@Column(name = "old_path", nullable = false, length = 1024)
	private String oldPath;

	/**
	 * 재발견된 경로. PATH_DRIFT 일 때만 의미. 그 외엔 null.
	 */
	@Column(name = "new_path", length = 1024)
	private String newPath;

	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;

	/**
	 * 자유 텍스트 (SIGNATURE_INVALID 등의 변조 정황 메시지).
	 */
	@Column(name = "detail", length = 1024)
	private String detail;

	/**
	 * 양방향 매핑 동기화 — DriftReport.addDrift() 가 호출.
	 */
	void attachTo(DriftReport report) {
		this.report = report;
	}
}
