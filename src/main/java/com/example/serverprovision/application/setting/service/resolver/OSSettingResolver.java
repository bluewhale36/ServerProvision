package com.example.serverprovision.application.setting.service.resolver;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.OSSetting;
import com.example.serverprovision.application.setting.model.request.AbstractProcessRequest;
import com.example.serverprovision.application.setting.model.request.OSSettingRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link OSSettingRequest}를 {@link OSSetting} 도메인 모델로 변환하는 Resolver이다.
 *
 * <p>역할: {@link OSSettingRequest} 타입의 요청을 {@link OSSetting} 도메인 객체로
 * 변환한다. 현재 {@link OSSetting}은 필드 없는 스텁이므로 빈 인스턴스를 반환한다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.SettingService}가
 * {@link OSSettingRequest} 타입을 만나면 이 Resolver를 선택한다.
 * 현재 {@link OSSetting}이 스텁 상태이므로 변환 로직 없이 {@code new OSSetting()}만
 * 반환하며, 실제 OS 후처리 설정(예: SELinux 모드, 패키지 추가 설치, 서비스 활성화) 관련
 * 필드 및 검증 로직은 별도 사이클에서 채워질 예정이다.</p>
 *
 * <p>확장 가이드: {@link OSSetting}에 OS 후처리 설정 필드가 추가되면
 * {@link OSSettingRequest}에 동일한 필드를 추가하고, 이 Resolver에서 요청 필드를
 * 읽어 {@link OSSetting} 생성자 또는 빌더에 전달하는 코드를 작성한다.
 * OS 타입별로 다른 설정이 필요하다면 {@link OSInstallationResolver}처럼
 * OS 타입 switch로 분기하여 구현한다.</p>
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
