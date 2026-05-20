package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeLogDetails;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.entity.PurgeLog;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;
import com.example.serverprovision.global.trash.exception.PurgeIoFailedException;
import com.example.serverprovision.global.trash.repository.PurgeLogRepository;
import com.example.serverprovision.global.trash.service.internal.PurgeExecutorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * S5-2-4 — PurgeExecutor 본체 단위 테스트. plan §6 의 7 시나리오.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurgeExecutorTest {

    @Mock MarkableScanner isoScanner;
    @Mock TrashSettingsService settings;
    @Mock PurgeLogRepository purgeLogRepository;
    @Mock Markable snapshot;

    PurgeExecutorImpl executor;

    @BeforeEach
    void setUp() {
        // 본 테스트의 PurgeExecutor 는 단일 도메인 (OS_ISO) 만 등록.
        executor = new PurgeExecutorImpl(List.of(isoScanner), settings, purgeLogRepository);
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
    }

    private void stubSnapshot() {
        given(snapshot.displayName()).willReturn("Rocky Linux 9.5 dvd.iso");
        given(snapshot.getResourcePath()).willReturn(Path.of("/opt/iso/rocky/9/dvd.iso"));
        given(snapshot.getManifestHash()).willReturn("sha256:7f3a");
        given(snapshot.getMarkerSignature()).willReturn("abc");
        given(isoScanner.findTrashedById(27L)).willReturn(Optional.of(snapshot));
    }

    private void stubSettings(int retryMax, long backoffBase) {
        given(settings.getRetryMaxAttempts()).willReturn(retryMax);
        given(settings.getRetryBackoffBaseMs()).willReturn(backoffBase);
    }

    private void stubLogSaveEcho() {
        given(purgeLogRepository.save(any(PurgeLog.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("(1) USER_DIRECT 성공 — purge_log SUCCESS 1행 + purged_at NOT NULL + scanner.purgeFromTrash 1회")
    void userDirect_success() {
        stubSnapshot();
        stubSettings(3, 1000L);
        stubLogSaveEcho();

        PurgeResult result = executor.execute(
                PurgeRequest.forUserDirect(ResourceType.OS_ISO, 27L, "admin", "Rocky Linux 9.5 dvd.iso"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        verify(isoScanner, times(1)).purgeFromTrash(27L);
        ArgumentCaptor<PurgeLog> captor = ArgumentCaptor.forClass(PurgeLog.class);
        verify(purgeLogRepository).save(captor.capture());
        PurgeLog saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo(PurgeOutcome.SUCCESS);
        assertThat(saved.getPurgedAt()).isNotNull();
        assertThat(saved.getDisplayName()).isEqualTo("Rocky Linux 9.5 dvd.iso");
        assertThat(saved.getOrigin()).isEqualTo(PurgeOrigin.USER_DIRECT);
        assertThat(saved.getDetails()).isInstanceOf(PurgeLogDetails.Success.class);
    }

    @Test
    @DisplayName("(2) NUDGE_REPLACE 성공 — origin=NUDGE_REPLACE + triggeredBy 보존")
    void nudgeReplace_success() {
        stubSnapshot();
        stubSettings(3, 1000L);
        stubLogSaveEcho();

        PurgeResult result = executor.execute(
                PurgeRequest.forNudgeReplace(ResourceType.OS_ISO, 27L, "user@example", "Rocky Linux 9.5 dvd.iso"));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        ArgumentCaptor<PurgeLog> captor = ArgumentCaptor.forClass(PurgeLog.class);
        verify(purgeLogRepository).save(captor.capture());
        PurgeLog saved = captor.getValue();
        assertThat(saved.getOrigin()).isEqualTo(PurgeOrigin.NUDGE_REPLACE);
        PurgeLogDetails.Success details = (PurgeLogDetails.Success) saved.getDetails();
        assertThat(details.triggeredBy()).isEqualTo("user@example");
    }

    @Test
    @DisplayName("(3) TTL_AUTO 성공 1회 — attemptCount=1, retry 시도 없음 (scanner 1회 호출)")
    void ttlAuto_successFirstAttempt() {
        stubSnapshot();
        stubSettings(3, 1000L);
        stubLogSaveEcho();

        executor.execute(PurgeRequest.forTtlAuto(ResourceType.OS_ISO, 27L));

        verify(isoScanner, times(1)).purgeFromTrash(27L);
        ArgumentCaptor<PurgeLog> captor = ArgumentCaptor.forClass(PurgeLog.class);
        verify(purgeLogRepository).save(captor.capture());
        PurgeLogDetails.Success details = (PurgeLogDetails.Success) captor.getValue().getDetails();
        assertThat(details.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("(4) TTL_AUTO retry 후 성공 — 1회 실패 → 2회째 성공. purge_log 1행 (마지막 결과만)")
    void ttlAuto_retryThenSuccess() {
        stubSnapshot();
        stubSettings(3, 10L);  // backoff 짧게
        stubLogSaveEcho();
        willThrow(new RuntimeException("io-fail"))
                .willDoNothing()
                .given(isoScanner).purgeFromTrash(27L);

        PurgeResult result = executor.execute(PurgeRequest.forTtlAuto(ResourceType.OS_ISO, 27L));

        assertThat(result).isInstanceOf(PurgeResult.Success.class);
        verify(isoScanner, times(2)).purgeFromTrash(27L);
        verify(purgeLogRepository, times(1)).save(any(PurgeLog.class));  // 마지막 SUCCESS row 1개만
    }

    @Test
    @DisplayName("(5) TTL_AUTO 3회 실패 — purge_log FAILED 1행 + tickAttemptCount=3 + attemptNumber=과거 FAILED count+1")
    void ttlAuto_threeFailures() {
        stubSnapshot();
        stubSettings(3, 10L);
        stubLogSaveEcho();
        willThrow(new RuntimeException("io-fail")).given(isoScanner).purgeFromTrash(27L);
        // 이전에 2회 실패 누적된 상태 가정
        given(purgeLogRepository.countByResourceTypeAndResourceIdAndOutcome(
                ResourceType.OS_ISO, 27L, PurgeOutcome.FAILED)).willReturn(2L);

        PurgeResult result = executor.execute(PurgeRequest.forTtlAuto(ResourceType.OS_ISO, 27L));

        assertThat(result).isInstanceOf(PurgeResult.Failed.class);
        verify(isoScanner, times(3)).purgeFromTrash(27L);
        ArgumentCaptor<PurgeLog> captor = ArgumentCaptor.forClass(PurgeLog.class);
        verify(purgeLogRepository).save(captor.capture());
        PurgeLog saved = captor.getValue();
        assertThat(saved.getOutcome()).isEqualTo(PurgeOutcome.FAILED);
        assertThat(saved.getPurgedAt()).isNull();
        PurgeLogDetails.Failed details = (PurgeLogDetails.Failed) saved.getDetails();
        assertThat(details.tickAttemptCount()).isEqualTo(3);
        assertThat(details.attemptNumber()).isEqualTo(3); // 과거 2회 + 이번 시도 = 누적 3회
    }

    @Test
    @DisplayName("(6) USER_DIRECT IOException — retry 안 함 (origin.retriesAllowed=false), FAILED 1행 + tickAttemptCount=1")
    void userDirect_ioException_noRetry() {
        stubSnapshot();
        // USER_DIRECT 는 retryMaxAttempts 사용 안 함 (1회로 고정), settings stub 불필요 — Mockito strict mode 회피
        stubLogSaveEcho();
        willThrow(new RuntimeException("io-fail")).given(isoScanner).purgeFromTrash(27L);

        PurgeResult result = executor.execute(
                PurgeRequest.forUserDirect(ResourceType.OS_ISO, 27L, "admin", "Rocky Linux 9.5 dvd.iso"));

        assertThat(result).isInstanceOf(PurgeResult.Failed.class);
        verify(isoScanner, times(1)).purgeFromTrash(27L);
        ArgumentCaptor<PurgeLog> captor = ArgumentCaptor.forClass(PurgeLog.class);
        verify(purgeLogRepository).save(captor.capture());
        PurgeLogDetails.Failed details = (PurgeLogDetails.Failed) captor.getValue().getDetails();
        assertThat(details.tickAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("(7) snapshot 미발견 — FAILED 1행 + tickAttemptCount=0 + scanner.purgeFromTrash 미호출")
    void snapshotNotFound() {
        given(isoScanner.findTrashedById(99L)).willReturn(Optional.empty());
        stubLogSaveEcho();

        PurgeResult result = executor.execute(PurgeRequest.forTtlAuto(ResourceType.OS_ISO, 99L));

        assertThat(result).isInstanceOf(PurgeResult.Failed.class);
        verify(isoScanner, atLeastOnce()).findTrashedById(99L);
        // scanner.purgeFromTrash 호출 0회
        ArgumentCaptor<PurgeLog> captor = ArgumentCaptor.forClass(PurgeLog.class);
        verify(purgeLogRepository).save(captor.capture());
        PurgeLogDetails.Failed details = (PurgeLogDetails.Failed) captor.getValue().getDetails();
        assertThat(details.tickAttemptCount()).isEqualTo(0);
    }
}
