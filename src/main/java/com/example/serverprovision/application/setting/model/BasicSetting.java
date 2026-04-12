package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;

/**
 * BIOS 설정 단계를 표현하는 {@link AbstractSettingProcess} 구현체이다.
 *
 * <p>역할: PXE 부팅 시 BIOS 설정(예: 부팅 순서, 보안 부팅 여부 등)을 적용하는 두 번째 단계를
 * 나타낸다. 직렬화 시 {@code "type": "BASIC_SETTING"} 판별자가 JSON에 포함된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.BasicSettingResolver}가
 * {@link com.example.serverprovision.application.setting.model.request.BasicSettingRequest}를
 * 받아 이 클래스의 인스턴스를 생성하여 반환한다.
 * 현재 이 클래스는 필드 없는 스텁 상태이므로 {@link com.example.serverprovision.application.setting.model.SettingProcess}에
 * BIOS 설정 단계가 포함된다는 사실만 기록하고 실제 설정 데이터는 보유하지 않는다.</p>
 *
 * <p>확장 가이드: BIOS 설정 기능을 구현할 때 이 클래스에 관련 필드(예: 부팅 순서 목록,
 * 보안 부팅 활성화 여부)를 추가한다. 동시에
 * {@link com.example.serverprovision.application.setting.model.request.BasicSettingRequest}에
 * 동일한 필드를 추가하고, {@link com.example.serverprovision.application.setting.service.resolver.BasicSettingResolver}에서
 * 요청 필드를 읽어 이 클래스의 생성자 또는 빌더에 전달하는 코드를 작성한다.
 * Jackson 역직렬화를 위해 {@code @JsonCreator} 생성자 또는 {@code @JsonProperty} 필드 어노테이션도 추가해야 한다.</p>
 */
public class BasicSetting extends AbstractSettingProcess {

    public BasicSetting() {
        super(SettingProcessStep.BASIC_SETTING);
    }
}
