package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;

/**
 * 게스트에서 감지한 RAID 카드(E3.5-1) — 에이전트가 보고한 lspci · CLI 원문에서 서버 파서가 세운다.
 *
 * @param pciSubsystemId 완제품 카드 식별자, 소문자 4자리 16진수 쌍(예: {@code 1458:3008}) —
 *                       MA7 {@code RaidCard.pciSubsystemId} 와의 대조 키({@code PciSubsystemId.toDisplay()} 형식)
 */
public record DetectedRaidCard(
        RaidChipFamily chipFamily,
        String pciSubsystemId,
        String model,
        String firmware
) {
}
