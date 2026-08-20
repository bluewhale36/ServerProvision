package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.CapacityOrder;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 볼륨 우선순위 행 — {@code {종류, 전송, 용량 순서}} (U4-1-2, U4-1 토론 E23~E27).
 *
 * <p>행의 <b>순서가 곧 우선순위</b>다. 실행 시 볼륨(RAID 볼륨 · 단독 디스크)은 종류 · 전송이 같은 첫 행의 순번을
 * 받고, 같은 행 안에서는 그 행의 용량 순서로, 어느 행에도 맞지 않으면 맨 뒤(열거 순서)에 선다 —
 * {@code VolumePriorityRules.rankOf}. 정의서는 이 값을 갖고, 볼륨을 세우는 일은 실행(E)의 몫이다.</p>
 *
 * <p>종류 · 전송은 묶음 규칙과 같은 enum 을 쓰되 {@code AUTO} 는 거절한다 — 우선순위 행은 "어느 볼륨이 먼저인가"
 * 를 적는 자리라 자동 탐지가 뜻이 없고, 새 enum 두 벌을 두면 SSD/HDD · SATA/SAS/NVMe 표기가 갈라진다.</p>
 */
public record VolumePriorityRuleRequest(

        @NotNull(message = "디스크 종류는 필수 값입니다.")
        DiskTypeRequirement diskType,

        @NotNull(message = "전송 방식은 필수 값입니다.")
        DiskTransportRequirement transport,

        @NotNull(message = "용량 순서는 필수 값입니다.")
        CapacityOrder capacityOrder
) {

    @JsonCreator
    public VolumePriorityRuleRequest(
            @JsonProperty("diskType")      DiskTypeRequirement diskType,
            @JsonProperty("transport")     DiskTransportRequirement transport,
            @JsonProperty("capacityOrder") CapacityOrder capacityOrder
    ) {
        this.diskType      = diskType;
        this.transport     = transport;
        this.capacityOrder = capacityOrder;
    }

    /**
     * 정의서를 처음 만들 때 채우는 기본 행 — 기본값의 SSOT(폼 채움 · 되돌리기 버튼 · 테스트가 이것만 본다).
     * 순서는 U4-1 토론 E24(NVMe SSD → SATA SSD → HDD, 작은 용량부터)에 SAS 를 끼운 것(U4-1-2 OQ1 확정).
     * 종류 2 × 전송 3 에서 HDD × NVMe 를 뺀 유효 조합 5 개 전부다.
     */
    public static List<VolumePriorityRuleRequest> defaults() {
        return List.of(
                new VolumePriorityRuleRequest(DiskTypeRequirement.SSD, DiskTransportRequirement.NVME, CapacityOrder.SMALLER_FIRST),
                new VolumePriorityRuleRequest(DiskTypeRequirement.SSD, DiskTransportRequirement.SAS,  CapacityOrder.SMALLER_FIRST),
                new VolumePriorityRuleRequest(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CapacityOrder.SMALLER_FIRST),
                new VolumePriorityRuleRequest(DiskTypeRequirement.HDD, DiskTransportRequirement.SAS,  CapacityOrder.SMALLER_FIRST),
                new VolumePriorityRuleRequest(DiskTypeRequirement.HDD, DiskTransportRequirement.SATA, CapacityOrder.SMALLER_FIRST));
    }

    /** 종류 · 전송이 구체값인가 — 우선순위 행에는 자동 탐지가 없다. 판정 메서드는 payload 에 싣지 않는다. */
    @JsonIgnore
    @AssertTrue(message = "우선순위 행에는 자동 탐지를 쓸 수 없습니다.")
    public boolean isConcrete() {
        return diskType != DiskTypeRequirement.AUTO && transport != DiskTransportRequirement.AUTO;
    }

    /** 종류 ↔ 전송 정합 — HDD 에는 NVMe 전송이 없다(묶음 규칙 6 번과 같은 사실). */
    @JsonIgnore
    @AssertTrue(message = "HDD 에는 NVMe 전송 방식이 없습니다.")
    public boolean isTransportCompatible() {
        return !(diskType == DiskTypeRequirement.HDD && transport == DiskTransportRequirement.NVME);
    }

    /** 같은 (종류, 전송) 인가 — 중복 행 판정과 실행 시 볼륨 매칭이 함께 쓴다. */
    @JsonIgnore
    public boolean matches(DiskTypeRequirement type, DiskTransportRequirement transportOfVolume) {
        return diskType == type && transport == transportOfVolume;
    }

    /** 화면 표기 — {@code SSD · NVMe · 작은 용량부터}. null 축은 이미 400 이라 표기에서는 그대로 비운다. */
    public String toDisplay() {
        return (diskType == null ? "?" : diskType.getDisplayName())
                + " · " + (transport == null ? "?" : transport.getDisplayName())
                + " · " + (capacityOrder == null ? "?" : capacityOrder.getDisplayName());
    }
}
