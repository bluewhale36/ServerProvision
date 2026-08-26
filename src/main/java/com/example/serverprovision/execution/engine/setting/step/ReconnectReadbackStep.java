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

import java.time.LocalDateTime;

/**
 * 2행(진리표 1b) — Bond 를 쓴 뒤 끊긴 연결을 다시 열어 되읽는다(E3-2 D-8). 신원 대조부터 다시 하고(D-7), 되읽기가
 * 맞으면 BMC 축을 닫고 다음으로, 어긋나면 실패로 닫는다. 아직 닿지 않으면 {@code bondAt} 기준 시한 안에서 기다린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconnectReadbackStep implements SettingStep {

    private final SettingLedger ledger;
    private final BmcIdentityProbe identityProbe;
    private final AmiWebClient webClient;
    private final BmcCredentialsFallback credentialsFallback;
    private final FlashTimeoutPolicy timeoutPolicy;
    private final SettingCursor settingCursor;

    @Override
    public int order() {
        return 2;
    }

    @Override
    public boolean matches(SettingContext context) {
        return context.axis() == SettingAxis.BMC
                && context.runningRow().map(row -> ledger.bondAtOf(row) != null).orElse(false);
    }

    @Override
    public void execute(SettingContext context) {
        ProvisioningHistory row = context.runningRow().orElseThrow();
        LocalDateTime bondAt = ledger.bondAtOf(row);
        RedfishTarget target = context.redfishTarget();

        BmcIdentity identity = identityProbe.probe(context.provider(), target,
                context.detail().getBoardSerial(), context.detail(), "setting");
        if (identity == BmcIdentity.MISMATCHED) {
            close(context, row, SettingLedger.IDENTITY_MISMATCH, "응답한 장비의 보드 시리얼이 이 서버와 다릅니다");
            return;
        }
        if (identity == BmcIdentity.UNREACHABLE) {
            waitOrExpire(context, row, bondAt, "BMC 에 닿지 못했고 새 주소도 찾지 못했습니다");
            return;
        }

        AmiWebSession session;
        try {
            session = credentialsFallback.attempt(target, c -> webClient.login(target.bmcIp(), c));
        } catch (BmcRequestException e) {
            if (e.authFailure()) {
                close(context, row, SettingLedger.AUTH_REJECTED, e.getMessage());
            } else {
                waitOrExpire(context, row, bondAt, e.getMessage());
            }
            return;
        }
        try {
            BmcItemOutcome outcome = BmcSettingItem.NETWORK_BOND.verify(webClient.bind(session), context.bmcTarget());
            switch (outcome.status()) {
                case APPLIED -> {
                    ledger.markItem(row, BmcSettingItem.NETWORK_BOND, outcome);
                    ledger.close(row, ProvisioningStatus.SUCCEEDED, SettingLedger.APPLIED, ledger.summaryOf(row), context.now());
                    settingCursor.afterAxis(SettingAxis.BMC, context.progress(), context.server().getId(), context.now());
                    log.info("[setting] {} — Bond 재접속 확인, BMC 표준 세팅 {} , 축 종결", context.server().getId(), ledger.summaryOf(row));
                }
                case MISMATCH -> {
                    ledger.markItem(row, BmcSettingItem.NETWORK_BOND, outcome);
                    close(context, row, SettingLedger.READBACK_MISMATCH, BmcSettingItem.NETWORK_BOND.name() + " — " + outcome.detail());
                }
                case RECONNECT_PENDING -> waitOrExpire(context, row, bondAt, "Bond 적용 뒤 아직 닿지 않습니다");
                case SKIPPED, REJECTED -> close(context, row, SettingLedger.WRITE_REJECTED,
                        BmcSettingItem.NETWORK_BOND.name() + " — " + outcome.detail());
            }
        } catch (AmiWebRequestException e) {
            if (e.getError() == AmiWebError.AUTH_FAILED) {
                close(context, row, SettingLedger.AUTH_REJECTED, e.getMessage());
            } else {
                waitOrExpire(context, row, bondAt, e.getMessage());
            }
        } finally {
            webClient.logout(session);
        }
    }

    private void waitOrExpire(SettingContext context, ProvisioningHistory row, LocalDateTime bondAt, String detail) {
        if (timeoutPolicy.isExpired(bondAt, timeoutPolicy.returnLimit(), context.now())) {
            close(context, row, SettingLedger.BMC_UNREACHABLE, detail);
        } else {
            log.info("[setting] {} — Bond 재접속 대기 : {}", context.server().getId(), detail);
        }
    }

    private void close(SettingContext context, ProvisioningHistory row, String reason, String detail) {
        ledger.close(row, ProvisioningStatus.FAILED, reason, detail, context.now());
        context.progress().markFailed(context.now());
        log.warn("[setting] {} — {} : {}", context.server().getId(), reason, detail);
    }
}
