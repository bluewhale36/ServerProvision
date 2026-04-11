package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * 세팅 주문서의 개별 프로세스 단계를 표현하는 다형성 추상 기반 클래스이다.
 *
 * <p>역할: {@code @JsonTypeInfo} + {@code @JsonSubTypes}로 Jackson 다형성 직렬화/역직렬화를
 * 구현한다. {@code type} 판별자 필드를 통해 구현체 4종을 식별하며, {@link SettingProcessStep}으로
 * 실행 순서를 정의한다. {@link Comparable} 구현으로 {@code processStep.getOrder()} 기준
 * 정렬이 가능하다.</p>
 *
 * <p>유스케이스: {@link SettingProcess}의 {@code processList}에 담겨 DB {@code process} 컬럼에
 * JSON 배열로 저장된다. {@link com.example.serverprovision.application.setting.converter.SettingProcessConverter}가
 * {@link tools.jackson.databind.ObjectMapper}로 직렬화/역직렬화를 수행하며, 역직렬화 시
 * JSON의 {@code "type"} 필드 값으로 구현체를 결정한다.
 * {@link com.example.serverprovision.application.setting.service.SettingService#save}에서
 * {@code .sorted()}를 호출할 때 이 클래스의 {@link #compareTo}가 사용된다.</p>
 *
 * <p>확장 가이드: 새 프로세스 타입을 추가할 때 이 클래스의 {@code @JsonSubTypes}에
 * 새 {@code @JsonSubTypes.Type}을 등록한다. 동시에 {@link SettingProcessStep}에 대응하는
 * 상수를 추가하고, {@link com.example.serverprovision.application.setting.model.request.AbstractProcessRequest}의
 * {@code @JsonSubTypes}에도 Request 타입을 등록해야 한다.
 * 새 구현체는 반드시 {@code super(SettingProcessStep.XXX)}를 호출하는 생성자를 제공해야
 * {@code processStep}이 초기화된다.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicUpdate.class, name = "BASIC_UPDATES"),
        @JsonSubTypes.Type(value = BasicSetting.class, name = "BASIC_SETTING"),
        @JsonSubTypes.Type(value = OSInstallation.class, name = "OS_INSTALLATION"),
        @JsonSubTypes.Type(value = OSSetting.class, name = "OS_SETTING")
})
public abstract class AbstractSettingProcess implements Comparable<AbstractSettingProcess> {

    /**
     * 이 프로세스 단계의 실행 순서 및 유형을 정의하는 열거형 값이다.
     * {@link SettingProcessStep#getOrder()} 기준으로 정렬되며, 생성자에서만 설정된다.
     */
    private final SettingProcessStep processStep;

    protected AbstractSettingProcess(SettingProcessStep processStep) {
        this.processStep = processStep;
    }

    /**
     * 이 프로세스 단계의 {@link SettingProcessStep}을 반환한다.
     *
     * @return 이 단계의 실행 순서와 유형을 나타내는 {@link SettingProcessStep}
     */
    public final SettingProcessStep getProcessStep() {
        return processStep;
    }

    /**
     * {@code processStep.getOrder()} 기준으로 두 프로세스를 비교한다.
     *
     * <p>{@link com.example.serverprovision.application.setting.service.SettingService#save}에서
     * {@code .sorted()}로 단계 순서를 보장할 때 사용된다.</p>
     *
     * @param o 비교 대상
     * @return 음수·0·양수 (표준 {@link Comparable} 계약)
     */
    @Override
    public final int compareTo(AbstractSettingProcess o) {
        return Integer.compare(this.processStep.getOrder(), o.getProcessStep().getOrder());
    }
}
