package com.example.serverprovision.execution.engine.phase;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 게스트 한 대의 프로비저닝이 종단됐다(HF20) — {@link PhaseCursorAdvancer} 가 종단을 결정한 트랜잭션 안에서 발행하고,
 * 커밋 뒤 듣는 쪽(부트 순서 정착 등)이 BMC 를 만진다. 종단 규칙 SSOT 에 BMC 부작용을 두지 않기 위한 분리.
 */
public record ProvisioningCompletedEvent(UUID guestServerId, LocalDateTime completedAt) {
}
