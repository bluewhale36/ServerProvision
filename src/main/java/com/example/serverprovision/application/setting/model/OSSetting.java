package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * OS 후처리 설정 단계를 표현하는 {@link AbstractSettingProcess} 구현체이다.
 *
 * <p>역할: OS 설치 이후 수행하는 후처리 설정(SELinux 모드, 추가 패키지 설치,
 * 시스템 서비스 활성화 등)을 나타내는 다섯 번째 단계이다.
 * 직렬화 시 {@code "type": "OS_SETTING"} 판별자가 JSON에 포함된다.</p>
 *
 * <p>유스케이스: {@link com.example.serverprovision.application.setting.service.resolver.OSSettingResolver}가
 * {@link com.example.serverprovision.application.setting.model.request.OSSettingRequest}를
 * 받아 이 클래스의 인스턴스를 생성하여 반환한다.
 * DB에 JSON으로 저장된 후 역직렬화 시 {@code @JsonCreator} 생성자가 사용된다.</p>
 *
 * <p>Kickstart 연계: {@code selinuxMode}, {@code enabledServices}, {@code additionalPackages}는
 * {@link com.example.serverprovision.domain.os.model.setting.RockyLinuxSetting#getPostInstallScript}에
 * 전달되어 {@code %post} 섹션 스크립트를 생성하는 데 사용된다.</p>
 */
@Getter
public class OSSetting extends AbstractSettingProcess {

    /**
     * SELinux 모드이다. {@code "enforcing"}, {@code "permissive"}, {@code "disabled"} 중 하나.
     */
    private final String selinuxMode;

    /**
     * {@code systemctl enable} 할 서비스 목록이다. 빈 리스트이면 해당 섹션을 생략한다.
     */
    private final List<String> enabledServices;

    /**
     * {@code dnf install} 할 추가 패키지 목록이다. 빈 리스트이면 해당 섹션을 생략한다.
     */
    private final List<String> additionalPackages;

    /**
     * Jackson 역직렬화 및 일반 인스턴스 생성을 위한 생성자이다.
     *
     * @param selinuxMode        SELinux 모드 ({@code "enforcing"}, {@code "permissive"}, {@code "disabled"})
     * @param enabledServices    활성화할 서비스 목록 (null이면 빈 리스트로 처리)
     * @param additionalPackages 추가 설치할 패키지 목록 (null이면 빈 리스트로 처리)
     */
    @JsonCreator
    public OSSetting(
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("enabledServices")    List<String> enabledServices,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        super(SettingProcessStep.OS_SETTING);
        this.selinuxMode        = selinuxMode;
        this.enabledServices    = enabledServices    != null ? enabledServices    : List.of();
        this.additionalPackages = additionalPackages != null ? additionalPackages : List.of();
    }
}
