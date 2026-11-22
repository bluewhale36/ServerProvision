package com.example.serverprovision.execution.dto.request;

import com.example.serverprovision.execution.engine.rhel.ossetting.OsSettingObservation;
import com.example.serverprovision.execution.engine.rhel.ossetting.ServiceEnablement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 첫 부팅 보고에 실린 OS 후처리 관측값(E4-1-b-7). 셸은 판정하지 않고 읽은 값만 보낸다.
 *
 * @param selinux         {@code getenforce} 결과(소문자)
 * @param scriptExitCodes 스크립트 순서대로의 종료 코드. 종료 코드 파일이 없으면 null
 */
public record OsSettingObservationRequest(
        @Size(max = 32, message = "selinux 는 32자 이내여야 합니다.")
        String selinux,

        @Size(max = 200, message = "services 는 200개 이내여야 합니다.")
        List<@Valid ServiceState> services,

        @Size(max = 500, message = "packages 는 500개 이내여야 합니다.")
        List<@Valid PackageState> packages,

        @Size(max = 10, message = "scriptExitCodes 는 10개 이내여야 합니다.")
        List<Integer> scriptExitCodes
) {

    public record ServiceState(@Size(max = 256) String name, @Size(max = 64) String state) {
    }

    public record PackageState(@Size(max = 256) String name, boolean installed) {
    }

    public OsSettingObservation toObservation() {
        Map<String, ServiceEnablement> states = new LinkedHashMap<>();
        (services == null ? List.<ServiceState>of() : services)
                .forEach(service -> states.putIfAbsent(service.name(), ServiceEnablement.of(service.state())));
        Map<String, Boolean> installed = new LinkedHashMap<>();
        (packages == null ? List.<PackageState>of() : packages)
                .forEach(pkg -> installed.putIfAbsent(pkg.name(), pkg.installed()));
        return new OsSettingObservation(selinux == null ? "" : selinux, states, installed,
                scriptExitCodes == null ? List.of() : scriptExitCodes);
    }
}
