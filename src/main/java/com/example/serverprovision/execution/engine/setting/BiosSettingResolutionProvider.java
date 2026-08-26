package com.example.serverprovision.execution.engine.setting;

import java.util.Optional;
import java.util.UUID;

/**
 * BIOS 설정 목표 공급 SPI(E3-1 D-3) — 구현은 provisioning 측(할당 스냅샷을 아는 곳)이 한다.
 * {@code FirmwareResolutionProvider} 와 같은 역전 구조. empty = 활성 할당이 없거나 정의서에 BASIC_SETTING 이
 * 없다(창 밖), 비어 있는 목표 = 감지 보드와 일치하는 템플릿이 없다(NO_TARGET).
 */
public interface BiosSettingResolutionProvider {

    Optional<BiosSettingTarget> resolveFor(UUID guestServerId);
}
