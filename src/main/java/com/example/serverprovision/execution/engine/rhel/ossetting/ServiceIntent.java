package com.example.serverprovision.execution.engine.rhel.ossetting;

import java.util.EnumSet;
import java.util.Set;

/** 정의서의 서비스 지시와 그 지시가 이뤄졌다고 볼 관측 상태. masked 는 disabled 보다 강한 비활성이라 비활성 지시를 채운다. */
public enum ServiceIntent {
    ENABLE(EnumSet.of(ServiceEnablement.ENABLED)),
    DISABLE(EnumSet.of(ServiceEnablement.DISABLED, ServiceEnablement.MASKED));

    private final Set<ServiceEnablement> satisfying;

    ServiceIntent(Set<ServiceEnablement> satisfying) {
        this.satisfying = satisfying;
    }

    public boolean isSatisfiedBy(ServiceEnablement observed) {
        return satisfying.contains(observed);
    }
}
