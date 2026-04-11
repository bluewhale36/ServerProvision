package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.BasicSetting;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.BasicSettingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link BasicSettingRequest} → {@link BasicSetting} 해석.
 *
 * <p>현재 {@link BasicSetting} 은 필드 없는 스텁이므로 빈 도메인 객체를 반환한다.
 * 실제 BIOS 설정 필드/로직은 별도 사이클에서 채워질 예정.
 */
@Slf4j
@Component
public class BasicSettingResolver implements SettingProcessResolver {

    @Override
    public boolean supports(AbstractProcessRequest request) {
        return request instanceof BasicSettingRequest;
    }

    @Override
    public AbstractSettingProcess resolve(AbstractProcessRequest request) {
        log.info("[BasicSettingResolver] BasicSetting 생성.");
        return new BasicSetting();
    }
}
