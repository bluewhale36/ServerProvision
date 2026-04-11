package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;

/**
 * 주문서 생성 시 각 {@link AbstractProcessRequest} 구현체를 도메인 모델
 * {@link AbstractSettingProcess} 로 해석하는 전략 인터페이스.
 *
 * <p>{@link com.example.serverprovision.domain.provisioning.model.strategy.ProvisioningStrategy}
 * 와 동일한 패턴으로, 각 구현체는 Spring {@code @Component} 로 등록되어
 * {@code List<SettingProcessResolver>} 로 주입된 뒤 Stream dispatch 된다.
 */
public interface SettingProcessResolver {

    /** 이 resolver 가 해당 요청 타입을 해석할 수 있는지 여부. */
    boolean supports(AbstractProcessRequest request);

    /** 요청 DTO 를 도메인 모델로 해석. 내부 검증 실패 시 IllegalArgumentException. */
    AbstractSettingProcess resolve(AbstractProcessRequest request);
}
