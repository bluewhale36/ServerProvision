package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanPopulation;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	 * MK4-4-2 — 이 회차가 들여다본 대상의 수. 활성 자원 · 삭제 상태 자원 · 짝 없는 마커 셋으로 나뉜다.
	 * <p>종전에는 활성 자원 수 하나(`totalChecked`)만 담아, 나머지 두 모집단에서 나온 드리프트가
	 * 목록에 실리면 "점검한 자원보다 문제가 많은" 화면이 됐다(진단 1-5).</p>
	 */
	@Embedded
	@Builder.Default
	private ScanPopulation population = ScanPopulation.EMPTY;

	/**
	 * HF4-4 — 그 회차에 몇 건을 봤는가. 보고서 생성 시점에 확정되는 역사적 사실이라 여기 보존한다
	 * ({@code Drift.display_name} 스냅샷과 동일 개념 — R9-5 선례).
	 * <p>MK4-1 — 종전에는 자식 드리프트가 해소될 때마다 물리 삭제되어 '미해결 잔수' 가 사후에 줄었고,
	 * 이 스냅샷이 그 왜곡을 막는 유일한 방벽이었다. 이제 자식이 관측이라 목록 자체가 줄지 않으므로
	 * 스냅샷과 관측 수가 항상 일치한다 — 대체 표기 fallback 은 그 역할이 사라져 제거됐다.
	 * 기록은 {@link #addObservation(DriftObservation)} 내부 증가.</p>
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
	 * MK4-4-2 — 이 회차가 실제로 뒤진 디렉토리 목록. 저장 방식은 위 실패 root 와 같다(줄바꿈 구분).
	 *
	 * <p>회차 상세가 "이 점검이 무엇을 했는가" 에 답하려면 범위를 알아야 한다. 그런데 종전에는
	 * <b>실패한 root 만</b> 남기고 성공한 범위는 어디에도 기록하지 않아, 지난 회차를 열어도 어디를
	 * 봤는지 알 수 없었다. 현재 설정으로 계산해 보여 주는 방법도 있으나 그것은 그 회차의 사실이
	 * 아니라 지금의 설정이라, 설정이 바뀐 뒤 지난 회차를 열면 화면이 거짓을 말하게 된다
	 * (결정 Q1 — 안 가).</p>
	 */
	@Column(name = "scanned_roots", length = 4096)
	private String scannedRoots;

	/**
	 * MK4-1 — 자식이 드리프트가 아니라 <b>관측</b>이다. 보고서는 그 회차에 무엇을 봤는지의 사진이고,
	 * 문제의 해소는 문제 쪽 상태 전이라 이 목록을 건드리지 않는다 — 지난 보고서의 건수가 사후에
	 * 줄어들던 현상(진단 후보 2-4)이 여기서 사라진다.
	 */
	@OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	@OrderBy("observedAt ASC")
	@Builder.Default
	private List<DriftObservation> observations = new ArrayList<>();

	/**
	 * 관측 1건 적재 + 양방향 동기화. 탐지 수 누적은 종전 {@code addDrift} 와 동일하게 여기서 한다 —
	 * "탐지 수 = 이 회차에 본 문제의 총수" 라는 스냅샷의 정의는 그대로다.
	 */
	public void addObservation(DriftObservation observation) {
		observations.add(observation);
		observation.attachTo(this);
		this.detectedDriftCount++;
	}

	/**
	 * MK4-4-2 — 이 회차가 본 문제들. 한 문제가 여러 번 관측돼도 한 번만 나온다.
	 *
	 * <p>관측은 회차마다 쌓이지만 문제는 하나다. "이 회차에서 무엇이 보였나" 에 답할 때 세어야 하는
	 * 것은 관측이 아니라 문제이므로, 그 추림을 회차가 직접 한다 — 묻는 쪽마다 다시 추리면 어느
	 * 화면에서는 같은 문제가 두 번 세어진다.</p>
	 */
	public List<Drift> observedDrifts() {
		Map<Long, Drift> byId = new LinkedHashMap<>();
		for (DriftObservation observation : observations) {
			byId.putIfAbsent(observation.getDrift().getId(), observation.getDrift());
		}
		return List.copyOf(byId.values());
	}

	/**
	 * UI 응답용 — Duration 으로 변환.
	 */
	public Duration getScanDuration() {
		return Duration.ofMillis(scanDurationMs);
	}


	/**
	 * UI 응답용 — 실패 root 를 List 로. NULL/빈 → 빈 리스트.
	 */
	public List<String> getFailedScanRootList() {
		return splitRoots(failedScanRoots);
	}

	/**
	 * Service 가 스캔 결과 저장 시 호출. List → 줄바꿈 구분 문자열.
	 */
	public void recordFailedScanRoots(List<String> roots) {
		this.failedScanRoots = joinRoots(roots);
	}

	/**
	 * MK4-4-2 — UI 응답용. 점검한 root 를 List 로. NULL/빈 → 빈 리스트.
	 * <p>도입 이전 회차는 비어 있다 — 화면은 "기록 없음" 으로 구분해 말한다.</p>
	 */
	public List<String> getScannedRootList() {
		return splitRoots(scannedRoots);
	}

	/** MK4-4-2 — Service 가 스캔 결과 저장 시 호출. */
	public void recordScannedRoots(Collection<String> roots) {
		this.scannedRoots = joinRoots(roots);
	}

	// 두 root 목록은 저장 방식이 같다 — 경로에 줄바꿈이 들어갈 수 없다는 같은 근거 위에 서 있으므로
	// 조립·해체를 한 곳에 둔다. 한쪽만 고치면 두 목록의 표현이 갈라진다.

	private static List<String> splitRoots(String packed) {
		if (packed == null || packed.isEmpty()) return List.of();
		return List.of(packed.split("\n"));
	}

	private static String joinRoots(Collection<String> roots) {
		if (roots == null || roots.isEmpty()) return null;
		return String.join("\n", roots);
	}
}
