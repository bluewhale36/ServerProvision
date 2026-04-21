package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * Rocky Linux 9 계열 Post-install 설정.
 *
 * <p>호환 버전: {@code 9.0} - {@code 9.7}. RHELBasedSetting 의 Kickstart {@code %post} 생성 로직을 그대로
 * 사용한다.</p>
 */
public class RockyLinux9Setting extends RHELBasedSetting {

    private static final List<String> COMPATIBLE_VERSIONS =
            List.of("9.0", "9.1", "9.2", "9.3", "9.4", "9.5", "9.6", "9.7");

    @Builder
    @JsonCreator
    protected RockyLinux9Setting(
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("services")           List<ServiceDirective> services,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        super(OSName.ROCKY_LINUX, COMPATIBLE_VERSIONS, selinuxMode, services, additionalPackages);
    }
}
