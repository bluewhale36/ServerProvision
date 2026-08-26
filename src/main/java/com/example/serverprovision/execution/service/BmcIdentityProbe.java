package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.redfish.RedfishTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * BMC 신원 판정의 phase 무관 부분(E3-1 D-6 — E2-2 {@code BmcIdentityGuard} 에서 추출): 보드 시리얼 대조와,
 * 도달 불가일 때 같은 MAC 의 현재 주소를 다시 찾아 갱신하는 일까지. 원장 기록(즉시 실패 · 시한 실패)은
 * 컨텍스트를 아는 phase 별 가드가 각자 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BmcIdentityProbe {

    private final BmcAddressRediscovery addressRediscovery;

    /**
     * 판정 — MATCHED 면 그대로. UNREACHABLE 이면 주소를 다시 찾아 detail 을 갱신해 두고(다음 주기가 새 주소로
     * 다시 본다) UNREACHABLE 을 돌려준다. MISMATCHED 는 사건이라 손대지 않고 돌려준다.
     */
    public BmcIdentity probe(FirmwareUpdateProvider provider, RedfishTarget target,
                             String expectedBoardSerial, GuestServerDetail detail, String logTag) {
        BmcIdentity identity = provider.verifyIdentity(target, expectedBoardSerial);
        if (identity != BmcIdentity.UNREACHABLE || detail == null) {
            return identity;
        }
        Optional<IpAddressVO> found = addressRediscovery.currentAddressOf(detail.getBmcMac());
        if (found.isPresent() && !found.get().equals(detail.getBmcIp())) {
            log.info("[{}] BMC 주소 갱신 {} → {}", logTag,
                    detail.getBmcIp() == null ? "(없음)" : detail.getBmcIp().value(), found.get().value());
            detail.updateBmcIp(found.get());
        }
        return BmcIdentity.UNREACHABLE;
    }
}
