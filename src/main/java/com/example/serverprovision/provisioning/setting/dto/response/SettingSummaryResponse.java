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
        LocalDateTime createdAt,
        /**
         * 이 정의서가 요구하는 메인보드(U3-5-a) — 요구하지 않으면(보드 AUTO 또는 해당 단계 없음) 둘 다 null.
         * 서버 상세의 선택지가 서버의 보드와 대조해 맞지 않는 정의서를 잠그는 데 쓴다. 이름을 함께 싣는 것은
         * 잠긴 이유를 라벨에 적어야 하기 때문이며, 대조 자체는 이름이 아니라 id 로 한다.
         */
        Long requiredBoardModelId,
        String requiredBoardModelName
) {

    /** 요구 보드 없이 만드는 축약 생성자 — 요구 보드가 무의미한 목록(전체 조회 등)이 쓴다. */
    public SettingSummaryResponse(Long id, String name, List<SettingProcessType> processTypes,
                                  boolean deleted, boolean enabled, boolean deprecated, LocalDateTime createdAt) {
        this(id, name, processTypes, deleted, enabled, deprecated, createdAt, null, null);
    }
}
