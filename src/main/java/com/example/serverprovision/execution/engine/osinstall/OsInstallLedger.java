package com.example.serverprovision.execution.engine.osinstall;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OS 설치 원장의 계열 공통부(E4-1-b-4-1). 계열마다 같은 step(OS_INSTALLING) 에 행을 쓰므로 조회는 언제나 meta 의
 * {@code origin} 으로 거른다 — 거르지 않으면 한 계열의 스위퍼 · 재진입 판정이 다른 계열의 열린 행을 집는다.
 * RUNNING 행 하나가 서빙 한 사이클이고, 실패는 그 행을 사유와 함께 닫는다(서빙 meta 보존).
 */
public abstract class OsInstallLedger {

    /** 완료 보고가 서빙 행을 SUCCEEDED 로 닫았다. */
    public static final String COMPLETED = "COMPLETED";

    protected static final ProvisioningPhaseStep STEP = ProvisioningPhaseStep.OS_INSTALLING;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    protected final ProvisioningHistoryRecorder recorder;
    protected final ProvisioningHistoryRepository historyRepository;
    protected final ObjectMapper objectMapper;

    protected OsInstallLedger(ProvisioningHistoryRecorder recorder, ProvisioningHistoryRepository historyRepository,
                              ObjectMapper objectMapper) {
        this.recorder = recorder;
        this.historyRepository = historyRepository;
        this.objectMapper = objectMapper;
    }

    /** 이 원장이 meta 에 적는 계열 표지. */
    public abstract OsInstallOrigin origin();

    /** 이 원장이 적은 행인가(meta 의 origin 이 이 계열). 표지 없는 행 · 다른 표지(운영자 전환 · 게스트 보고)는 아니다. */
    public boolean owns(ProvisioningHistory row) {
        return origin().matches(read(row).get("origin"));
    }

    /**
     * 지금 열려 있는 이 계열의 서빙 행 — 상태 조건으로 직접 묻고 origin 으로 거른다. 열린 OS 설치 행은 계열을 통틀어
     * 언제나 하나라(새 서빙이 옛 행을 SUPERSEDED 로 닫는다) 최신 RUNNING 행 하나만 보면 된다.
     */
    public Optional<ProvisioningHistory> latestRunning(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guestServerId, STEP, ProvisioningStatus.RUNNING).filter(this::owns);
    }

    /**
     * 가장 최근 OS 설치 행(상태 · 계열 무관 — 운영자 전환 행도 포함). 호출자가 {@link #owns} 나 사유로 판정한다
     * (완료 보고의 멱등 판정이 "최신 행이 완료 행인가" 를 이 값으로 본다).
     */
    public Optional<ProvisioningHistory> latestOf(UUID guestServerId) {
        return historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(guestServerId, STEP);
    }

    /** 재진입 서빙 — 행 교체가 아니라 meta 갱신(served 는 그대로, reentries +1). */
    public int bumpReentry(ProvisioningHistory row, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        int next = intOf(meta, "reentries") + 1;
        meta.put("reentries", next);
        meta.put("lastReentryAt", now.toString());
        row.updateRunningMeta(write(meta));
        return next;
    }

    /**
     * 열린 행을 사유와 함께 닫고 설치 단계를 실패로 바꾼다. 서빙 meta 는 보존한다. 행이 이미 닫혀 있으면(동시 전환) 단발 실패 행으로
     * 남긴다.
     */
    public void failRunning(GuestServer server, ProvisioningProgress progress, ProvisioningHistory row,
                            OsInstallFailureReason reason, String detail, LocalDateTime now) {
        if (!abortRunning(row, reason, detail, now)) {
            recordFailure(server, reason, detail, now);
        }
        progress.markFailed(now);
    }

    /** 서빙 전에 정해진 실패 — 열린 행이 없으므로 단발 실패 행을 적고 설치 단계를 실패로 바꾼다. */
    public void failBeforeServing(GuestServer server, ProvisioningProgress progress, OsInstallFailureReason reason,
                                  String detail, LocalDateTime now) {
        recordFailure(server, reason, detail, now);
        progress.markFailed(now);
    }

    /** 열린 서빙 행을 사유와 함께 FAILED 로 닫는다 — 진행 신호는 건드리지 않는다. 이미 닫힌 행이면 false. */
    public boolean abortRunning(ProvisioningHistory row, OsInstallFailureReason reason, String detail, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        meta.putIfAbsent("origin", origin().wire());
        putFailure(meta, reason, detail);
        return row.close(ProvisioningStatus.FAILED, write(meta), now);
    }

    private void recordFailure(GuestServer server, OsInstallFailureReason reason, String detail, LocalDateTime now) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("origin", origin().wire());
        putFailure(meta, reason, detail);
        recorder.recordInstant(server, STEP, ProvisioningStatus.FAILED, write(meta), now);
    }

    /** 사유 코드 · 운영 기록(detail) · 화면 문구(사유가 줄 때만)를 meta 에 적는다. */
    private static void putFailure(Map<String, Object> meta, OsInstallFailureReason reason, String detail) {
        meta.put("reason", reason.code());
        meta.put("detail", detail == null ? "" : detail);
        reason.screenMessage().ifPresentOrElse(message -> meta.put("message", message), () -> meta.remove("message"));
    }

    public LocalDateTime servedAtOf(ProvisioningHistory row) {
        Object v = read(row).get("served");
        return v == null ? null : LocalDateTime.parse(v.toString());
    }

    public int reentriesOf(ProvisioningHistory row) {
        return intOf(read(row), "reentries");
    }

    /** 실패 · 완료 행의 사유 코드 — 없으면 null. */
    public String reasonOf(ProvisioningHistory row) {
        Object v = read(row).get("reason");
        return v == null ? null : v.toString();
    }

    /** 완료 보고로 닫힌 행인가 — 멱등 판정(중복 보고 no-op)과 카드의 완료 분기가 같은 판독을 쓴다. */
    public boolean isCompletedRow(ProvisioningHistory row) {
        return row.getStatus() == ProvisioningStatus.SUCCEEDED && COMPLETED.equals(reasonOf(row));
    }

    public LocalDateTime completedAtOf(ProvisioningHistory row) {
        Object v = read(row).get("completedAt");
        return v == null ? null : LocalDateTime.parse(v.toString());
    }

    /**
     * 열린 서빙 행을 완료로 닫는다. 서빙 meta 는 그대로 두고 완료 시각, 계열이 넘긴 사실, 사유 COMPLETED, 운영 기록을 더한다.
     * 이미 닫힌 행이면 false.
     */
    protected boolean closeCompleted(ProvisioningHistory row, Map<String, Object> facts, String detail, LocalDateTime now) {
        Map<String, Object> meta = read(row);
        meta.putIfAbsent("origin", origin().wire());
        meta.put("completedAt", now.toString());
        meta.putAll(facts);
        meta.put("reason", COMPLETED);
        meta.put("detail", detail);
        return row.close(ProvisioningStatus.SUCCEEDED, write(meta), now);
    }

    /** 설치 디스크 사후 확증. 완료 행이 아니면(meta 에 키가 없으면) null. */
    public InstallConfirmation diskConfirmationOf(ProvisioningHistory row) {
        return confirmationOf(row, "diskConfirmed");
    }

    /** meta 의 Boolean 확증 값을 옮긴다. 키가 없으면 null, 값이 null 이면 미보고. */
    protected InstallConfirmation confirmationOf(ProvisioningHistory row, String key) {
        Map<String, Object> meta = read(row);
        if (!meta.containsKey(key)) {
            return null;
        }
        return InstallConfirmation.of(meta.get(key) instanceof Boolean confirmed ? confirmed : null);
    }

    /** meta 의 문자열 값. 없으면 null. */
    protected String textOf(ProvisioningHistory row, String key) {
        Object v = read(row).get(key);
        return v == null ? null : v.toString();
    }

    /** 실패 행에 적힌 화면 문구 — 사유가 문구를 주지 않았으면 empty(화면은 사유 코드로 문구를 고른다). */
    public Optional<String> failureMessageOf(ProvisioningHistory row) {
        Object v = read(row).get("message");
        return v == null ? Optional.empty() : Optional.of(v.toString());
    }

    protected static int intOf(Map<String, Object> meta, String key) {
        Object v = meta.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    protected Map<String, Object> read(ProvisioningHistory row) {
        String raw = row.getStatusMeta();
        if (raw == null || raw.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(raw, MAP));
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    protected String write(Map<String, Object> meta) {
        return objectMapper.writeValueAsString(meta);
    }
}
