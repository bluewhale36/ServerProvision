package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.rhel.ossetting.RhelOsSettingPlan;

import java.util.Optional;
import java.util.UUID;

/**
 * RHEL 설치 목표 공급 SPI(E4-1-b-3) — 구현은 provisioning 측(할당 스냅샷을 아는 곳)이 한다.
 * {@code WindowsInstallationResolutionProvider} 와 같은 역전 구조. empty = 활성 할당이 없거나 OS 설치 단계가 없거나
 * RHEL 계열 정의서가 아니다(이 계열이 판정할 것이 없다).
 */
public interface RhelInstallationResolutionProvider {

    Optional<RhelResolution> resolveFor(UUID guestServerId);

    /** 활성 할당의 OS 후처리 목표만 읽는다. 첫 부팅 판정이 설치 재료(카탈로그 · 비밀번호)를 다시 풀지 않게 한다(E4-1-b-7). */
    Optional<RhelOsSettingPlan> osSettingFor(UUID guestServerId);
}
