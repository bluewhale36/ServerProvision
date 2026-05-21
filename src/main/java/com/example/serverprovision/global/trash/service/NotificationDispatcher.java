package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.trash.enums.NotifyChannel;

import java.util.Set;

/**
 * S5-2-4 — 휴지통 자동 hard-delete 의 사전/실패 알림 발송 SPI.
 *
 * <p><strong>D5 메모</strong>: 본 인터페이스는 향후 Provisioning / Execution 도메인의 서버 상태
 * 알림이 도입되면 별도 알림 도메인으로 이관 예정. 본 슬라이스는 BackgroundJob 어댑터 1종만
 * 구현체로 두되, 이관 시점에 EMAIL / SLACK / WEBHOOK adapter 가 추가될 수 있도록 인터페이스
 * 단계에서 채널 셋을 받는 구조로 설계.</p>
 *
 * <p>본 인터페이스의 모든 구현체는 trash_settings 의 notification_channels 컬럼을 참조하여
 * 자기 채널이 활성인 경우에만 발송 — 채널 분기는 인프라가 담당, Dispatcher 호출측은 도메인 모름.</p>
 */
public interface NotificationDispatcher {

	/**
	 * 사건 발생 알림. JobType 별 다형성 — {@link JobType} 자체에 displayName 이 있으므로 본 인터페이스는
	 * type + 자원 식별자 + 메시지만 받는다.
	 *
	 * @param type           알림 종류 (TTL_NOTIFY / TRASH_AUTO_PURGE / TRASH_PURGE_FAILED 등)
	 * @param activeChannels 현재 trash_settings.notification_channels 의 활성 셋
	 * @param title          알림 제목 (Job 카드 본문 등에 사용)
	 * @param body           상세 메시지 (NULL 가능)
	 */
	void dispatch(JobType type, Set<NotifyChannel> activeChannels, String title, String body);
}
