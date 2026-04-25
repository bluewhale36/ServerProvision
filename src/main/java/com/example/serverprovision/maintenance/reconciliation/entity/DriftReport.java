package com.example.serverprovision.maintenance.reconciliation.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
     * stale write 를 막는다. 충돌 발생 시 OptimisticLockException → GlobalExceptionHandler 가 409 매핑.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    /** 스캔이 시작된 시각. 정렬 기준 (DESC = 최근 우선). */
    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    /** 스캔 1회 소요시간. {@code Duration.ofMillis(...)} 등 그대로 보관. */
    @Column(name = "scan_duration_ms", nullable = false)
    private long scanDurationMs;

    /** deep scan 여부. true 면 manifestHash 재계산을 포함한 결과. */
    @Column(name = "deep", nullable = false)
    private boolean deep;

    /** 점검한 자원 총수 (드리프트 여부와 무관). */
    @Column(name = "total_checked", nullable = false)
    private int totalChecked;

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

    /** Service 가 빌더로 생성한 후 자식 drift 추가 시 호출. 양방향 매핑 동기화. */
    public void addDrift(Drift drift) {
        drifts.add(drift);
        drift.attachTo(this);
    }

    /** dismiss 시 자식 1건 분리 — orphanRemoval=true 라 DB 행도 삭제됨. */
    public void removeDrift(Drift drift) {
        drifts.remove(drift);
    }

    /** UI 응답용 — Duration 으로 변환. */
    public Duration getScanDuration() {
        return Duration.ofMillis(scanDurationMs);
    }

    public int getDriftCount() {
        return drifts.size();
    }

    /** UI 응답용 — 실패 root 를 List 로. NULL/빈 → 빈 리스트. */
    public List<String> getFailedScanRootList() {
        if (failedScanRoots == null || failedScanRoots.isEmpty()) return List.of();
        return List.of(failedScanRoots.split("\n"));
    }

    /** Service 가 스캔 결과 저장 시 호출. List → 줄바꿈 구분 문자열. */
    public void recordFailedScanRoots(List<String> roots) {
        if (roots == null || roots.isEmpty()) {
            this.failedScanRoots = null;
            return;
        }
        this.failedScanRoots = String.join("\n", roots);
    }
}
