package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.engine.firmware.AxisResolution.AxisOutcome;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 펌웨어 축 판정 사유(E2-1-b 진리표) — 사유마다 <b>결과 등급과 사용자 문구를 자기가 든다</b>.
 * 소비처(화면 카드 · 대기 스크립트 · 로그)가 사유를 보고 다시 분기하지 않게 하기 위함이며,
 * 사유가 늘어도 소비처는 자라지 않는다(RedfishError 선례와 같은 관례).
 *
 * <p>건너뜀(SKIPPED)과 차단(BLOCKED)의 경계는 "그 축을 포기하면 나머지를 진행할 수 있는가" 다.
 * 후보가 없거나 지정한 자원이 사라진 것은 그 축만 포기하면 되지만, 무결성이 깨진 재료는 계보를
 * 확인할 수 없어 굽는 행위 자체를 시작하면 안 된다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum FirmwareAxisReason {

    /** 등록이 아예 없는 경우와 전부 비활성인 경우를 함께 덮는다 — 둘 다 "지금 쓸 수 있는 것이 없다" 이다. */
    NO_CANDIDATE(AxisOutcome.SKIPPED, "이 보드에 사용할 수 있는 펌웨어가 없어 이 축을 건너뜁니다"),
    REFERENCE_GONE(AxisOutcome.SKIPPED, "정의서가 지정한 펌웨어가 더 이상 없어 이 축을 건너뜁니다"),
    DISABLED(AxisOutcome.SKIPPED, "정의서가 지정한 펌웨어가 비활성이라 이 축을 건너뜁니다"),
    FILE_MISSING(AxisOutcome.SKIPPED, "펌웨어 파일을 찾을 수 없어 이 축을 건너뜁니다"),

    MARKER_MISSING(AxisOutcome.BLOCKED, "펌웨어의 무결성 표식이 없습니다"),
    SIGNATURE_INVALID(AxisOutcome.BLOCKED, "펌웨어의 무결성 표식이 유효하지 않습니다"),
    BOARD_MISMATCH(AxisOutcome.BLOCKED, "정의서가 지정한 메인보드가 이 서버의 보드와 다릅니다");

    private final AxisOutcome outcome;
    private final String userMessage;
}
