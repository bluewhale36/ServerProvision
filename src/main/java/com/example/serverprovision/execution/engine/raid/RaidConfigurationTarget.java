package com.example.serverprovision.execution.engine.raid;

/**
 * 활성 할당이 전제하는 RAID 카드(E3.5-1) — 대조에 필요한 최소만 나른다. 규칙 목록의 공급은
 * 계획 산출(E3.5-2)이 첫 소비자라 그때 넓힌다(미리 분리 금지).
 *
 * @param pciSubsystemId 카드 자원의 Subsystem 쌍({@code PciSubsystemId.toDisplay()} 형식) —
 *                       소프트참조라 카드 자원이 사라졌거나 미등록이면 null(대조 생략 · WARN)
 */
public record RaidConfigurationTarget(
        Long raidCardId,
        String pciSubsystemId,
        String cardModelName
) {
}
