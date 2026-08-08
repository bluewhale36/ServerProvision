package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftHandlingAction;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.enums.SnoozeWindow;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 자원 하나에 생긴 문제 하나. 고쳐질 때까지 지속된다.
 *
 * <p><b>MK4-1 — 의미가 바뀌었다.</b> 종전에는 이 행이 "이번 점검에서 발견된 것" 이라는 관측이자
 * 문제였다. 같은 문제가 세 번의 점검에서 발견되면 서로 무관한 세 행이 되어, 언제부터 있었는지 ·
 * 이번에 새로 생겼는지를 알 방법이 없었고(진단 후보 2-1), 해소하면 그 행이 보고서에서 떨어져 나가
 * 지난 보고서의 건수가 사후에 줄었다(후보 2-4).</p>
 *
 * <p>이제 이 행은 <b>문제</b>다. 신원은 자원 종류 · 자원 번호 · 드리프트 종류 셋이고, 같은 신원의
 * 열린 문제는 동시에 하나만 존재한다. 회차별 사실은 {@link DriftObservation} 이 갖고, 가해진 처리는
 * {@link DriftHandling} 이 갖는다. 보고서를 가리키던 연결은 제거됐다 — 이제 관측이 보고서를 가리킨다.</p>
 *
 * <p>상태 전이는 전부 이 클래스의 메서드다. 서비스는 호출만 한다 — 전이 규칙이 여러 서비스에
 * 흩어지면 "해결됐는데 두고 보기" 같은 불법 조합이 생길 자리가 열린다.</p>
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

	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 32)
	private ResourceType resourceType;

	/**
	 * 자원 PK (도메인별). 외부 식별자 — DB 무결성 제약 없음(여러 도메인 공용 칼럼).
	 */
	@Column(name = "resource_id", nullable = false)
	private Long resourceId;

	/**
	 * R9-5 — 스캔 시점의 자원 표시명 스냅샷({@code Markable.displayName()}). 자원이 이후 purge 되어도
	 * 보고서에 이름이 남는다 — {@code PurgeLog.display_name} 과 동일 개념·동일 명명. ORPHAN 처럼
	 * DB 매칭 자원이 없는 drift 는 마커가 가리키는 본체 파일명이 fallback. 도입 이전 행은 null
	 * (백필 없음 — 화면이 종전 "TYPE #id" 표기로 fallback, FIFO retention 으로 자연 해소).
	 */
	@Column(name = "display_name", length = 255)
	private String displayName;

	@Enumerated(EnumType.STRING)
	// S6-2-2 — SOFTDEL_ESCAPE_TO_ORIGINAL(26자) 수용을 위해 24→32 확장 (sql/S6-2-2_drift_kind_widen.sql 동반)
	@Column(name = "kind", nullable = false, length = 32)
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

	/**
	 * MK4-1 — 이 문제를 <b>처음</b> 본 시각. 종전 {@code detectedAt}("이 회차에 봤다")의 개명이다.
	 * 의미가 바뀌었으므로 이름도 바꾼다 — 옛 이름을 그대로 두면 "언제부터 있었나" 를 읽는 코드가
	 * 회차 시각으로 오해한다.
	 */
	@Column(name = "first_detected_at", nullable = false)
	private Instant firstDetectedAt;

	/**
	 * 마지막으로 관측된 시각. 점검이 이 문제를 다시 볼 때마다 갱신된다.
	 */
	@Column(name = "last_observed_at", nullable = false)
	private Instant lastObservedAt;

	/**
	 * 관측 횟수. 순수 계수라 원시형을 쓴다({@code DriftReport.totalChecked} 선례).
	 */
	@Column(name = "observation_count", nullable = false)
	@Builder.Default
	private int observationCount = 1;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	@Builder.Default
	private DriftStatus status = DriftStatus.OPEN;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "resolved_by", length = 32)
	private DriftHandlingAction resolvedBy;

	/**
	 * 두고 보기 만료 시각. 조건형(다음 정밀 점검까지)은 시각이 아니라 사건으로 풀리므로 null 이고,
	 * {@link #snoozeWindow} 가 그 조건을 들고 있다.
	 */
	@Column(name = "snooze_until")
	private Instant snoozeUntil;

	@Enumerated(EnumType.STRING)
	@Column(name = "snooze_window", length = 32)
	private SnoozeWindow snoozeWindow;

	@Column(name = "snooze_reason", length = 500)
	private String snoozeReason;

	/**
	 * 자유 텍스트 (SIGNATURE_INVALID 등의 변조 정황 메시지).
	 */
	@Column(name = "detail", length = 1024)
	private String detail;

	/**
	 * S6-3-4 — HASH_MISMATCH 전용: 감지 시점(정밀 점검)에 재계산된 현재 내용의 지문 스냅샷.
	 * 수용([정본으로 수용]) 실행 시 재계산 값과 대조해, 사용자가 확인한 내용과 다른 것이
	 * 정본화되는 사고를 차단한다 (그 사이 파일이 또 바뀌면 거절 — Tripwire high-security 등가).
	 * 다른 종류의 drift 에서는 null.
	 */
	@Column(name = "observed_hash", length = 64)
	private String observedHash;

	/* ═══════════ MK4-1 — 상태 전이. 서비스는 호출만 한다 ═══════════ */

	/**
	 * 점검이 이 문제를 다시 관측했다. 최신 스냅샷을 덮어쓰고 수명 값을 갱신한다.
	 *
	 * <p>조건형 두고 보기('다음 정밀 점검까지')는 정밀 점검이 관측하는 순간 풀린다 — 시각이 아니라
	 * 사건으로 만료되는 유일한 경우라 여기서 다룬다.</p>
	 */
	public void observe(Instant at, String oldPath, String newPath, String detail,
			String observedHash, String displayName, boolean deepScan) {
		this.lastObservedAt = at;
		this.observationCount++;
		this.oldPath = oldPath;
		this.newPath = newPath;
		this.detail = detail;
		this.observedHash = observedHash;
		if (displayName != null) this.displayName = displayName;
		if (status == DriftStatus.SNOOZED && snoozeWindow != null
				&& snoozeWindow.releasedByDeepScan() && deepScan) {
			reopen();
		}
	}

	/**
	 * 해소로 닫는다. 어떤 처리로 닫혔는지를 함께 남긴다 — 나중에 이력을 읽을 때 "누가 닫았나" 가
	 * 상태만으로는 드러나지 않기 때문이다.
	 */
	public void resolve(Instant at, DriftHandlingAction by) {
		this.status = DriftStatus.RESOLVED;
		this.resolvedAt = at;
		this.resolvedBy = by;
		this.snoozeUntil = null;
		this.snoozeWindow = null;
	}

	/**
	 * 지금은 두고 보기. 기간형이면 만료 시각이 정해지고, 조건형이면 다음 정밀 점검이 풀어 준다.
	 */
	public void snooze(SnoozeWindow window, String reason, Instant now) {
		this.status = DriftStatus.SNOOZED;
		this.snoozeWindow = window;
		this.snoozeUntil = window.expiresAt(now);
		this.snoozeReason = reason;
	}

	/**
	 * 다시 열림 — 두고 보기 만료 또는 수동 해제.
	 */
	public void reopen() {
		this.status = DriftStatus.OPEN;
		this.snoozeUntil = null;
		this.snoozeWindow = null;
		this.snoozeReason = null;
	}

	/**
	 * 두고 보기가 만료됐는가. 조건형은 시각으로 판정하지 않는다.
	 */
	public boolean isSnoozeExpired(Instant now) {
		return status == DriftStatus.SNOOZED && snoozeUntil != null && !snoozeUntil.isAfter(now);
	}

	/**
	 * 지금 목록에 떠야 하는가. 열려 있거나, 두고 보기인데 만료된 것.
	 */
	public boolean isListed(Instant now) {
		return status == DriftStatus.OPEN || isSnoozeExpired(now);
	}

	/**
	 * 두고 보기를 걸 수 없는 사유. null 이면 가능하다 — 화면의 버튼 비활성 사유와 서버 가드가
	 * 이 한 메서드를 함께 본다(UI 1차 차단의 단일 소스).
	 */
	public String snoozeBlockReason() {
		if (status == DriftStatus.RESOLVED) return "이미 해결된 드리프트라 보관할 것이 없습니다.";
		if (status == DriftStatus.SNOOZED) return "이미 보관 중인 드리프트입니다.";
		return null;
	}

	/**
	 * 해결할 수 없는 사유. null 이면 가능하다 — {@link #snoozeBlockReason()} 과 같은 방식으로
	 * 화면의 버튼 비활성 사유와 서버 가드가 이 한 메서드를 함께 본다.
	 *
	 * <p>지난 회차의 보고서를 열면 이미 닫힌 문제도 상세에 뜨므로 [해결] 버튼이 눈앞에 있다.
	 * 눌러 봐야 거절만 돌아오니 애초에 못 누르게 하고 이유를 보여 준다.</p>
	 *
	 * <p>두고 보기 상태는 막지 않는다 — 미뤄 둔 문제를 곧바로 해결하는 것은 정상 흐름이다.</p>
	 */
	public String resolveBlockReason() {
		if (status == DriftStatus.RESOLVED) return "이미 해결된 드리프트입니다.";
		return null;
	}
}
