package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * OS 후처리(Post-install) 설정 단계를 표현하는 {@link AbstractSettingProcess} 구현체.
 *
 * <p>{@link OSMetadataDTO}(대상 OS 타입·버전)와 도메인 계층의
 * {@link com.example.serverprovision.domain.os.model.setting.OSSetting}(OS 계열/버전별 후처리 스크립트 로직)을
 * 함께 보유하는 래퍼이다. 직렬화 시 {@code "type": "OS_SETTING"} 판별자가 포함되며, 생성자에서
 * {@code osSetting.isCompatible(...)} 로 메타데이터와의 호환성을 검증한다 (불일치 시
 * {@link IllegalArgumentException}). 도메인 레이어와 클래스명이 같으므로 참조 시 FQCN 을 사용한다.</p>
 */
@Getter
public class OSSetting extends AbstractSettingProcess {

    /**
     * OS 후처리가 적용될 대상 OS 의 타입·버전 메타데이터. 호환성 검증의 기준이다.
     */
    @NotNull(message = "OS 메타데이터는 필수 값입니다.")
    private final OSMetadataDTO osMetadata;

    /**
     * 도메인 계층의 OS 후처리 설정 객체. SELinux 모드, 패키지, 서비스 등 OS 계열별 후처리 로직을 보유한다.
     */
    @NotNull(message = "OS 설정 정보는 필수 값입니다.")
    private final com.example.serverprovision.domain.os.model.setting.OSSetting osSetting;

    @JsonCreator
    public OSSetting(
            @JsonProperty("osMetadata") OSMetadataDTO osMetadata,
            @JsonProperty("osSetting")  com.example.serverprovision.domain.os.model.setting.OSSetting osSetting
    ) {
        super(SettingProcessStep.OS_SETTING);

        if (!osSetting.isCompatible(osMetadata.osName(), osMetadata.osVersion())) {
            throw new IllegalArgumentException("OS 메타데이터와 OS 설정 정보가 호환되지 않습니다.");
        }

        this.osMetadata = osMetadata;
        this.osSetting = osSetting;
    }
}
