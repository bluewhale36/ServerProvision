package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세팅 정의서 목록 행 응답. ({@code GET /provisioning/setting})
 */
public record SettingSummaryResponse(
        Long id,
        String name,
        /** 정의서에 포함된 단계 타입 목록 — 목록 화면의 단계 요약 배지용. */
        List<SettingProcessType> processTypes,
        /** soft-delete 여부(U3-2-b) — includeDeleted 목록에서 "삭제됨" 배지 렌더용. */
        boolean deleted,
        /** 활성 여부(U3-2-b DEC-G) — false 면 목록에 "비활성" 배지. 신규 할당은 차단된다. */
        boolean enabled,
        /** 사용 중단 권고(U3-2-b DEC-G) — 목록 "사용 중단" 배지 · 할당 드롭다운 옵션 접미. 차단은 아니다. */
        boolean deprecated,
        LocalDateTime createdAt
) {
}
