package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.management.board.enums.Vendor;

/** 서버 목록 보드 셀렉트의 선택지(S8-2) — 서버에 실제로 등장한 보드 모델만 담는다. 제조사로 {@code optgroup} 을 가른다. */
public record BoardOptionResponse(Long id, String name, Vendor vendor) {
}
