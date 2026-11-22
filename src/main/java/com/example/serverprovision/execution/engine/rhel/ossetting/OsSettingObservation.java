package com.example.serverprovision.execution.engine.rhel.ossetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 첫 부팅에서 읽은 OS 후처리 상태(E4-1-b-7). 목록에 없는 서비스 · 패키지는 관측하지 못한 것으로 본다.
 *
 * @param selinux 관측 원문. 화면에 그대로 보인다
 */
public record OsSettingObservation(String selinux, Map<String, ServiceEnablement> serviceStates,
                                   Map<String, Boolean> packagesInstalled, List<Integer> scriptExitCodes) {

    public OsSettingObservation {
        serviceStates = Map.copyOf(serviceStates);
        packagesInstalled = Map.copyOf(packagesInstalled);
        scriptExitCodes = Collections.unmodifiableList(new ArrayList<>(scriptExitCodes));
    }

    public ServiceEnablement serviceState(String name) {
        return serviceStates.getOrDefault(name, ServiceEnablement.UNKNOWN);
    }

    public boolean isInstalled(String name) {
        return packagesInstalled.getOrDefault(name, false);
    }

    /** 순서 번호(1 부터)의 종료 코드. 기록이 없으면 비어 있다. */
    public Optional<Integer> exitCodeOf(int no) {
        return no <= scriptExitCodes.size() ? Optional.ofNullable(scriptExitCodes.get(no - 1)) : Optional.empty();
    }
}
