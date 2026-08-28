package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.global.redfish.RedfishTarget;

import java.util.Map;
import java.util.UUID;

/**
 * BIOS 레지스트리 채집 · 대조 SPI(E3-3) — 구현은 provisioning 측(레지스트리 파서와 스냅샷 저장소를 아는 곳)이 한다.
 * {@link BiosSettingResolutionProvider} 와 같은 역전 구조: execution 이 선언하고 provisioning 이 채운다.
 *
 * <p>둘 다 <b>집행을 막지 않는다</b> — 채집 · 대조가 어떤 이유로든 불가하면 {@link RegistryCheck#unavailable()} 로
 * 답하고 예외를 올리지 않는다(CP1 Q2: 채집 불가는 PATCH 를 막을 사유가 아니다 — BMC 의 400 이 안전망이다).</p>
 */
public interface BiosRegistryCapturePort {

    /** 실제 BIOS 버전의 레지스트리를 (없으면) 적립하고, 목표값을 그 허용값과 대조한다. */
    RegistryCheck captureAndCheck(UUID guestServerId, RedfishTarget target, Map<String, Object> attributes);

    /** 대조 없이 적립만 — 굽기 반영 확인 직후처럼 "이 버전의 레지스트리가 지금 BMC 에 있다" 는 순간에 부른다. */
    void captureIfAbsent(UUID guestServerId, RedfishTarget target);
}
