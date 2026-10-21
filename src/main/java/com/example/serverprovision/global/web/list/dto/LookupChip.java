package com.example.serverprovision.global.web.list.dto;

/**
 * 검색 선택기 줄에 쌓이는 칩 하나(S8-6) — 제출 값(파라미터 값)과 보이는 이름.
 * 칩 마크업(fragments/ui/lookup-field)이 짝 hidden input 과 함께 그린다.
 */
public record LookupChip(String value, String label) {
}
