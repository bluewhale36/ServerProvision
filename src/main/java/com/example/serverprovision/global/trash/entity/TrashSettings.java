package com.example.serverprovision.global.trash.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.global.trash.dto.request.TrashSettingsRequest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * S5-2-4 — 휴지통 운영 설정. 단일행 (singleton) entity.
 *
 * <p><strong>Singleton 보장</strong> : id 값 고정 (1L) 강제 대신 {@code count() == 1} 검사 기반
 * (사용자 결정 2026-05-20). {@link com.example.serverprovision.global.trash.service.TrashSettingsService}
 * 가 application start 시점에 count() 로 확인 → 0 이면 default 시드, &gt;=2 이면 첫 row 만 신뢰
 * + 경고 로그.</p>
 *
 * <p>{@link #updatedAt} 은 BaseTimeEntity 의 자동 갱신 컬럼으로 마지막 운영자 수정 시각을 흡수.</p>
 */
@Entity
@Table(name = "trash_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrashSettings extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ttl_days", nullable = false)
    private int ttlDays;

    @Column(name = "auto_purge_enabled", nullable = false)
    private boolean autoPurgeEnabled;

    @Column(name = "purge_cron_expression", nullable = false, length = 64)
    private String purgeCronExpression;

    @Column(name = "notify_cron_expression", nullable = false, length = 64)
    private String notifyCronExpression;

    /** 콤마 구분 D-day 리스트. 예: "7,1". service 단에서 split → int 셋. */
    @Column(name = "notify_days_before", nullable = false, length = 64)
    private String notifyDaysBefore;

    /** 콤마 구분 NotifyChannel enum 이름 셋. 예: "BACKGROUND_JOB,SERVER_LOG". */
    @Column(name = "notification_channels", nullable = false, length = 128)
    private String notificationChannels;

    @Column(name = "retry_max_attempts", nullable = false)
    private int retryMaxAttempts;

    @Column(name = "retry_backoff_base_ms", nullable = false)
    private long retryBackoffBaseMs;

    @Builder
    private TrashSettings(int ttlDays, boolean autoPurgeEnabled,
                          String purgeCronExpression, String notifyCronExpression,
                          String notifyDaysBefore, String notificationChannels,
                          int retryMaxAttempts, long retryBackoffBaseMs) {
        this.ttlDays = ttlDays;
        this.autoPurgeEnabled = autoPurgeEnabled;
        this.purgeCronExpression = purgeCronExpression;
        this.notifyCronExpression = notifyCronExpression;
        this.notifyDaysBefore = notifyDaysBefore;
        this.notificationChannels = notificationChannels;
        this.retryMaxAttempts = retryMaxAttempts;
        this.retryBackoffBaseMs = retryBackoffBaseMs;
    }

    /**
     * 초기 시드용 default 값. count()==0 일 때 {@code TrashSettingsService} 가 본 팩토리로 row 생성.
     */
    public static TrashSettings defaults() {
        return TrashSettings.builder()
                .ttlDays(30)
                .autoPurgeEnabled(true)
                .purgeCronExpression("0 0 * * * *")
                .notifyCronExpression("0 0 * * * *")
                .notifyDaysBefore("7,1")
                .notificationChannels("BACKGROUND_JOB,SERVER_LOG")
                .retryMaxAttempts(3)
                .retryBackoffBaseMs(1000L)
                .build();
    }

    /**
     * 운영자 갱신. 모든 컬럼을 한 번에 교체 — partial update 안 함 (운영 설정은 always-full).
     */
    public void apply(TrashSettingsRequest request) {
        this.ttlDays = request.ttlDays();
        this.autoPurgeEnabled = request.autoPurgeEnabled();
        this.purgeCronExpression = request.purgeCronExpression();
        this.notifyCronExpression = request.notifyCronExpression();
        this.notifyDaysBefore = request.notifyDaysBefore();
        this.notificationChannels = request.notificationChannels();
        this.retryMaxAttempts = request.retryMaxAttempts();
        this.retryBackoffBaseMs = request.retryBackoffBaseMs();
    }
}
