package com.example.serverprovision.global.trash.enums;

/**
 * S5-2-4 — 휴지통 자동 hard-delete 의 사전/실패 알림 채널.
 *
 * <p><strong>D5 메모</strong>: 향후 Provisioning / Execution 도메인의 서버 상태 알림이
 * 도입되면 본 enum + {@link com.example.serverprovision.global.trash.service.NotificationDispatcher}
 * 인터페이스가 별도 알림 도메인으로 이관 예정. EMAIL / SLACK / WEBHOOK 은 그 시점에 추가.</p>
 *
 * <p>현재 슬라이스는 어플리케이션 내부 채널 2종만 지원.</p>
 */
public enum NotifyChannel {

    /** navbar 우측 작업 조회 아이콘 (서류가방) 패널의 Job 카드. */
    BACKGROUND_JOB,

    /** SLF4J 서버 로그 (INFO/WARN/ERROR). 운영자가 별도 로그 수집기로 흡수. */
    SERVER_LOG
}
