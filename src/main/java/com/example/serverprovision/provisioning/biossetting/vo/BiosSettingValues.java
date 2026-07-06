package com.example.serverprovision.provisioning.biossetting.vo;

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
}
