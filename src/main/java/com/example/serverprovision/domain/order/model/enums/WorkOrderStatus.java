package com.example.serverprovision.domain.order.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 작업 지시서({@link com.example.serverprovision.domain.order.entity.WorkOrder})의
 * 현재 진행 상태를 나타내는 열거형이다.
 */
@Getter
@RequiredArgsConstructor
public enum WorkOrderStatus {

    /** 작업 지시서가 생성된 초기 상태이다. */
    CREATED("생성됨"),

    /** 프로비저닝이 진행 중인 상태이다. */
    IN_PROGRESS("진행 중"),

    /** 모든 단계가 성공적으로 완료된 상태이다. */
    COMPLETED("완료"),

    /** 프로비저닝 중 실패한 상태이다. */
    FAILED("실패"),

    /** 관리자가 수동으로 취소한 상태이다. */
    CANCELLED("취소됨");

    /** 사용자에게 표시되는 상태 명칭이다. */
    private final String description;
}
