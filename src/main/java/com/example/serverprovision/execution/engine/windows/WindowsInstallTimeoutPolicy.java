package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.osinstall.OsInstallTimeoutPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Windows 설치의 시한 값({@code provision.windows-install.*}). 기본값 60분 · 5회는 실측 3호(첫 로그온까지 11.5분 · 정상
 * 재부팅 2~3회)에 여유를 둔 값이다(E4-1-a-3 CP1 결정). 스윕 유예 30분은 E4-1-a-4 에서 정했다.
 */
@Component
public class WindowsInstallTimeoutPolicy extends OsInstallTimeoutPolicy {

    public WindowsInstallTimeoutPolicy(
            @Value("${provision.windows-install.install-timeout:60m}") Duration installTimeout,
            @Value("${provision.windows-install.max-reentries:5}") int maxReentries,
            @Value("${provision.windows-install.sweep-grace:30m}") Duration sweepGrace) {
        super(installTimeout, maxReentries, sweepGrace);
    }
}
