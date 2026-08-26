package com.example.serverprovision.execution.exception;

import com.example.serverprovision.global.exception.ConflictException;

import java.util.UUID;

/**
 * 펌웨어를 굽는 중의 중단성 조작(전원 제어 · 수동 실패 전환 · 회수) 거절(R13 후속, 2026-08-25).
 * 정상 흐름은 UI 가 버튼을 막으므로 direct POST 안전망이다. 판정 SSOT 는
 * {@code ProvisioningProgress.isDisruptionBlocked}.
 */
public class DisruptiveActionRejectedException extends ConflictException {

    public DisruptiveActionRejectedException(UUID id) {
        super("펌웨어를 굽는 중에는 이 조작을 할 수 없습니다 — 집행이 끝난 뒤 다시 시도하십시오. id=" + id);
    }

    /** 사유가 SSOT 에서 오는 변형(U6) — 전원 조작은 {@code GuestServer.powerControlBlockReason} 의 문구를 그대로 싣는다. */
    public DisruptiveActionRejectedException(UUID id, String reason) {
        super(reason + " id=" + id);
    }
}
