package com.example.serverprovision.management.bios.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * BIOS 버전 순서 재정렬(E2-1-a) — 보드의 살아있는 BIOS id 를 원하는 순서(앞 = 최신)로 전부 담는다.
 * 삭제 행은 대상이 아니다(순위 공간은 공유하되 상대 위치가 보존된다).
 */
public record BiosRankRequest(@NotEmpty(message = "순서 목록이 비어 있습니다.") List<Long> orderedIds) {
}
