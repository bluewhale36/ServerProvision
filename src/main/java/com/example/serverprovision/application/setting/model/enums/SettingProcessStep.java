package com.example.serverprovision.application.setting.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 세팅 주문서의 각 프로세스 단계 유형과 실행 순서를 정의하는 열거형이다.
 *
 * <p>역할: {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}가
 * 보유하는 {@code processStep} 필드의 타입이다. {@link #getOrder()}가 반환하는 정수값이
 * 단계 실행 순서의 기준이 되며, {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess#compareTo}에서
 * 정렬에 사용된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService#save}에서
 * {@code .sorted()}로 프로세스 목록을 정렬할 때 각 {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}의
 * {@link #getOrder()} 값이 비교 기준이 된다. {@link com.example.serverprovision.application.setting.controller.SettingController#newSetting}은
 * {@code List.of(SettingProcessStep.values())}를 모델에 담아 폼의 단계 선택 UI를 구성한다.
 * {@link com.example.serverprovision.domain.node.model.ServerNode}의 {@code currentStepIndex}가
 * 이 순서와 연동되므로, order 값 변경 시 {@code currentStepIndex} 해석 로직도 함께 검토해야 한다.</p>
 *
 * <p>확장 가이드: 새 단계를 추가할 때 order 값은 기존 값과 겹치지 않게 선택한다
 * (order 3은 {@code BASIC_TEST}를 위해 예약·주석 처리된 상태이다).
 * 새 상수를 추가하면 {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess}의
 * {@code @JsonSubTypes}, {@link com.example.serverprovision.application.setting.model.request.AbstractProcessRequest}의
 * {@code @JsonSubTypes}, 그리고 {@link com.example.serverprovision.application.setting.service.resolver.SettingProcessResolver}
 * 구현체도 함께 추가해야 한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum SettingProcessStep {

    /** BIOS/BMC 펌웨어 업데이트 단계. order=1 (첫 번째로 실행). */
    BASIC_UPDATE(1, "BIOS/BMC 업데이트"),

    /** BIOS 설정 단계 (부팅 순서, 보안 부팅 등). order=2 (두 번째로 실행). */
    BASIC_SETTING(2, "BIOS 설정"),

//    BASIC_TEST(3, "기본 테스트"),  // order=3은 기본 테스트 단계를 위해 예약됨 (미구현)

    /** OS 설치 단계. order=4 (네 번째로 실행). {@link com.example.serverprovision.application.setting.model.OSInstallation}에 대응. */
    OS_INSTALLATION(4, "OS 설치"),

    /** OS 후처리 설정 단계. order=5 (다섯 번째로 실행). {@link com.example.serverprovision.application.setting.model.OSSetting}에 대응. */
    OS_SETTING(5, "OS 설정");

    /**
     * 단계 실행 순서를 나타내는 양의 정수이다.
     * {@link com.example.serverprovision.application.setting.model.AbstractSettingProcess#compareTo}에서
     * 정렬 기준으로 사용된다. 값이 작을수록 먼저 실행된다.
     */
    private final int order;

    /** 사용자에게 표시되는 단계 명칭이다. 폼 UI에서 라벨로 사용된다. */
    private final String description;
}
