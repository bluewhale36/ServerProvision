package com.example.serverprovision.global.trash.service.internal;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.trash.enums.NotifyChannel;
import com.example.serverprovision.global.trash.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * S5-2-4 CP4 — NotificationDispatcher 의 BackgroundJob + SLF4J 어댑터.
 *
 * <p>현재 슬라이스의 단일 구현체. {@link NotifyChannel#BACKGROUND_JOB} 활성 시 즉시 register +
 * complete (단순 알림 type 이므로 stage 진행 없음). {@link NotifyChannel#SERVER_LOG} 활성 시 INFO 로그.</p>
 *
 * <p><strong>D5 메모</strong> : 후일 알림 도메인 신설 시 본 클래스가 알림 도메인으로 이관 + EMAIL /
 * SLACK adapter 가 자매 구현체로 추가.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundJobNotificationAdapter implements NotificationDispatcher {

	private final BackgroundJobService backgroundJobService;

	@Override
	public void dispatch(JobType type, Set<NotifyChannel> activeChannels, String title, String body) {
		if (activeChannels.contains(NotifyChannel.SERVER_LOG)) {
			log.info("[trash-notify] type={} title='{}' body='{}'", type, title, body);
		}
		if (activeChannels.contains(NotifyChannel.BACKGROUND_JOB)) {
			// 단일 stage 알림 — register 즉시 complete. 사용자가 알림 패널에서 확인 후 dismiss.
			String jobId = backgroundJobService.register(type, title, body, List.of("알림"));
			backgroundJobService.complete(jobId);
		}
	}
}
