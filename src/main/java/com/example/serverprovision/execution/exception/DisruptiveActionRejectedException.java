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
}
