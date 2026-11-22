package com.example.serverprovision.execution.engine.rhel.ossetting;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * OS 후처리 판정을 원장에 남기고 읽는 한 곳(E4-1-b-7). 판정은 첫 부팅 보고 한 번에 끝나므로 열고 닫는 행이 아니라 단발 행이다.
 * meta 의 {@code detail} 은 세부 단계 표의 사유 칸이 읽는다.
 */
@Component
@RequiredArgsConstructor
public class OsSettingLedger {

    static final String ORIGIN = "rhel-first-boot";
    private static final ProvisioningPhaseStep STEP = ProvisioningPhaseStep.OS_SETTING;

    private final ProvisioningHistoryRecorder recorder;
    private final ProvisioningHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;

    public void record(GuestServer server, OsSettingVerdict verdict, LocalDateTime now) {
        Meta meta = new Meta(ORIGIN, verdict.passed(), verdict.items(), verdict.detail(), verdict.message());
        recorder.recordInstant(server, STEP, verdict.passed() ? ProvisioningStatus.SUCCEEDED : ProvisioningStatus.FAILED,
                objectMapper.writeValueAsString(meta), now);
    }

    /** 가장 최근 판정. 이 원장이 적지 않은 행(운영자 전환 등)이면 비어 있다. */
    public Optional<OsSettingVerdict> latestOf(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(guestServerId, STEP)
                .flatMap(this::read);
    }

    private Optional<OsSettingVerdict> read(ProvisioningHistory row) {
        try {
            Meta meta = objectMapper.readValue(row.getStatusMeta(), Meta.class);
            return ORIGIN.equals(meta.origin())
                    ? Optional.of(new OsSettingVerdict(meta.passed(), meta.items(), meta.detail(), meta.message()))
                    : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    record Meta(String origin, boolean passed, List<OsSettingItemOutcome> items, String detail, String message) {
    }
}
