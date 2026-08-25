package com.example.serverprovision.execution.event;

import java.util.UUID;

/**
 * 진단 수집이 BMC 접점(IP)을 적재했다 — 계정 표준화(E1.6)의 방아쇠. 커밋 확정 후(AFTER_COMMIT)에만
 * 소비된다 — 롤백된 수집으로 BMC 를 만지지 않기 위해서다.
 */
public record BmcEndpointDiscoveredEvent(UUID serverId) {
}
