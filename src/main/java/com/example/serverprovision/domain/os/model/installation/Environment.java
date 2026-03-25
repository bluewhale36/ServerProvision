package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.dto.OSEnvironmentDTO;
import com.example.serverprovision.domain.os.dto.OSPackageGroupDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;

public class Environment implements InstallScriptable {

    @NotNull(message = "OS 환경 정보는 필수 값입니다.")
    private final OSEnvironmentDTO osEnvironment;
    private final List<OSPackageGroupDTO> packageGroups;

    @Builder
    private Environment(
            OSEnvironmentDTO osEnvironment,
            List<OSPackageGroupDTO> packageGroups
    ) {
        if (packageGroups != null && !packageGroups.isEmpty()) {
            if (
                    !packageGroups.stream().allMatch(group -> group.osEnvironment().id().equals(osEnvironment.id()))
            ) {
                throw new IllegalArgumentException("패키지 그룹의 OS 환경 정보가 OS 환경 정보와 일치하지 않습니다.");
            }
        }

        this.osEnvironment = osEnvironment;
        this.packageGroups = packageGroups == null ? List.of() : packageGroups;
    }

    @Override
    public String getRHELScript() {
        StringBuilder scriptBuilder = new StringBuilder();

        // 최상위 설치 환경
        scriptBuilder.append(osEnvironment.getInstallationScript()).append("\n");

        // add-on package group
        for (OSPackageGroupDTO group : packageGroups) {
            scriptBuilder.append(group.getInstallationScript()).append("\n");
        }

        return scriptBuilder.toString();
    }
}
