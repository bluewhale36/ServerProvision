package com.example.serverprovision.maintenance.trash.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.trash.TrashPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * MK3 — TTL 30일 경과 자원 자동 hard-delete worker (DCN4 b).
 *
 * <p>{@link MarkableScanner} SPI 다형성 활용 — 4 도메인 scanner 각자 trashed 자원 조회/만료 검사를 수행하고,
 * 본 worker 는 도메인 모르고 합산만 한다. 도메인 분기 응집 (CLAUDE.md §조건 분기문 정합).</p>
 *
 * <p>Hard-delete 실제 본체 (trash 파일 + DB 행 정리) 는 후속 sub-slice (S5-2-2) 의 typed-name purge 와
 * 합쳐진다 — 본 worker 는 만료 자원 식별 + 알림 까지만. 자동 hard-delete 의 정확한 정책은
 * S5-2-2 와 함께 결정 (DCN-NEW7 b 의 retry 정합 + 운영 안전).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrashTtlWorker {

    private final TrashPolicy trashPolicy;
    private final List<MarkableScanner> scanners;

    /**
     * TTL 만료 자원 식별. CP4 단계는 식별 + 알림만, 실제 자동 hard-delete 는 S5-2-2 와 합쳐 결정.
     */
    @Scheduled(cron = "${trash.ttl.purge-cron:0 0 * * * *}")
    public void purgeExpired() {
        Instant threshold = Instant.now().minus(trashPolicy.getTtl());
        for (MarkableScanner scanner : scanners) {
            List<Markable> expired = scanner.findTrashedBefore(threshold);
            if (!expired.isEmpty()) {
                log.warn("[trash:ttl] type={} expired count={} (threshold={}). 자동 hard-delete 는 S5-2-2 와 합쳐 결정 — 현재는 보고만.",
                        scanner.supportedType(), expired.size(), threshold);
            }
        }
    }

    /**
     * TTL 7일 / 1일 전 알림 (DCN-NEW9 b). 작업 조회 아이콘에 알림 등록.
     */
    @Scheduled(cron = "${trash.ttl.notify-cron:0 0 * * * *}")
    public void notifyUpcomingExpiration() {
        Instant now = Instant.now();
        Duration ttl = trashPolicy.getTtl();

        // 7일 전 — trashedAt 가 (now - TTL + 7일) 직전 시점
        Instant sevenDaysWindowStart = now.minus(ttl).plus(Duration.ofDays(6));
        Instant sevenDaysWindowEnd = now.minus(ttl).plus(Duration.ofDays(7));
        // 1일 전 — trashedAt 가 (now - TTL + 1일) 직전 시점
        Instant oneDayWindowStart = now.minus(ttl).plus(Duration.ofDays(0));
        Instant oneDayWindowEnd = now.minus(ttl).plus(Duration.ofDays(1));

        for (MarkableScanner scanner : scanners) {
            List<Markable> sevenDays = scanner.findTrashedBetween(sevenDaysWindowStart, sevenDaysWindowEnd);
            List<Markable> oneDay = scanner.findTrashedBetween(oneDayWindowStart, oneDayWindowEnd);
            if (!sevenDays.isEmpty()) {
                log.info("[trash:ttl] type={} 7일 후 영구삭제 예정 자원 {}건", scanner.supportedType(), sevenDays.size());
            }
            if (!oneDay.isEmpty()) {
                log.warn("[trash:ttl] type={} 1일 이내 영구삭제 예정 자원 {}건", scanner.supportedType(), oneDay.size());
            }
        }
    }
}
