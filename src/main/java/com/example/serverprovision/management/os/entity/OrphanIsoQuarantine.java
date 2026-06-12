package com.example.serverprovision.management.os.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.os.enums.OrphanFailureClass;
import com.example.serverprovision.management.os.enums.OrphanRecoveryState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인프라/일시 실패로 등록이 좌절된 ISO 업로드의 <b>durable</b> 복구 레코드 (recoverable saga).
 *
 * <p>오펀(파일은 디스크에 있으나 DB row·마커 없음)은 마커가 없어 reconciliation 으로 탐지 불가하고,
 * background job 은 10분 후 in-memory 에서 prune 되므로, 복구 결정(재시도/폐기)이 살아남으려면 별도 영속 레코드가 필요하다.</p>
 *
 * <p>trash 테이블을 쓰지 않는 이유: trash 는 (resourceType, resourceId=PK) 로 키잉되는데 오펀은 PK 가 없다.
 * PurgeLog 도 쓰지 않는다: append-only 감사라 PENDING→RECOVERED/DISCARDED 같은 가변 상태를 담을 수 없다.</p>
 *
 * @implNote 향후 Audit Event Bus 도입 시 흡수될 stop-gap 레코드 (CLAUDE.md §후속 후보).
 */
@Entity
@Table(
		name = "orphan_iso_quarantine",
		indexes = {
				@Index(name = "ux_orphan_recovery_id", columnList = "recovery_id", unique = true),
				@Index(name = "ix_orphan_state_created", columnList = "state, created_at")
		}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrphanIsoQuarantine extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 복구 액션/모달의 키. nudgeId 와 같은 역할. UUID 문자열. */
	@Column(name = "recovery_id", nullable = false, unique = true, length = 36)
	private String recoveryId;

	@Column(name = "os_metadata_id", nullable = false)
	private Long osMetadataId;

	/** 등록하려던 active 경로 (재시도 시 복원 대상). */
	@Column(name = "resolved_path", nullable = false, length = 1024)
	private String resolvedPath;

	/** 격리된 파일의 현재 경로. registerExisting 이거나 격리 자체 실패 시 null. */
	@Column(name = "quarantine_path", length = 1024)
	private String quarantinePath;

	@Column(name = "original_filename", nullable = false, length = 512)
	private String originalFilename;

	@Column(name = "description", length = 1024)
	private String description;

	@Column(name = "client_hash", length = 128)
	private String clientHash;

	/** {@code !uploadedFile} — 운영자의 기존 파일(in-place)이면 격리하지 않고 제자리에서 재시도. */
	@Column(name = "register_existing", nullable = false)
	private boolean registerExisting;

	@Enumerated(EnumType.STRING)
	@Column(name = "failure_class", nullable = false, length = 32)
	private OrphanFailureClass failureClass;

	@Column(name = "exception_detail", length = 2048)
	private String exceptionDetail;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 16)
	private OrphanRecoveryState state;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	/** 격리를 만든 in-memory job (살아있는 동안 UI 상관용). prune 후엔 의미 없음. */
	@Column(name = "job_id", length = 64)
	private String jobId;

	/** 재시도 1회 — 상태를 RECOVERED 로 소비하고 카운트 증가. */
	public void markRecovered() {
		this.state = OrphanRecoveryState.RECOVERED;
		this.retryCount += 1;
	}

	public void markDiscarded() {
		this.state = OrphanRecoveryState.DISCARDED;
	}

	public boolean isPending() {
		return state == OrphanRecoveryState.PENDING;
	}
}
