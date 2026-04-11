package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.BasicSetting;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.BasicSettingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link BasicSettingRequest}를 {@link BasicSetting} 도메인 모델로 변환하는 Resolver이다.
 *
 * <p>역할: {@link BasicSettingRequest} 타입의 요청을 {@link BasicSetting} 도메인 객체로
 * 변환한다. 현재 {@link BasicSetting}은 필드 없는 스텁이므로 빈 인스턴스를 반환한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService}가
 * {@link BasicSettingRequest} 타입을 만나면 이 Resolver를 선택한다.
 * 현재 {@link BasicSetting}이 스텁 상태이므로 변환 로직 없이 {@code new BasicSetting()}만
 * 반환하며, 실제 BIOS 설정 관련 필드 및 검증 로직은 별도 사이클에서 채워질 예정이다.</p>
 *
 * <p>확장 가이드: {@link BasicSetting}에 BIOS 설정 관련 필드(예: 부팅 순서, 보안 부팅 여부)가
 * 추가되면 {@link BasicSettingRequest}에 동일한 필드를 추가하고, 이 Resolver에서
 * 요청 필드를 읽어 {@link BasicSetting} 생성자 또는 빌더에 전달하는 코드를 작성한다.
 * DB 조회가 필요하면 {@link BasicUpdateResolver}와 동일한 패턴으로 Repository를 주입한다.</p>
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
