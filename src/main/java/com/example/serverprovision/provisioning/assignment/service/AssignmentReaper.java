package com.example.serverprovision.provisioning.assignment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미소비 supersede 스냅샷의 지연 purge 스케줄 트리거(U3-2-a). 실제 로직은
 * {@link AssignmentReapService#purgeExpired()} 에 위임 — 본 클래스는 트리거 + 건수 로깅만 담당한다
 * ({@code OrphanQuarantineReaper} 동형).
 *
 * <p>cron 은 trash purge(:00) · orphan purge(:30) 와 겹치지 않게 :45 슬롯이 기본이다.
 * {@code assignment.reap.cron} 으로 override 하고, {@code assignment.reap.enabled=false} 면 트리거 빈 자체가
 * 생성되지 않는다(수거 로직 {@link AssignmentReapService} 는 별도 검증 · 수동 호출 대비 항상 존재).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "assignment.reap", name = "enabled", matchIfMissing = true)
public class AssignmentReaper {

    private final AssignmentReapService assignmentReapService;

    @Scheduled(cron = "${assignment.reap.cron:0 45 * * * *}")
    public void reap() {
        int purged = assignmentReapService.purgeExpired();
        if (purged > 0) {
            log.info("[assignment-reaper] 미소비 supersede 스냅샷 {} 건 수거 완료.", purged);
        }
    }
}
