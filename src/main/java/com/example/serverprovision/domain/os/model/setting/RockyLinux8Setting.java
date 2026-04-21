package com.example.serverprovision.domain.os.model.setting;

import com.example.serverprovision.domain.os.model.enums.OSName;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

/**
 * Rocky Linux 8 계열 Post-install 설정.
 *
 * <p>호환 버전: {@code 8.10} (현재 지원 범위). 추후 추가 마이너가 열리면 여기만 업데이트한다.</p>
 */
public class RockyLinux8Setting extends RHELBasedSetting {

    private static final List<String> COMPATIBLE_VERSIONS = List.of("8.10");

    @Builder
    @JsonCreator
    protected RockyLinux8Setting(
            @JsonProperty("selinuxMode")        String selinuxMode,
            @JsonProperty("services")           List<ServiceDirective> services,
            @JsonProperty("additionalPackages") List<String> additionalPackages
    ) {
        super(OSName.ROCKY_LINUX, COMPATIBLE_VERSIONS, selinuxMode, services, additionalPackages);
    }
}
