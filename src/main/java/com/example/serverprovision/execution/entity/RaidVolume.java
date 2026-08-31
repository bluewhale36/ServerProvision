package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 검증을 통과한 RAID 구성의 현재 실물 기록(E3.5-3 결정 D-8) — E4 OS 설치가 "이 서버의 OS 영역 볼륨" 을
 * 묻는 조회 경로다. 원장(provisioning_history)이 사건의 append-only 라면 이 표는 "지금 카드에 있는 것" —
 * 재집행 시 게스트 단위로 전부 지우고 다시 쓴다(replace).
 */
@Entity
@Table(name = "raid_volume")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RaidVolume extends BaseTimeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_server_id")
    private GuestServer guestServer;

    @Column(nullable = false, length = 32)
    private String name;

    /** RAID 없음(단독 디스크 보장)은 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "raid_level")
    private RaidLevel raidLevel;

    /** 멤버 슬롯의 JSON 배열 — 슬롯 표기는 계열 원문 그대로(E3.5-1 원칙). */
    @Column(name = "member_slots", columnDefinition = "longtext")
    private String memberSlotsJson;

    @Column(name = "usable_bytes", nullable = false)
    private long usableBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "volume_role", nullable = false, length = 16)
    private PlannedVolumeRole volumeRole;

    /** 정의서 규칙 순번(1-based) — 기록용 서수(plan Q2 확정: int). */
    @Column(name = "rule_no", nullable = false)
    private int ruleNo;

    /** 재채집이 보고한 상태 원문(Optl · Okay (OKY) 등) — 동기화 대기는 하지 않는다(결정 D-9). */
    @Column(length = 64)
    private String state;

    public static RaidVolume of(GuestServer guestServer, String name, RaidLevel raidLevel,
                                String memberSlotsJson, long usableBytes, PlannedVolumeRole volumeRole,
                                int ruleNo, String state) {
        return RaidVolume.builder()
                .id(org.hibernate.id.uuid.UuidVersion7Strategy.INSTANCE.generateUuid(null))
                .guestServer(guestServer)
                .name(name)
                .raidLevel(raidLevel)
                .memberSlotsJson(memberSlotsJson)
                .usableBytes(usableBytes)
                .volumeRole(volumeRole)
                .ruleNo(ruleNo)
                .state(state)
                .build();
    }
}
