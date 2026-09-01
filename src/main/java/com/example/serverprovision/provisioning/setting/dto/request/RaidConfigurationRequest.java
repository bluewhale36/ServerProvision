package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
import com.example.serverprovision.provisioning.setting.enums.ExistingRaidConfigPolicy;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 볼륨 우선순위 행 목록(U4-1-2) — 행 순서가 곧 우선순위. 정의서가 값으로 갖는다(E26): 폼이 처음 열릴 때
     * {@link VolumePriorityRuleRequest#defaults()} 를 채우고 사용자가 고친다. 서버는 빈 값을 기본값으로 바꾸지
     * 않는다 — direct POST 에서 사용자가 모르는 값이 저장되면 안 되고, "비움" 의 뜻이 축마다 달라지면 안 된다(D5).
     * 빈 목록은 "우선순위 없음 = 열거 순서" 라는 명시적 값이며, OS 설치 단계가 있을 때의 제약은
     * {@code SettingSaveRequest.isOsVolumeDeterminable} 이 정의서 수준에서 건다.
     */
    @NotNull(message = "볼륨 우선순위는 필수 값입니다.")
    @Valid
    private final List<VolumePriorityRuleRequest> volumePriorities;

    /**
     * 기존 구성 처리 축(E3.5-4, 결정 D-7) — 기본값 없는 필수 선택. {@code null} 은 이 축이 없던 구 저장본의
     * 관용 값이다(volumePriorities 의 "구 저장본" 관례와 동일): 화면은 "미지정" 으로 표기하고 실행은 종전과
     * 같은 보류를 유지하며, 편집 저장 시에는 {@link #isExistingPolicyPresentWhenRequired} 가 선택을 요구한다.
     */
    private final ExistingRaidConfigPolicy existingConfigPolicy;

    @JsonCreator
    public RaidConfigurationRequest(
            @JsonProperty("raidCardId")           Long raidCardId,
            @JsonProperty("diskGroups")           List<DiskGroupRuleRequest> diskGroups,
            @JsonProperty("volumePriorities")     List<VolumePriorityRuleRequest> volumePriorities,
            @JsonProperty("existingConfigPolicy") ExistingRaidConfigPolicy existingConfigPolicy
    ) {
        this.raidCardId           = raidCardId;
        this.diskGroups           = diskGroups != null ? List.copyOf(diskGroups) : List.of();
        this.volumePriorities     = volumePriorities != null ? List.copyOf(volumePriorities) : null;
        this.existingConfigPolicy = existingConfigPolicy;
    }

    /** 축이 없던 구 형태(E3.5-4 이전 호출부 호환) — 구 저장본과 같은 null 관용. */
    public RaidConfigurationRequest(Long raidCardId, List<DiskGroupRuleRequest> diskGroups,
                                    List<VolumePriorityRuleRequest> volumePriorities) {
        this(raidCardId, diskGroups, volumePriorities, null);
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

    /**
     * RAID 묶음이 있으면 기존 구성 처리를 골라야 한다(E3.5-4 · D-7 필수 선택) — 판정 재료는 카드 요구
     * ({@link #requiresRaidCard})와 같다. 폼의 라디오 잠금과 서버 가드가 이 판정을 공유한다(UI 1차 차단).
     * 오류 경로는 {@code processList[i].existingPolicyPresentWhenRequired}.
     */
    @JsonIgnore
    @AssertTrue(message = "RAID 를 구성하는 묶음이 있으므로 기존 구성 처리를 선택해야 합니다.")
    public boolean isExistingPolicyPresentWhenRequired() {
        return !requiresRaidCard() || existingConfigPolicy != null;
    }

    /**
     * 우선순위 행의 (종류, 전송) 조합은 한 번씩만 — 같은 조합이 둘이면 뒤 행은 절대 매칭되지 않아 뜻이 없다.
     * 오류 경로는 {@code processList[i].volumePriorityDistinct}. 폼은 두 번째 행을 강조해 먼저 막는다.
     */
    @JsonIgnore
    @AssertTrue(message = "우선순위 행의 종류 · 전송 조합이 중복됩니다.")
    public boolean isVolumePriorityDistinct() {
        if (volumePriorities == null) return true;  // @NotNull 위반이 이미 보고된다.
        Set<String> seen = new HashSet<>();
        for (VolumePriorityRuleRequest row : volumePriorities) {
            if (row == null) continue;
            if (!seen.add(row.diskType() + "|" + row.transport())) return false;
        }
        return true;
    }

    /** OS 영역으로 고정한 규칙이 있는가 — 정의서 수준 판정({@code SettingSaveRequest.isOsVolumeDeterminable})의 재료. */
    @JsonIgnore
    public boolean hasOsFixedRule() {
        return diskGroups.stream().anyMatch(DiskGroupRuleRequest::isOsFixed);
    }

    /** 우선순위에 맡긴 규칙이 있는가 — 위 판정의 두 번째 재료. */
    @JsonIgnore
    public boolean hasByPriorityRule() {
        return diskGroups.stream().anyMatch(r -> r.role() == DiskGroupRole.BY_PRIORITY);
    }

    /**
     * 실행 시 어느 볼륨이 OS 인지 정의서만으로 정해지는가(U4-1-2 OQ2 · OQ3 확정 2026-08-19) —
     * OS 고정 규칙이 있거나, 우선순위에 맡긴 규칙이 있고 우선순위 행이 하나 이상이다. 묶음 규칙이 없으면 볼륨
     * 자체가 없어 판정 대상이 아니다(설치기 자동 — U4-1-1 그대로).
     */
    @JsonIgnore
    public boolean isOsVolumeDeterminable() {
        if (diskGroups.isEmpty()) return true;
        if (hasOsFixedRule()) return true;
        return hasByPriorityRule() && volumePriorities != null && !volumePriorities.isEmpty();
    }

    /**
     * OS 영역이 될 수 있는 규칙 — OS 고정 규칙이 있으면 그것만, 없으면 '우선순위에 따름' 규칙 전부(해석 계약 ②).
     * {@code OsVolumeTargets} 와 용량 하한이 같은 집합을 본다.
     */
    @JsonIgnore
    public List<DiskGroupRuleRequest> osCandidateRules() {
        List<DiskGroupRuleRequest> fixed = diskGroups.stream().filter(DiskGroupRuleRequest::isOsFixed).toList();
        if (!fixed.isEmpty()) return fixed;
        return diskGroups.stream().filter(r -> r.role() == DiskGroupRole.BY_PRIORITY).toList();
    }

    /** OS 고정 규칙의 1-based 번호, 없으면 0. */
    @JsonIgnore
    public int osFixedRuleNo() {
        for (int i = 0; i < diskGroups.size(); i++) {
            if (diskGroups.get(i).isOsFixed()) return i + 1;
        }
        return 0;
    }

    /**
     * OS 영역 볼륨의 용량 하한(바이트) — 후보 규칙 전부가 용량을 직접 지정했을 때만 알 수 있고(최솟값), 하나라도 자동 탐지면
     * empty(실행 시 E 가 확인). 후보가 없어도 empty. (U4-1-3 D7)
     */
    @JsonIgnore
    public java.util.OptionalLong osVolumeCapacityLowerBoundBytes() {
        List<DiskGroupRuleRequest> candidates = osCandidateRules();
        if (candidates.isEmpty()) return java.util.OptionalLong.empty();
        long min = Long.MAX_VALUE;
        for (DiskGroupRuleRequest rule : candidates) {
            java.util.OptionalLong bound = rule.usableCapacityLowerBoundBytes();
            if (bound.isEmpty()) return java.util.OptionalLong.empty();
            min = Math.min(min, bound.getAsLong());
        }
        return java.util.OptionalLong.of(min);
    }
}
