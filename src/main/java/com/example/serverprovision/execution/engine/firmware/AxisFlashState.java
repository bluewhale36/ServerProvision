package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.enums.ProvisioningStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 한 축의 집행 진행 상태(E2-2) — 화면 배지가 읽는 어휘다. 상수가 표시 라벨을 함께 들어 소비처가
 * 상태를 보고 문구를 다시 고르지 않는다({@link FirmwareAxisReason} 과 같은 관례).
 *
 * <p>라벨은 명사형이고 진행 중인 것만 "~ 중" 을 쓴다(문구 규약).</p>
 */
@RequiredArgsConstructor
@Getter
public enum AxisFlashState {

    /** 아직 차례가 오지 않았다. */
    PENDING("대기"),

    /** 굽고 있다 — 이 축의 Task 가 진행 중이다. */
    RUNNING("굽는 중"),

    /** 전송과 반영이 끝났다. */
    SUCCEEDED("완료"),

    /** 판정이 굽지 않기로 했다 — 그 축만 건너뛰고 나머지는 진행한다. */
    SKIPPED("건너뜀"),

    /** 이 축에서 실패했다. */
    FAILED("실패");

    private final String label;

    /**
     * 원장 행의 결과를 화면 어휘로 옮긴다 — 이 매핑을 화면 조립부에 두면 상태가 늘 때마다 그 분기가
     * 함께 자란다. 지식이 화면 enum 자신에게 있는 편이 맞다.
     */
    public static AxisFlashState of(ProvisioningStatus status) {
        if (status == null) {
            return PENDING;
        }
        return switch (status) {
            case RUNNING -> RUNNING;
            case SUCCEEDED -> SUCCEEDED;
            case SKIPPED -> SKIPPED;
            case FAILED -> FAILED;
            case PENDING -> PENDING;
        };
    }
}
