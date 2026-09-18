package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.engine.phase.ProvisioningCompletedEvent;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.global.redfish.BootOrderPolicy;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 종단 시 부트 순서 정착(HF20) — 프로비저닝이 끝난 게스트의 BootOrder 첫 항목을 디스크(Windows Boot Manager 우선)로 두어,
 * 다음 POST 에서 BIOS 의 맞바꿈 규칙이 Hard Disk 를 Fixed Boot Order 1순위로 되돌리게 한다. 커밋 뒤(AFTER_COMMIT)에만 BMC 를
 * 만지고, 트랜잭션이 없으면(단위 테스트) 즉시 실행한다. 실패는 best effort — 종단은 이미 확정됐고 로그가 사유를 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootOrderSettlement {

    private final GuestServerDetailRepository detailRepository;
    private final RedfishPowerService powerService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCompleted(ProvisioningCompletedEvent event) {
        GuestServerDetail detail = detailRepository.findByGuestServer_Id(event.guestServerId()).orElse(null);
        if (detail == null || detail.getBmcIp() == null) {
            log.debug("[boot-order] {} — 종단인데 BMC 미검출, 정착 없음", event.guestServerId());
            return;
        }
        RedfishTarget target = new RedfishTarget(detail.getBmcIp().value(), detail.getBoardSerial());
        try {
            PowerControlResult result = powerService.settleBootOrder(target, BootOrderPolicy.DISK_FIRST);
            if (result.kind() == PowerControlResult.Kind.FAILED) {
                log.warn("[boot-order] {} — 종단 정착 실패(best effort) : {}", event.guestServerId(), result.message());
                return;
            }
            log.info("[boot-order] {} — 종단 정착 : {}", event.guestServerId(), result.message());
        } catch (RuntimeException e) {
            log.warn("[boot-order] {} — 종단 정착 중 예외 : {}", event.guestServerId(), e.toString());
        }
    }
}
