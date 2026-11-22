package com.example.serverprovision.execution.engine.rhel.ossetting;

import java.util.List;
import java.util.Objects;

/**
 * 할당 스냅숏의 OS 후처리 단계를 kickstart 재료로 푼 값(E4-1-b-7). kickstart 생성과 첫 부팅 판정이 같은 값을 읽어,
 * 적용한 것과 확인하는 것이 어긋나지 않는다.
 */
public record RhelOsSettingPlan(SelinuxMode selinux, List<ServiceDirective> services, List<String> packages,
                                List<UserScript> scripts) {

    public RhelOsSettingPlan {
        Objects.requireNonNull(selinux, "selinux");
        services = List.copyOf(services);
        packages = List.copyOf(packages);
        scripts = List.copyOf(scripts);
    }

    public List<String> serviceNames(ServiceIntent intent) {
        return services.stream().filter(service -> service.intent() == intent).map(ServiceDirective::name).toList();
    }
}
