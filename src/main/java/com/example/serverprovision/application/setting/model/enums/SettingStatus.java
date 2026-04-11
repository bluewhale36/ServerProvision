package com.example.serverprovision.application.setting.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 세팅 주문서({@link com.example.serverprovision.application.setting.domain.entity.ServerSetting})의
 * 현재 진행 상태를 나타내는 열거형이다.
 *
 * <p>역할: {@link com.example.serverprovision.application.setting.domain.entity.ServerSetting#getStatus}
 * 필드의 타입이다. DB에는 {@code EnumType.STRING}으로 문자열 그대로 저장된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService#save}에서
 * 새로 생성된 {@link com.example.serverprovision.application.setting.domain.entity.ServerSetting}의
 * 초기 상태로 {@link #PENDING}이 설정된다.
 * {@link com.example.serverprovision.application.setting.dto.SettingCreateResponse}에 포함되어
 * 201 Created 응답의 {@code status} 필드로 반환된다.
 * PXE 부팅 흐름에서 단계가 진행될 때 상태가 {@link #IN_PROGRESS} → {@link #COMPLETED} 또는
 * {@link #FAILED}로 전이되는 로직은 현재 미구현 상태이다.</p>
 *
 * <p>확장 가이드: 상태 전이 로직을 구현할 때 {@link com.example.serverprovision.domain.provisioning}
 * 패키지의 Strategy 계층에서 단계 완료/실패 시 이 열거형 값을 갱신하도록 구현한다.
 * 일부 서버만 성공한 경우를 표현하기 위한 {@link #PARTIAL_SUCCESS}가 이미 정의되어 있으므로,
 * 복수의 서버에 동일 주문서를 적용하는 기능 구현 시 이 상수를 활용한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum SettingStatus {

    /** 세팅 주문서가 생성되었으나 아직 실행이 시작되지 않은 대기 상태이다. 최초 저장 시 초기값으로 설정된다. */
    PENDING("대기 중"),

    /** 하나 이상의 단계가 실행 중인 상태이다. */
    IN_PROGRESS("진행 중"),

    /** 모든 단계가 성공적으로 완료된 상태이다. */
    COMPLETED("전체 완료"),

    /** 복수의 서버에 적용 시 일부는 성공하고 일부는 실패한 상태이다. */
    PARTIAL_SUCCESS("부분 성공"),

    /** 모든 서버에서 실패한 상태이다. */
    FAILED("전체 실패"),

    /** 실행 전 취소된 상태이다. */
    CANCELED("취소됨");

    /** 사용자에게 표시되는 상태 명칭이다. */
    private final String description;
}