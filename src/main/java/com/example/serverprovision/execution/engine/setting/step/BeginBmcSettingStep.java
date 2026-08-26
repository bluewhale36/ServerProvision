package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.setting.BmcItemOutcome;
import com.example.serverprovision.execution.engine.setting.BmcSettingItem;
import com.example.serverprovision.execution.engine.setting.SettingAxis;
import com.example.serverprovision.execution.engine.setting.SettingCursor;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import com.example.serverprovision.global.bmcweb.AmiWebApi;
import com.example.serverprovision.global.bmcweb.AmiWebClient;
import com.example.serverprovision.global.bmcweb.AmiWebError;
import com.example.serverprovision.global.bmcweb.AmiWebRequestException;
import com.example.serverprovision.global.bmcweb.AmiWebSession;
import com.example.serverprovision.global.redfish.BmcCredentialsFallback;
import com.example.serverprovision.global.redfish.BmcRequestException;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 7행 — BMC 축 착수(E3-2 D-3 · D-5 · D-9): ① 신원 대조 → ② 원장 열기(첫 쓰기보다 먼저) 또는 이어받기 → ③ 세션
 * (폴백 · 캐시) → ④ 항목 순서대로 쓰기 + 되읽기, 결과를 항목마다 원장에 → ⑤ 로그아웃 → ⑥ 종결 · 다음 축.
 * 어느 항목이 거절 · 불일치면 그 자리에서 닫고 뒤 항목은 미수행으로 남긴다. Bond 뒤 연결이 끊기면 {@code bondAt} 만
 * 적고 RUNNING 으로 남긴다 — 2행이 거둔다. 재개(bondAt 없는 RUNNING 행)는 신원부터 다시 하고 항목도 다시 쓴다(멱등).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeginBmcSettingStep implements SettingStep {

    private final SettingLedger ledger;
    private final BmcIdentityProbe identityProbe;
    private final AmiWebClient webClient;
    private final BmcCredentialsFallback credentialsFallback;
    private final FlashTimeoutPolicy timeoutPolicy;
    private final SettingCursor settingCursor;

    @Override
    public int order() {
        return 7;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.axis() == SettingAxis.BMC && context.target() != null && context.bmcDetected();
    }

    @Override
    public void execute(SettingContext context) {
        Optional<ProvisioningHistory> resumed = context.runningRow();
        RedfishTarget target = context.redfishTarget();

        BmcIdentity identity = identityProbe.probe(context.provider(), target,
                context.detail().getBoardSerial(), context.detail(), "setting");
        if (identity == BmcIdentity.MISMATCHED) {
            fail(context, resumed, SettingLedger.IDENTITY_MISMATCH, "응답한 장비의 보드 시리얼이 이 서버와 다릅니다");
            return;
        }
        if (identity == BmcIdentity.UNREACHABLE) {
            if (expired(context)) {
                fail(context, resumed, SettingLedger.BMC_UNREACHABLE, "BMC 에 닿지 못했고 새 주소도 찾지 못했습니다");
            }
            return;
        }

        ProvisioningHistory row = resumed.orElseGet(() -> ledger.openBmc(context.server(), context.now()));
        AmiWebSession session;
        try {
            session = credentialsFallback.attempt(target, c -> webClient.login(target.bmcIp(), c));
        } catch (BmcRequestException e) {
            if (e.authFailure()) {
                close(context, row, SettingLedger.AUTH_REJECTED, e.getMessage());
            } else if (expired(context)) {
                close(context, row, SettingLedger.BMC_UNREACHABLE, e.getMessage());
            } else {
                log.info("[setting] {} — 웹 세션 발급 실패, 다음 주기 재시도 : {}", context.server().getId(), e.getMessage());
            }
            return;
        }
        AmiWebApi api = webClient.bind(session);
        try {
            Instant now = context.now().atZone(ZoneId.systemDefault()).toInstant();
            for (BmcSettingItem item : BmcSettingItem.values()) {
                BmcItemOutcome outcome = item.apply(api, context.bmcTarget(), now);
                ledger.markItem(row, item, outcome);
                switch (outcome.status()) {
                    case APPLIED, SKIPPED -> { }
                    case REJECTED -> {
                        close(context, row, SettingLedger.WRITE_REJECTED, item.name() + " — " + outcome.detail());
                        return;
                    }
                    case MISMATCH -> {
                        close(context, row, SettingLedger.READBACK_MISMATCH, item.name() + " — " + outcome.detail());
                        return;
                    }
                    case RECONNECT_PENDING -> {
                        ledger.markBondAt(row, context.now());
                        log.info("[setting] {} — {} 적용 뒤 연결 끊김, 재접속 대기(bondAt)", context.server().getId(), item.name());
                        return;
                    }
                }
            }
            ledger.close(row, ProvisioningStatus.SUCCEEDED, SettingLedger.APPLIED, ledger.summaryOf(row), context.now());
            settingCursor.afterAxis(SettingAxis.BMC, context.progress(), context.server().getId(), context.now());
            log.info("[setting] {} — BMC 표준 세팅 {} , 축 종결", context.server().getId(), ledger.summaryOf(row));
        } catch (AmiWebRequestException e) {
            if (e.getError() == AmiWebError.AUTH_FAILED) {
                close(context, row, SettingLedger.AUTH_REJECTED, e.getMessage());
            } else if (e.getError() == AmiWebError.CONNECT_FAILED && !expired(context)) {
                log.info("[setting] {} — 연결 끊김, 다음 주기 재개 : {}", context.server().getId(), e.getMessage());
            } else if (e.getError() == AmiWebError.CONNECT_FAILED) {
                close(context, row, SettingLedger.BMC_UNREACHABLE, e.getMessage());
            } else {
                close(context, row, SettingLedger.WRITE_REJECTED, e.getMessage());
            }
        } finally {
            webClient.logout(session);
        }
    }

    private boolean expired(SettingContext context) {
        return timeoutPolicy.isExpired(context.progress().getLastTransitionAt(), timeoutPolicy.returnLimit(), context.now());
    }

    private void close(SettingContext context, ProvisioningHistory row, String reason, String detail) {
        ledger.close(row, ProvisioningStatus.FAILED, reason, detail, context.now());
        context.progress().markFailed(context.now());
        log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
    }

    private void fail(SettingContext context, Optional<ProvisioningHistory> row, String reason, String detail) {
        row.ifPresentOrElse(
                r -> close(context, r, reason, detail),
                () -> {
                    ledger.failAtCursor(context.server(), context.progress(), reason, detail, context.now());
                    log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
                });
    }
}
