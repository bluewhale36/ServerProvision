package com.example.serverprovision.provisioning.biossetting.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 템플릿 상세 뷰모델 — "flat 저장값 하나의 세 얼굴" 중 두 개를 담는다:
 * 재조인 그룹 렌더(무엇이 어떻게 바뀌는지 한눈에) + Redfish Request Body 프리뷰(실행 시 전송분).
 *
 * <p>{@code catalogMissing} 은 보드가 카탈로그 목록에서 사라진 경우의 degraded 렌더 + 수정 버튼
 * disabled 를 함께 구동하는 단일 판정이다(UI 차단·서버 404 안전망의 SSOT 공유).
 * 그 경우 {@code groups} 는 비고 모든 저장 속성이 {@code stale} 로 내려간다(raw 표시).</p>
 */
public record BiosSettingTemplateDetailResponse(
        Long id,
        String name,
        String description,
        Long boardModelId,
        String boardModelName,
        boolean inUse,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean catalogMissing,
        List<Group> groups,
        List<StaleEntry> stale,
        RedfishPreview redfish
) {

    /** registry MenuPath 기준 그룹(첫 등장 순서 보존). */
    public record Group(String menuPath, List<Entry> entries) {
    }

    /**
     * 재조인된 저장 속성 1건 — 라벨은 DisplayName, 값은 기본값 대비(default → stored).
     * ENUMERATION 은 표시명(valueDisplayName)으로, 원시 값은 {@code storedRaw}/{@code defaultRaw} 에 보존.
     */
    public record Entry(
            String attributeName,
            String displayName,
            String widgetKind,
            String defaultDisplay,
            String defaultRaw,
            String storedDisplay,
            String storedRaw,
            boolean resetRequired
    ) {
    }

    /** registry 에 없어진 저장 속성(BIOS 카탈로그 개정) — 경고 표시용. 재저장 시 자연 탈락. */
    public record StaleEntry(String attributeName, String storedRaw) {
    }

    /** 실행 시 전송될 Redfish 계획 — 저장 flat 의 무변환 투영(조회 시점 조립, 저장 안 함). */
    public record RedfishPreview(
            String method,
            String target,
            String bodyJson,
            boolean resetRequired,
            String resetTarget,
            String resetBodyJson
    ) {
    }
}
