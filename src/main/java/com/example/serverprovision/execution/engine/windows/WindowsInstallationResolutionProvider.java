package com.example.serverprovision.execution.engine.windows;

import java.util.Optional;
import java.util.UUID;

/**
 * Windows 설치 목표 공급 SPI(E4-1-a-3) — 구현은 provisioning 측(할당 스냅샷을 아는 곳)이 한다.
 * {@code RaidConfigurationResolutionProvider} 와 같은 역전 구조. empty = 활성 할당이 없거나 정의서에
 * OS 설치 단계가 없다(창 밖 — 이 실행기가 판정할 것이 없다).
 */
public interface WindowsInstallationResolutionProvider {

    Optional<WindowsInstallTarget> resolveFor(UUID guestServerId);
}
