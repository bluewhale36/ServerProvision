package com.example.serverprovision.domain.node.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 서버 노드에 할당된 작업 유형을 나타내는 열거형이다.
 *
 * <p>역할: {@code ServerNode#targetJob} 필드의 값 도메인을 정의한다. 프로비저닝 파이프라인에서
 * 현재 서버에 어떤 작업이 계획되어 있는지를 나타내며, 향후 {@code ProvisioningStrategy} 선택 기준으로
 * 활용될 수 있다.</p>
 *
 * <p>유스케이스: {@code ServerNode#create} 팩토리 메소드는 신규 서버 등록 시 {@code IDLE}로 초기화한다.
 * 세팅 주문서 할당 시 관리자 UI 또는 서비스 계층에서 적절한 {@code JobType}으로 변경하여
 * 다음 PXE 부팅 시 올바른 프로비저닝 전략이 선택되도록 한다.</p>
 *
 * <p>확장 가이드: 새 작업 유형이 필요하면 이 열거형에 상수를 추가하고, 대응하는
 * {@code ProvisioningStrategy} 구현체와 관리자 UI 선택 옵션을 함께 추가한다.</p>
 */
@RequiredArgsConstructor
@Getter
public enum JobType {
    IDLE("대기"),
    BIOS_UPDATE("BIOS 업데이트"),
    BMC_FIRMWARE_UPDATE("BMC 펌웨어 업데이트"),
    OS_INSTALLATION("OS 설치");

    private final String description;
}
