package com.example.serverprovision.provisioning.assignment.entity;

import com.example.serverprovision.global.entity.BaseTimeEntity;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettings;
import com.example.serverprovision.provisioning.assignment.vo.FrozenBiosSettingsConverter;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayload;
import com.example.serverprovision.provisioning.setting.vo.ProcessPayloadConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 할당 스냅샷의 단계 1행 — 정의서 {@code SettingProcess} 를 행 단위로 복사한 <b>불변 이력</b>.
 *
 * <p>{@code SettingProcess} 와 구조는 동형이나 생명주기가 갈린다(정의서 종속 · 가변 vs 할당 이력 · 불변).
 * {@code @MappedSuperclass} 억지 통합은 over-abstraction 비용이라 하지 않고, {@code payload} 직렬화만
 * {@link ProcessPayloadConverter} 를 재사용한다(무변환 복사 — 저장·요청 해석 SSOT 공유).</p>
 *
 * <p>{@code frozenBiosSettings} 는 BASIC_SETTING 전용 deep-freeze(결정 D-C) — 그 외 타입은 null.
 * {@code processType} 컬럼은 표시 · 감사 전용이고 phase 진행 권위는 {@code SettingAssignmentSnapshot.ownedPhases}
 * 다(결정 D-G).</p>
 */
@Entity
@Table(name = "assigned_process_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_assigned_process_snapshot_type",
                columnNames = {"assignment_id", "process_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssignedProcessSnapshot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private SettingAssignmentSnapshot assignment;

    /** 표시 · 감사 전용 판별자(권위는 ownedPhases VO). payload 에서 단방향 파생. */
    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", nullable = false, length = 32)
    private SettingProcessType processType;

    /** 할당 시점 복사된 계약 원문(무변환) — 컨버터 재사용. */
    @Convert(converter = ProcessPayloadConverter.class)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private ProcessPayload payload;

    /** BASIC_SETTING deep-freeze(결정 D-C) — 그 외 타입은 null(템플릿 FK 없음, 자족 스냅샷). */
    @Convert(converter = FrozenBiosSettingsConverter.class)
    @Column(name = "frozen_bios_settings_json", columnDefinition = "json")
    private FrozenBiosSettings frozenBiosSettings;

    public AssignedProcessSnapshot(ProcessPayload payload, FrozenBiosSettings frozenBiosSettings) {
        this.payload = payload;
        this.processType = payload.processType();
        this.frozenBiosSettings = frozenBiosSettings;
    }

    /** 양방향 연관 세팅 — {@link SettingAssignmentSnapshot#addProcess} 만 호출. */
    void assignTo(SettingAssignmentSnapshot assignment) {
        this.assignment = assignment;
    }
}
