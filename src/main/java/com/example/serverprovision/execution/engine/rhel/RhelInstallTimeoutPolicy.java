package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.osinstall.OsInstallTimeoutPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * RHEL 설치의 시한 값({@code provision.rhel-install.*}). 실측 전이라 Windows 와 같은 기본값(60분 · 5회 · 유예 30분)으로
 * 시작하고, 실기에서 설치 시간을 잰 뒤 설정으로 맞춘다(E4-1-b-4 D-11).
 */
@Component
public class RhelInstallTimeoutPolicy extends OsInstallTimeoutPolicy {

    public RhelInstallTimeoutPolicy(
            @Value("${provision.rhel-install.install-timeout:60m}") Duration installTimeout,
            @Value("${provision.rhel-install.max-reentries:5}") int maxReentries,
            @Value("${provision.rhel-install.sweep-grace:30m}") Duration sweepGrace) {
        super(installTimeout, maxReentries, sweepGrace);
    }
}
