package com.example.serverprovision.application.setting.model;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class OSInstallation extends AbstractSettingProcess {

    @NotNull(message = "OS 메타데이터는 필수 값입니다.")
    private final OSMetadataDTO osMetadata;
    @NotNull(message = "OS 설치 정보는 필수 값입니다.")
    private final com.example.serverprovision.domain.os.model.installation.OSInstallation osInstallation;

    public OSInstallation(
            OSMetadataDTO osMetadata,
            com.example.serverprovision.domain.os.model.installation.OSInstallation osInstallation
    ) {
        super(SettingProcessStep.OS_INSTALLATION);

        if (!osInstallation.isCompatible(osMetadata.osName(), osMetadata.osVersion())) {
            throw new IllegalArgumentException("OS 메타데이터와 OS 설치 정보가 호환되지 않습니다.");
        }

        this.osMetadata = osMetadata;
        this.osInstallation = osInstallation;
    }
}
