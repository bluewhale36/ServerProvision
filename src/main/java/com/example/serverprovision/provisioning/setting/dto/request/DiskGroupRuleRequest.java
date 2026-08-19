package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 디스크 묶음 규칙 한 줄 — {@code {RAID 레벨, 디스크 종류, 전송 방식, 용량, 개수}} (U4-1-1, U4-1 토론 E1 · E22).
 *
 * <p><b>묶음은 볼륨 하나의 명세가 아니라 규칙이다</b>(E18 · E19) — 조건에 맞는 조합 전부에 적용되어 볼륨이
 * 여러 개 생길 수 있다. 그 매칭은 실행(E)의 몫이고 정의서는 규칙만 적는다.</p>
 *
 * <p>{@code raidLevel} 이 {@code null} 이면 <b>RAID 없음</b>(E5) — 그 묶음을 RAID 로 묶지 않고 디스크를 그대로
 * 쓴다. 이 축에는 '없음' 외의 다른 의도가 없어 {@code BoardModelSelectionRequest} 같은 mode 래퍼를 두지
 * 않았고, {@link RaidLevel} 에 NONE 상수를 더하지도 않았다 — 그 enum 은 "카드가 만들 수 있는 것" 의 집합이라
 * '안 만든다' 를 상수로 두면 카드 등록 화면의 지원 레벨 선택지에도 나타난다(U4-1-1 D2).</p>
 */
public record DiskGroupRuleRequest(

        RaidLevel raidLevel,

        @NotNull(message = "디스크 종류는 필수 값입니다.")
        DiskTypeRequirement diskType,

        @NotNull(message = "전송 방식은 필수 값입니다.")
        DiskTransportRequirement transport,

        @NotNull(message = "용량 조건은 필수 값입니다.")
        @Valid
        DiskCapacityRequirement capacity,

        @NotNull(message = "개수 조건은 필수 값입니다.")
        @Valid
        DiskCountRequirement count
) {

    @JsonCreator
    public DiskGroupRuleRequest(
            @JsonProperty("raidLevel") RaidLevel raidLevel,
            @JsonProperty("diskType")  DiskTypeRequirement diskType,
            @JsonProperty("transport") DiskTransportRequirement transport,
            @JsonProperty("capacity")  DiskCapacityRequirement capacity,
            @JsonProperty("count")     DiskCountRequirement count
    ) {
        this.raidLevel = raidLevel;
        this.diskType  = diskType;
        this.transport = transport;
        this.capacity  = capacity;
        this.count     = count;
    }

    /** 이 묶음이 RAID 를 구성하는가 — RAID 카드 요구(E6)와 레벨 판정의 출발점. */
    @JsonIgnore
    public boolean buildsRaid() {
        return raidLevel != null;
    }
}
