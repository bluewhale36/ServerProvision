package com.example.serverprovision.execution.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * 실패한 서버의 재시도를 막는 사유. 화면의 경보 띠 · 안내 줄과 재시도 409 가 같은 상수를 읽는다(E4-1-b-7 에서 펌웨어 하나를 넓혔다).
 *
 * @param failedPhase 그 phase 에서 실패하면 언제나 막는다. 원장 사실로 가르는 사유는 null
 * @param alertKey    경보 띠 문구 키
 * @param hintKey     동작 줄 아래 안내 문구 키
 */
public enum RetryBlock {

    /** 펌웨어를 쓰다가 난 실패. 원인을 모른 채 다시 쓰면 장비가 부팅하지 못할 수 있다(DEC-4). 판정은 원장 사실이 필요해 RetryPolicy 가 한다. */
    FIRMWARE_FLASH(null, "provisioning.retry.blocked.tooltip", "provisioning.retry.blocked.hint.firmware",
            "펌웨어를 쓰는 도중 실패한 서버는 원인을 확인하기 전에 다시 시도할 수 없습니다."),
    /** OS 후처리는 설치 중에 적용되어 같은 할당으로 다시 해도 결과가 같고, 설치 뒤 서버는 PXE 를 타지 않아 되감을 곳이 없다. */
    OS_SETTING_APPLIED(ProvisioningPhase.OS_SETTING, "provisioning.retry.blocked.os-setting",
            "provisioning.retry.blocked.hint.os-setting",
            "OS 후처리는 설치 중에 적용되어 다시 시도해도 결과가 같습니다.");

    private final ProvisioningPhase failedPhase;
    private final String alertKey;
    private final String hintKey;
    private final String rejectMessage;

    RetryBlock(ProvisioningPhase failedPhase, String alertKey, String hintKey, String rejectMessage) {
        this.failedPhase = failedPhase;
        this.alertKey = alertKey;
        this.hintKey = hintKey;
        this.rejectMessage = rejectMessage;
    }

    /** 그 phase 의 실패면 언제나 막는 사유. */
    public static Optional<RetryBlock> ofFailedPhase(ProvisioningPhase phase) {
        return Arrays.stream(values()).filter(block -> block.failedPhase != null && block.failedPhase == phase).findFirst();
    }

    public String getAlertKey() {
        return alertKey;
    }

    public String getHintKey() {
        return hintKey;
    }

    public String getRejectMessage() {
        return rejectMessage;
    }
}
