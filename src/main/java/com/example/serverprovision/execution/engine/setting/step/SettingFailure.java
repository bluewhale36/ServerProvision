package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * 설정 축 실패의 단일 처리처(HF25) — 네 step 이 각자 갖던 실패 종결을 모으고, 재시도로 풀릴 수 있는 사유는 축 안에서
 * 자동으로 다시 하게 한다. 자동 재시도는 행만 FAILED 로 닫고 진행은 실패로 바꾸지 않으므로, 다음 워커 주기가 새 행을
 * 열어 축을 처음부터 다시 한다. 회차는 원장 행에서 센다({@link SettingContext#autoRetriesUsed}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettingFailure {

    /** 자동 재시도 상한(사용자 결정 2026-10-23) — 첫 시도를 포함해 네 번까지 한다. */
    static final int MAX_AUTO_RETRIES = 3;

    /**
     * 다시 하면 풀릴 수 있는 사유. READBACK_MISMATCH 는 10~11 월 spv-01 설정 실패 7 건 전부의 사유였고, WRITE_REJECTED 는
     * BMC 쓰기 안의 읽기 일시 오류가 거절로 분류되어 들어오는 자리다. 그 밖(신원 불일치 · 레지스트리 밖 값 · 인증 · 시한 만료)은
     * 다시 해도 같은 결과이거나 운영자가 알아야 할 사건이다.
     */
    private static final Set<String> RETRYABLE = Set.of(SettingLedger.READBACK_MISMATCH, SettingLedger.WRITE_REJECTED);

    private final SettingLedger ledger;

    /** 열린 행이 있을 수도 없을 수도 있는 실패 — 행이 없으면 커서 자리에 단발 실패를 남긴다(재시도 대상 아님). */
    public void fail(SettingContext context, Optional<ProvisioningHistory> row, String reason, String detail) {
        row.ifPresentOrElse(
                r -> close(context, r, reason, detail),
                () -> {
                    ledger.failAtCursor(context.server(), context.progress(), reason, detail, context.now());
                    log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
                });
    }

    /** 열린 행의 실패 — 재시도할 수 있고 회차가 남으면 행만 닫고, 아니면 진행을 실패로 바꾼다. */
    public void close(SettingContext context, ProvisioningHistory row, String reason, String detail) {
        int used = context.autoRetriesUsed();
        if (RETRYABLE.contains(reason) && used < MAX_AUTO_RETRIES) {
            int attempt = used + 1;
            ledger.closeForAutoRetry(row, reason, detail, attempt, MAX_AUTO_RETRIES, context.now());
            log.info("[setting] {} — {} : {} → 자동 재시도 {}/{}", context.server().getId(), reason, detail,
                    attempt, MAX_AUTO_RETRIES);
            return;
        }
        ledger.close(row, ProvisioningStatus.FAILED, reason, detail, context.now());
        context.progress().markFailed(context.now());
        log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
    }
}
