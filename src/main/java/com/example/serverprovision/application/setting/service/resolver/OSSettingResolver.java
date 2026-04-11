package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.OSSetting;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link OSSettingRequest} → {@link OSSetting} 해석.
 *
 * <p>현재 {@link OSSetting} 은 필드 없는 스텁이므로 빈 도메인 객체를 반환한다.
 * 실제 OS 후처리 설정 필드/로직은 별도 사이클에서 채워질 예정.
 *
 * <p>참고: 기존 {@code SettingService} 구현은 이 타입을 리스트에 추가조차 하지 않는
 * silent-skip 버그를 가지고 있었다. 이 resolver 가 도입되면서 OS_SETTING 단계도
 * 정상적으로 {@code SettingProcess} 에 포함된다.
 */
@Slf4j
@Component
public class OSSettingResolver implements SettingProcessResolver {

    @Override
    public boolean supports(AbstractProcessRequest request) {
        return request instanceof OSSettingRequest;
    }

    @Override
    public AbstractSettingProcess resolve(AbstractProcessRequest request) {
        log.info("[OSSettingResolver] OSSetting 생성.");
        return new OSSetting();
    }
}
