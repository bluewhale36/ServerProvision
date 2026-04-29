package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * Rocky Linux 10 계열 Post-install 설정.
 *
 * <p>호환 버전: {@code 10.0}. Kickstart {@code %post} 생성은 RHELBasedSetting 을 그대로 사용한다.</p>
 */
public class RockyLinux10Setting extends RHELBasedSetting {

    private static final List<String> COMPATIBLE_VERSIONS = List.of("10.0");

    @Builder
    @JsonCreator
    protected RockyLinux10Setting(
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("services")           List<ServiceDirective> services,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        super(OSName.ROCKY_LINUX, COMPATIBLE_VERSIONS, selinuxMode, services, additionalPackages);
    }
}
