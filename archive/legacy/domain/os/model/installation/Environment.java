package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.dto.OSEnvironmentDTO;
import com.example.serverprovision.domain.os.dto.OSPackageGroupDTO;
import com.example.serverprovision.global.exception.DomainValidationException;
import com.example.serverprovision.global.exception.DomainValidationException.Reason;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
public class Environment implements InstallScriptable {

    @NotNull(message = "OS 환경 정보는 필수 값입니다.")
    private final OSEnvironmentDTO osEnvironment;
    private final List<OSPackageGroupDTO> packageGroups;

    @Builder
    @JsonCreator
    Environment(
            @JsonProperty("osEnvironment")  OSEnvironmentDTO osEnvironment,
            @JsonProperty("packageGroups")  List<OSPackageGroupDTO> packageGroups
    ) {
        // 도메인 규칙: 패키지 그룹은 반드시 선택된 OS 환경에 속해야 한다.
        // 이 검증은 이중 방어 — resolver 에서 이미 서비스 계층 교차 검증을 수행하므로
        // 정상 흐름에선 발동되지 않는다. 발동된다면 resolver 의 사전 검증과 도메인이
        // 불일치한다는 뜻이며 코드 버그에 해당한다. 그래도 도메인 순수성을 위해
        // DomainValidationException 으로 통일된 처리를 거친다.
        if (packageGroups != null && !packageGroups.isEmpty()) {
            if (
                    !packageGroups.stream().allMatch(group -> group.osEnvironment().id().equals(osEnvironment.id()))
            ) {
                throw new DomainValidationException(Reason.PACKAGE_GROUP_ENVIRONMENT_MISMATCH,
                        "패키지 그룹의 OS 환경 정보가 OS 환경 정보와 일치하지 않습니다.");
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
