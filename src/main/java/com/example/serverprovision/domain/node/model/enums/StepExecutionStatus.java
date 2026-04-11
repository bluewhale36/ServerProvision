package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StepExecutionStatus {

    PENDING("대기"),           // 아직 실행되지 않은 단계
    IN_PROGRESS("진행 중"),    // iPXE 스크립트 반환 후 서버가 실행 중인 단계
    COMPLETED("완료"),         // 정상 완료
    FAILED("실패");            // 실패 (수동 개입 필요)

    private final String description;
}
