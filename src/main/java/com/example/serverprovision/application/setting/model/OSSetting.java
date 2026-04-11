package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;

/**
 * OS 후처리 설정 단계를 표현하는 {@link AbstractSettingProcess} 구현체이다.
 *
 * <p>역할: OS 설치 이후 수행하는 후처리 설정(예: SELinux 모드, 추가 패키지 설치,
 * 시스템 서비스 활성화 등)을 나타내는 다섯 번째 단계이다.
 * 직렬화 시 {@code "type": "OS_SETTING"} 판별자가 JSON에 포함된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.OSSettingResolver}가
 * {@link com.example.serverprovision.application.setting.model.request.OSSettingRequest}를
 * 받아 이 클래스의 인스턴스를 생성하여 반환한다.
 * 현재 이 클래스는 필드 없는 스텁 상태이므로 {@link com.example.serverprovision.application.setting.model.SettingProcess}에
 * OS 후처리 설정 단계가 포함된다는 사실만 기록하고 실제 설정 데이터는 보유하지 않는다.</p>
 *
 * <p>확장 가이드: OS 후처리 설정 기능을 구현할 때 이 클래스에 관련 필드(예: SELinux 모드,
 * 활성화할 systemd 서비스 목록)를 추가한다. 동시에
 * {@link com.example.serverprovision.application.setting.model.request.OSSettingRequest}에
 * 동일한 필드를 추가하고, {@link com.example.serverprovision.application.setting.service.resolver.OSSettingResolver}에서
 * 요청 필드를 읽어 이 클래스의 생성자 또는 빌더에 전달하는 코드를 작성한다.
 * Jackson 역직렬화를 위해 {@code @JsonCreator} 생성자 또는 {@code @JsonProperty} 필드 어노테이션도 추가해야 한다.</p>
 */
public class OSSetting extends AbstractSettingProcess {

    public OSSetting() {
        super(SettingProcessStep.OS_SETTING);
    }
}
