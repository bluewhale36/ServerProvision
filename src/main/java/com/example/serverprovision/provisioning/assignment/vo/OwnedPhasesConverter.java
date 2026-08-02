package com.example.serverprovision.provisioning.assignment.vo;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link OwnedPhases} ↔ CSV 컬럼({@code "FW_UPDATE,OS_INSTALL"}) 왕복.
 *
 * <p>소비 스냅샷 행은 영구 보존이라 {@code ProvisioningPhase.name()} 대신 <b>명시 안정 코드</b>로
 * 직렬화한다(결정 D-F). enum 상수를 rename 해도 코드 문자열은 이 표에 고정돼 불멸 행의 하위호환이 깨지지
 * 않는다. ordinal 기반은 선언 순서 재편에 취약해 쓰지 않는다. 미등록 코드 저장본은 silent 흡수하지 않고
 * 명시 예외로 거절한다.</p>
 */
@Converter(autoApply = false)
public class OwnedPhasesConverter implements AttributeConverter<OwnedPhases, String> {

    private static final Map<ProvisioningPhase, String> TO_CODE;
    private static final Map<String, ProvisioningPhase> FROM_CODE;

    static {
        EnumMap<ProvisioningPhase, String> toCode = new EnumMap<>(ProvisioningPhase.class);
        toCode.put(ProvisioningPhase.FIRMWARE_UPDATING, "FW_UPDATE");
        toCode.put(ProvisioningPhase.FIRMWARE_SETTING, "FW_SETTING");
        toCode.put(ProvisioningPhase.OS_INSTALLING, "OS_INSTALL");
        toCode.put(ProvisioningPhase.OS_SETTING, "OS_SETTING");
        TO_CODE = Collections.unmodifiableMap(toCode);

        Map<String, ProvisioningPhase> fromCode = new HashMap<>();
        toCode.forEach((phase, code) -> fromCode.put(code, phase));
        FROM_CODE = Collections.unmodifiableMap(fromCode);
    }

    @Override
    public String convertToDatabaseColumn(OwnedPhases attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";   // 빈 정의서 할당 = ownedPhases ∅ (컬럼 NOT NULL — 빈 문자열로 표현)
        }
        List<String> codes = new ArrayList<>();
        for (ProvisioningPhase phase : attribute.asSet()) {   // 이미 선언 순 정렬
            codes.add(code(phase));
        }
        return String.join(",", codes);
    }

    @Override
    public OwnedPhases convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return OwnedPhases.empty();
        }
        List<ProvisioningPhase> phases = new ArrayList<>();
        for (String token : dbData.split(",")) {
            String code = token.trim();
            if (code.isEmpty()) {
                continue;
            }
            ProvisioningPhase phase = FROM_CODE.get(code);
            if (phase == null) {
                throw new IllegalStateException("owned_phases 에 알 수 없는 안정 코드가 있습니다: " + code);
            }
            phases.add(phase);
        }
        return OwnedPhases.of(phases);
    }

    private static String code(ProvisioningPhase phase) {
        String code = TO_CODE.get(phase);
        if (code == null) {
            throw new IllegalStateException("안정 코드가 없는 phase 입니다(소유 불가 phase): " + phase);
        }
        return code;
    }
}
