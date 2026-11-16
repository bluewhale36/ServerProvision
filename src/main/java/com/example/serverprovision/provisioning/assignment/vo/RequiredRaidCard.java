package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.execution.engine.raid.RaidConfigurationTarget;

/**
 * 정의서의 RAID 구성 단계가 지정한 카드(R20) — 할당 판정과 집행이 같은 카드를 보게 하는 값.
 *
 * <p>집행 쪽 {@link RaidConfigurationTarget} 은 카드가 영구 삭제되면 모델명 자리에 "(사라진 카드 #id)" 를 넣는다.
 * 할당 화면은 "카드가 없어 대조할 수 없다" 와 "카드에 Subsystem 이 없어 대조할 수 없다" 를 다른 문장으로 알려야 해서
 * 그 구분({@code registered})을 따로 든다.</p>
 *
 * @param raidCardId     지정 카드 id — RAID 구성 단계가 카드를 지정하지 않았으면 {@code null}
 * @param pciSubsystemId 카드 자원의 PCI Subsystem(표시형) — 미등록이거나 카드가 없으면 {@code null}
 * @param modelName      카드 모델명 — 카드가 없으면 {@code null}
 * @param registered     카드 자원이 남아 있는가(soft-delete 포함). 영구 삭제됐으면 false
 */
public record RequiredRaidCard(Long raidCardId, String pciSubsystemId, String modelName, boolean registered) {

    /** RAID 구성 단계는 있으나 카드를 지정하지 않음. */
    public static RequiredRaidCard unspecified() {
        return new RequiredRaidCard(null, null, null, false);
    }

    public boolean specified() {
        return raidCardId != null;
    }

    /** 집행 · 개시 전 예고가 쓰는 형태로 — 판정({@code RaidCardMatch.judge})의 입력. */
    public RaidConfigurationTarget toTarget() {
        if (!specified()) {
            return new RaidConfigurationTarget(null, null, null);
        }
        return new RaidConfigurationTarget(raidCardId, pciSubsystemId, displayName());
    }

    /** 화면 · 원장에 쓰는 카드 이름. */
    public String displayName() {
        return registered ? modelName : "(사라진 카드 #" + raidCardId + ")";
    }
}
