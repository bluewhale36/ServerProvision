package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.WindowsImagePresence;

import java.util.Map;

/**
 * 상세 화면의 참조 id → 표시명 사상(사용자 지시 2026-07-05: id 가 아닌 사람이 식별 가능한 이름).
 * 조회 시점 해석이라 자원명 변경이 즉시 반영되고, 사상에 없는 id(자원 삭제 등)는 템플릿이
 * {@code #id} 로 폴백한다. RAID 카드({@code raidCards}, U4-1-1)는 purge 참조검사가 없는 소프트참조라
 * 템플릿이 "(사라진 카드 #id)" 로 그 사실을 드러낸다. Windows 설치 이미지({@code windowsImages}, E4-1-a-2)는
 * 이름이 키이며 표시명과 설치 소스 대조 결과를 함께 든다.
 */
public record ReferenceNamesResponse(
        Map<Long, String> boards,
        Map<Long, String> biosVersions,
        Map<Long, String> bmcVersions,
        Map<Long, String> osNames,
        Map<Long, String> environments,
        Map<Long, String> packageGroups,
        Map<Long, String> templates,
        Map<Long, String> isos,
        Map<Long, String> raidCards,
        Map<String, WindowsImageReference> windowsImages
) {
    public static ReferenceNamesResponse empty() {
        return new ReferenceNamesResponse(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    /** Windows 설치 이미지 참조 — 표시명(소스에 없으면 이름 그대로)과 대조 배지. */
    public record WindowsImageReference(String displayName, WindowsImagePresence presence) {
    }
}
