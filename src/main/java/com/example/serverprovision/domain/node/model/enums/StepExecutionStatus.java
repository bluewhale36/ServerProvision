package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * {@code NodeStepExecution} 개별 단계의 실행 상태를 나타내는 열거형이다.
 *
 * <p>역할: {@code NodeStepExecution#status} 필드의 값 도메인을 정의하며, 세팅 주문서의
 * 각 단계별 진행 상태를 세밀하게 추적한다. 노드 전체의 거시적 상태는 {@code ProvisioningStatus}가
 * 담당하며, 이 열거형은 단계 단위의 미시적 상태를 표현한다.</p>
 *
 * <p>유스케이스: {@code ProvisioningScriptService#generateIPXEScript}가
 * {@code NodeStepExecutionRepository}를 통해 {@code PENDING} 상태 중 {@code stepOrder}가
 * 가장 낮은 단계를 조회하여 다음 실행 대상을 결정한다. iPXE 스크립트 반환 직전
 * {@code NodeStepExecution#markInProgress}로 {@code IN_PROGRESS}로 전이된다.</p>
 *
 * <p>확장 가이드: {@code FAILED} 상태 후 자동 재시도 정책을 추가할 경우 {@code RETRYING}
 * 같은 중간 상태를 이 열거형에 추가하고, 재시도 트리거 조건을 {@code ProvisioningScriptService}에
 * 구현한다. 상태 추가 시 관리자 대시보드의 상태 표시 UI도 함께 검토한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum StepExecutionStatus {

    /** 아직 실행되지 않은 단계이다. 다음 실행 대상 선택 시 이 상태의 레코드가 조회된다. */
    PENDING("대기"),
    /** iPXE 스크립트 반환 후 서버가 해당 단계를 실행 중인 상태이다. */
    IN_PROGRESS("진행 중"),
    /** 단계가 정상 완료된 상태이다. */
    COMPLETED("완료"),
    /** 단계가 실패하여 수동 개입이 필요한 상태이다. */
    FAILED("실패");

    private final String description;
}
