package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;

/**
 * 세팅 주문서 생성 시 각 {@link AbstractProcessRequest} 구현체를 도메인 모델
 * {@link AbstractSettingProcess}로 변환하는 Strategy 인터페이스이다.
 *
 * <p>역할: 프로세스 타입별 Request → Domain Model 변환 및 검증 로직을 캡슐화한다.
 * {@link com.example.serverprovision.domain.provisioning.model.strategy.ProvisioningStrategy}와
 * 동일한 Strategy 패턴을 적용하며, 각 구현체는 {@code @Component}로 등록되어
 * {@link com.example.serverprovision.application.setting.service.SettingService}에
 * {@code List<SettingProcessResolver>}로 주입된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService#save}가
 * 각 {@link AbstractProcessRequest}에 대해 {@link #supports}로 담당 구현체를 찾고,
 * {@link #resolve}로 도메인 모델을 생성한다. 현재 구현체는
 * {@link BasicUpdateResolver}, {@link BasicSettingResolver},
 * {@link OSInstallationResolver}, {@link OSSettingResolver} 4종이 존재한다.</p>
 *
 * <p>확장 가이드: 새 프로세스 타입 지원 시 이 인터페이스를 구현하는 클래스를 작성하고
 * {@code @Component}로 등록하면 {@code SettingService}가 자동으로 인식한다.
 * 동시에 {@link AbstractProcessRequest}의 {@code @JsonSubTypes}와
 * {@link AbstractSettingProcess}의 {@code @JsonSubTypes}에 새 타입을 등록해야
 * JSON 직렬화/역직렬화가 정상 동작한다.
 * {@link com.example.serverprovision.application.setting.model.enums.SettingProcessStep}에도
 * 대응하는 상수를 추가해야 실행 순서가 정렬된다.</p>
 */
public interface SettingProcessResolver {

    /**
     * 이 Resolver가 주어진 요청을 처리할 수 있는지 여부를 반환한다.
     *
     * @param request 처리 가능 여부를 확인할 요청 DTO
     * @return 처리 가능하면 {@code true}
     */
    boolean supports(AbstractProcessRequest request);

    /**
     * 요청 DTO를 도메인 모델로 해석하여 반환한다.
     *
     * @param request 변환할 요청 DTO. {@link #supports}가 {@code true}인 타입이어야 한다.
     * @return 변환된 {@link AbstractSettingProcess} 도메인 모델
     * @throws com.example.serverprovision.global.exception.FieldValidationException
     *         엔티티 조회 실패 또는 도메인 검증 실패 시
     */
    AbstractSettingProcess resolve(AbstractProcessRequest request);
}
