package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서버 노드의 전체 프로비저닝 진행 상태를 나타내는 열거형이다.
 *
 * <p>역할: {@code ServerNode#status} 필드의 값 도메인을 정의한다. 노드 단위의 거시적 상태를
 * 나타내며, 개별 단계의 세부 상태는 {@code StepExecutionStatus}가 담당한다.</p>
 *
 * <p>유스케이스: {@code ServerNodeRepository#findAvailableNodeByMacAddress}가
 * {@code COMPLETED} 또는 {@code FAILED} 상태 노드를 결과에서 제외하여, 재부팅 시
 * 프로비저닝이 완료된 서버가 다시 PXE 부팅 대상으로 잡히지 않도록 한다.
 * {@code ServerNode#startProvisioning}이 {@code IN_PROGRESS}로 전이시킨다.</p>
 *
 * <p>확장 가이드: 상태를 추가할 경우 {@code ServerNodeRepository}의 필터 조건과 관리자
 * 대시보드 UI의 상태 표시 로직도 함께 검토한다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum ProvisioningStatus {
    NEW("신규"),
    IN_PROGRESS("진행 중"),
    COMPLETED("완료"),
    FAILED("실패");

    private final String description;
}
