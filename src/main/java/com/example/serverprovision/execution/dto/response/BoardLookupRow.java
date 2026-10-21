package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.management.board.enums.Vendor;

/**
 * 보드 검색 팝업의 행(S8-6) — 등록된 보드 모델과 그 보드를 가진 서버 수(없으면 0).
 *
 * @param serverCount 이 보드를 가진 서버 수(회수 포함)
 * @param enabled     보드 모델이 활성인가 — 비활성은 '비활성 포함' 토글을 켰을 때만 후보(CP6 사용자 지시)
 * @param deprecated  보드 모델이 사용 중단(권고)인가 — 거르지 않고 배지로만 알린다
 */
public record BoardLookupRow(Long id, String name, Vendor vendor, Long serverCount, boolean enabled, boolean deprecated) {

    /** 검색어가 이름 또는 제조사 표시명에 들어 있는가(대소문자 무시) · 빈 검색어는 모두 맞음. */
    public boolean matches(String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        String needle = q.trim().toLowerCase();
        return name.toLowerCase().contains(needle) || vendor.getDisplayName().toLowerCase().contains(needle);
    }
}
