package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * CentOS 7 계열 Post-install 설정.
 *
 * <p>호환 버전: {@code 7.9}, {@code 7.10}. Kickstart {@code %post} 생성은 RHELBasedSetting 을 그대로
 * 사용한다.</p>
 */
public class CentOS7Setting extends RHELBasedSetting {

    private static final List<String> COMPATIBLE_VERSIONS = List.of("7.9", "7.10");

    @Builder
    @JsonCreator
    protected CentOS7Setting(
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("services")           List<ServiceDirective> services,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        super(OSName.CENTOS, COMPATIBLE_VERSIONS, selinuxMode, services, additionalPackages);
    }
}
