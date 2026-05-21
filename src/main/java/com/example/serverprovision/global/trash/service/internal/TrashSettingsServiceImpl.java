package com.example.serverprovision.global.trash.service.internal;

import com.example.serverprovision.global.trash.dto.request.TrashSettingsRequest;
import com.example.serverprovision.global.trash.dto.response.TrashSettingsResponse;
import com.example.serverprovision.global.trash.entity.TrashSettings;
import com.example.serverprovision.global.trash.enums.NotifyChannel;
import com.example.serverprovision.global.trash.repository.TrashSettingsRepository;
import com.example.serverprovision.global.trash.service.TrashSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S5-2-4 CP4 — TrashSettingsService 본체.
 *
 * <p><strong>Singleton 보장</strong> : id 강제 (1L) 폐기, {@code count() == 1} 검사 기반
 * (사용자 결정 2026-05-20). PostConstruct 가 count() 확인 → 0 시드 / 1 정상 / &gt;=2 정리 + log.warn.</p>
 *
 * <p><strong>캐시</strong> : worker / executor 가 자주 호출하는 getter 들의 DB 부하 완화. update()
 * 시점에 캐시 갱신.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashSettingsServiceImpl implements TrashSettingsService {

	private final TrashSettingsRepository repository;

	private volatile TrashSettings cached;

	/**
	 * Application 시작 시 singleton row 검사 + 시드.
	 * <ul>
	 *   <li>count==0 — {@link TrashSettings#defaults()} insert</li>
	 *   <li>count==1 — 정상</li>
	 *   <li>count&gt;=2 — 첫 row (id ASC) 만 신뢰, 나머지 hard-delete + log.warn</li>
	 * </ul>
	 */
	@PostConstruct
	@Transactional
	public void seedOrReconcile() {
		long count = repository.count();
		if (count == 0L) {
			cached = repository.save(TrashSettings.defaults());
			log.info("[trash-settings] singleton row 없음 — default 시드. id={}", cached.getId());
			return;
		}
		TrashSettings first = repository.findFirstByOrderByIdAsc().orElseThrow();
		if (count > 1L) {
			log.warn("[trash-settings] singleton 위반 — count={} 행. id={} 만 신뢰, 나머지 hard-delete.", count, first.getId());
			repository.findAll().stream()
					.filter(t -> !Objects.equals(t.getId(), first.getId()))
					.forEach(repository::delete);
		}
		cached = first;
	}

	@Override
	public TrashSettingsResponse current() {
		TrashSettings s = currentEntity();
		return toResponse(s);
	}

	@Override
	@Transactional
	public TrashSettingsResponse update(TrashSettingsRequest request) {
		TrashSettings s = currentEntity();
		s.apply(request);
		TrashSettings saved = repository.save(s);
		cached = saved;
		log.info(
				"[trash-settings] 운영 설정 갱신. ttl={}일 autoPurge={} cron={}",
				saved.getTtlDays(), saved.isAutoPurgeEnabled(), saved.getPurgeCronExpression()
		);
		return toResponse(saved);
	}

	/**
	 * 캐시 우선, 없으면 DB lookup + 캐시 갱신.
	 */
	private TrashSettings currentEntity() {
		TrashSettings c = cached;
		if (c != null) return c;
		synchronized (this) {
			if (cached == null) {
				cached = repository.findFirstByOrderByIdAsc()
						.orElseGet(() -> repository.save(TrashSettings.defaults()));
			}
			return cached;
		}
	}

	private TrashSettingsResponse toResponse(TrashSettings s) {
		return new TrashSettingsResponse(
				s.getTtlDays(), s.isAutoPurgeEnabled(),
				s.getPurgeCronExpression(), s.getNotifyCronExpression(),
				s.getNotifyDaysBefore(), s.getNotificationChannels(),
				s.getRetryMaxAttempts(), s.getRetryBackoffBaseMs(),
				s.getUpdatedAt() == null ? null : s.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
		);
	}

	@Override
	public Duration getTtl() {
		return Duration.ofDays(currentEntity().getTtlDays());
	}

	@Override
	public int getRetryMaxAttempts() {
		return currentEntity().getRetryMaxAttempts();
	}

	@Override
	public long getRetryBackoffBaseMs() {
		return currentEntity().getRetryBackoffBaseMs();
	}

	@Override
	public boolean isAutoPurgeEnabled() {
		return currentEntity().isAutoPurgeEnabled();
	}

	@Override
	public String getPurgeCronExpression() {
		return currentEntity().getPurgeCronExpression();
	}

	@Override
	public String getNotifyCronExpression() {
		return currentEntity().getNotifyCronExpression();
	}

	@Override
	public List<Integer> getNotifyDaysBeforeList() {
		return Arrays.stream(currentEntity().getNotifyDaysBefore().split(","))
				.map(String::trim).filter(s -> !s.isEmpty()).map(Integer::parseInt).toList();
	}

	@Override
	public Set<NotifyChannel> getNotificationChannels() {
		return Arrays.stream(currentEntity().getNotificationChannels().split(","))
				.map(String::trim).filter(s -> !s.isEmpty())
				.map(NotifyChannel::valueOf)
				.collect(Collectors.toSet());
	}
}
