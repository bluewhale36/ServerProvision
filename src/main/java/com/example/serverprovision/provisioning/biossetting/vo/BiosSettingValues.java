package com.example.serverprovision.provisioning.biossetting.vo;

import java.util.ArrayList;
import java.util.List;
import com.example.serverprovision.provisioning.exception.InvalidBiosValueException;
import com.example.serverprovision.provisioning.domain.vo.BiosEnumOption;
import com.example.serverprovision.provisioning.domain.BiosAttribute;
import com.example.serverprovision.provisioning.biossetting.enums.BiosStaleKind;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 템플릿이 담는 BIOS 설정 값 집합 — 기본값 대비 <b>변경분(diff)만</b>, coerce 후 타입 보존.
 *
 * <p>flat 1-depth 가 확정 설계다(U2-2 설계 report §03): 최종 적용 표면인 Redfish
 * {@code PATCH …/Bios/SD} 의 {@code Attributes} 와 구조 동형이라 execution 이 무변환 소비하고,
 * 화면의 중첩(페이지 계층)은 렌더 시 registry/SetupData 재조인으로 복원한다.</p>
 */
public record BiosSettingValues(Map<BiosAttributeName, BiosAttributeValue> entries) {

    public BiosSettingValues {
        if (entries == null || entries.isEmpty()) {
            // 도메인 invariant — 빈 템플릿은 존재 의미가 없다. 요청 검증(emptyDiff 400)의 최종 방어선.
            throw new IllegalArgumentException("BIOS 세팅 값은 최소 1개 속성을 가져야 합니다.");
        }
        entries = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public int size() {
        return entries.size();
    }

    /**
     * 이 값들이 주어진 레지스트리와 어긋나는 것들(E3-3 R4) — 속성 부재 · 값 불허. 템플릿 상세 경고 · 할당 차단 ·
     * 집행 전 검증이 전부 이 규칙 하나를 부른다. 값 규칙은 {@code BiosAttributeType.validate} 에 위임해
     * 편집기 저장 검증과 같은 판정을 쓴다.
     */
    public java.util.List<BiosStaleValue> staleAgainst(
            Map<BiosAttributeName, BiosAttribute> registry) {
        java.util.List<BiosStaleValue> stale = new java.util.ArrayList<>();
        for (Map.Entry<BiosAttributeName, BiosAttributeValue> entry : entries.entrySet()) {
            String raw = String.valueOf(entry.getValue().jsonValue());
            BiosAttribute attr = registry.get(entry.getKey());
            if (attr == null) {
                stale.add(new BiosStaleValue(entry.getKey(), raw,
                        BiosStaleKind.MISSING_ATTRIBUTE, List.of()));
                continue;
            }
            try {
                attr.type().validate(attr, raw);
            } catch (InvalidBiosValueException notAllowed) {
                stale.add(new BiosStaleValue(entry.getKey(), raw,
                        BiosStaleKind.VALUE_NOT_ALLOWED, allowedOf(attr)));
            }
        }
        return List.copyOf(stale);
    }

    private static java.util.List<String> allowedOf(BiosAttribute attr) {
        return switch (attr.type()) {
            case ENUMERATION -> attr.options().stream()
                    .map(com.example.serverprovision.provisioning.domain.vo.BiosEnumOption::valueName).toList();
            case INTEGER -> List.of(attr.bounds().lower() + "~" + attr.bounds().upper());
            case BOOLEAN -> List.of("true", "false");
            default -> List.of();   // PASSWORD 등 템플릿에 실리지 않는 타입 — 허용 목록이 없다
        };
    }
}
