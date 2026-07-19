package com.example.serverprovision.maintenance.trash.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableInventory;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.service.NotificationDispatcher;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.global.trash.service.TrashSettingsService;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * HF4-1 D4 — TTL worker 의 만료 재검증 필터.
 *
 * <p>{@code findTrashedBefore} 후보 조회는 trashed_at 기준(넓게)을 유지하고, 연장 가산분
 * ({@code ttl_extension_days})은 entity 만료 SSOT({@code trashExpiresAt})의 Java 재검증으로 반영 —
 * 연장으로 만료 미도래인 행이 purge 대상에서 제외되는지 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TrashTtlWorkerTest {

	@Mock
	TrashSettingsService settingsService;

	@Mock
	MarkableInventory scanner;

	@Mock
	PurgeExecutor purgeExecutor;

	@Mock
	NotificationDispatcher notificationDispatcher;

	TrashTtlWorker worker;

	@BeforeEach
	void setUp() {
		worker = new TrashTtlWorker(settingsService, List.of(scanner), purgeExecutor, notificationDispatcher);
	}

	private OSMetadata trashedEntity(Long id, String version, Duration trashedAgo, int extensionDays) {
		return OSMetadata.builder()
				.id(id).osName(OSName.ROCKY_LINUX).osVersion(version)
				.trashedAt(Instant.now().minus(trashedAgo))
				.ttlExtensionDays(extensionDays)
				.build();
	}

	@Test
	@DisplayName("연장 행 제외 — 후보 2건 중 만료 확정 1건만 purge, 연장으로 미도래인 행은 skip")
	void purgeExpired_excludesExtendedRow() {
		given(settingsService.isAutoPurgeEnabled()).willReturn(true);
		given(settingsService.getTtl()).willReturn(Duration.ofDays(30));
		// 40일 전 삭제 : 연장 0 → 만료 10일 경과 / 연장 +60 → 만료 50일 남음 (후보 조회에는 둘 다 걸림)
		OSMetadata expired = trashedEntity(1L, "9.5", Duration.ofDays(40), 0);
		OSMetadata extended = trashedEntity(2L, "8.9", Duration.ofDays(40), 60);
		given(scanner.findTrashedBefore(any()))
				.willReturn(List.of((Markable) expired, (Markable) extended));
		given(scanner.supportedType()).willReturn(ResourceType.OS_IMAGE);
		given(purgeExecutor.execute(any()))
				.willAnswer(inv -> new PurgeResult.Success(inv.getArgument(0), 1L));

		worker.purgeExpired();

		ArgumentCaptor<PurgeRequest> captor = ArgumentCaptor.forClass(PurgeRequest.class);
		then(purgeExecutor).should(times(1)).execute(captor.capture());
		assertThat(captor.getValue().resourceId()).isEqualTo(1L);   // 연장 행(2L)은 제외
	}

	@Test
	@DisplayName("전부 연장 행 — purge 호출 0건")
	void purgeExpired_allExtended_noPurge() {
		given(settingsService.isAutoPurgeEnabled()).willReturn(true);
		given(settingsService.getTtl()).willReturn(Duration.ofDays(30));
		OSMetadata extended = trashedEntity(2L, "8.9", Duration.ofDays(40), 60);
		given(scanner.findTrashedBefore(any())).willReturn(List.of((Markable) extended));

		worker.purgeExpired();

		then(purgeExecutor).should(never()).execute(any());
	}
}
