package com.example.serverprovision.provisioning.assignment.mapper;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * {@code SettingProcessType} → {@code ProvisioningPhase} 경계 매핑 — <b>할당 도메인이 소유</b>하는 선언적 표.
 *
 * <p>{@code SettingProcessType} 상수별 메서드로 표현하지 <b>않는다</b>: {@code provisioning.setting.enums} →
 * {@code execution.enums} 의존 신설이 R7 이 제거한 순환을 재생성하기 때문이다. 매핑은 이 경계 클래스가
 * 양쪽 enum 을 함께 참조하는 단일 지점에 격리한다. identity 매핑 · {@code displayName} 파생은 하지 않고
 * 5 상수를 1:1 로 명시하며, 전 상수 커버리지는 단위 테스트가 못박는다.</p>
 */
public final class SettingProcessPhaseMapper {

    private static final Map<SettingProcessType, ProvisioningPhase> PHASE_OF;

    static {
        EnumMap<SettingProcessType, ProvisioningPhase> map = new EnumMap<>(SettingProcessType.class);
        map.put(SettingProcessType.BASIC_UPDATE, ProvisioningPhase.FIRMWARE_UPDATING);
        map.put(SettingProcessType.BASIC_SETTING, ProvisioningPhase.FIRMWARE_SETTING);
        map.put(SettingProcessType.RAID_CONFIGURATION, ProvisioningPhase.RAID_CONFIGURATION);
        map.put(SettingProcessType.OS_INSTALLATION, ProvisioningPhase.OS_INSTALLING);
        map.put(SettingProcessType.OS_SETTING, ProvisioningPhase.OS_SETTING);
        PHASE_OF = Collections.unmodifiableMap(map);
    }

    private SettingProcessPhaseMapper() {
    }

    /** 단일 단계 타입의 실행 phase. 매핑 누락은 프로그램 버그이므로 명시 예외(전 상수 커버리지 테스트가 방어). */
    public static ProvisioningPhase phaseOf(SettingProcessType type) {
        ProvisioningPhase phase = PHASE_OF.get(type);
        if (phase == null) {
            throw new IllegalStateException("매핑되지 않은 SettingProcessType 입니다: " + type);
        }
        return phase;
    }

    /** 단계 타입 집합 → {@link OwnedPhases}(union, derive-then-freeze 의 derive 단계). */
    public static OwnedPhases toOwnedPhases(Set<SettingProcessType> types) {
        return OwnedPhases.of(types.stream().map(SettingProcessPhaseMapper::phaseOf).toList());
    }
}
