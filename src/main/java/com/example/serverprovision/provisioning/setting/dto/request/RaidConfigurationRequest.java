package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;

import java.util.List;

/**
 * RAID 구성 단계 요청 ({@code "type": "RAID_CONFIGURATION"}, U4-1-1 v2) — 1단 flat 타입.
 *
 * <p>정의서가 적는 것은 <b>규칙과 전제</b>다: 어떤 디스크를 어떤 RAID 로 묶는가(디스크 묶음 규칙 목록)와
 * 어떤 RAID 카드를 전제하는가. 규칙을 실제로 거는 것(집행)은 실행(E)의 몫이며, 그 phase 는
 * {@code ProvisioningPhase.RAID_CONFIGURATION}(BIOS 설정 다음 · OS 설치 전)이다. OS 설치 단계와 결합 규칙은
 * 없다 — RAID 구성만 있는 정의서 · OS 설치만 있는 정의서 모두 유효하다(v2 D14).</p>
 */
@Getter
public class RaidConfigurationRequest extends AbstractProcessRequest {

    /**
     * 이 정의서가 전제하는 RAID 카드(management RAID 카드 자원 id) — <b>소프트참조</b>(payload 가 JSON 이라 FK 불가,
     * MA7 확정). {@code null} = 카드를 전제하지 않음. RAID 를 구성하는 묶음이 하나라도 있으면 필수(E6 —
     * {@link #isRaidCardPresentWhenRequired()}). 자동 탐지(AUTO)는 실기 표본이 생길 때까지 계약에 두지 않는다(E33) —
     * 그때 {@code raidCardMode} 필드를 null-coalesce(SPECIFIED)로 덧붙이면 저장 payload 마이그레이션이 없다(D3).
     */
    private final Long raidCardId;

    /** 디스크 묶음 규칙 목록. 비어 있으면 디스크 구성을 정하지 않는 것 — 설치기 자동 선택(구 동작). */
    @Valid
    private final List<DiskGroupRuleRequest> diskGroups;

    @JsonCreator
    public RaidConfigurationRequest(
            @JsonProperty("raidCardId") Long raidCardId,
            @JsonProperty("diskGroups") List<DiskGroupRuleRequest> diskGroups
    ) {
        this.raidCardId = raidCardId;
        this.diskGroups = diskGroups != null ? List.copyOf(diskGroups) : List.of();
    }

    @Override
    public SettingProcessType processType() {
        return SettingProcessType.RAID_CONFIGURATION;
    }

    /** RAID 를 구성하는 묶음이 하나라도 있는가 — 카드 요구(E6)의 SSOT. 폼의 카드 select 잠금과 서버 가드가 함께 쓴다. */
    @JsonIgnore
    public boolean requiresRaidCard() {
        return diskGroups.stream().anyMatch(DiskGroupRuleRequest::buildsRaid);
    }

    /**
     * RAID 묶음이 있으면 카드가 있어야 한다 — 한 방향만 강제한다(D4). 카드만 고르고 RAID 묶음이 없는 상태는
     * "이 카드를 전제한다" 는 서술일 뿐 해롭지 않아 허용한다. 오류 경로는 {@code processList[i].raidCardPresentWhenRequired}
     * 로 오며 폼의 카드 그룹이 그 이름으로 exact-match 한다. 판정 메서드라 payload 에 싣지 않는다.
     */
    @JsonIgnore
    @AssertTrue(message = "RAID 를 구성하는 묶음이 있으므로 RAID 카드를 지정해야 합니다.")
    public boolean isRaidCardPresentWhenRequired() {
        return !requiresRaidCard() || raidCardId != null;
    }
}
