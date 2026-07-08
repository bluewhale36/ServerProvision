package com.example.serverprovision.global.marker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R9-2 — DriftKind 사용자 문구·autoApplicable SSOT 의 회귀 고정.
 * <p>autoApplicable 집합은 서버 가드({@code PathReconciliationService.apply})와 템플릿 버튼 노출이
 * 공유하는 유일 소스이므로, 집합이 바뀌면 이 테스트가 의도 변경을 강제로 드러낸다.</p>
 */
class DriftKindTest {

    @Test
    @DisplayName("12종 전부 label / description / recommendedAction 이 비어있지 않다 (사용자 가이드 원문)")
    void allKindsHaveUserFacingTexts() {
        for (DriftKind kind : DriftKind.values()) {
            assertThat(kind.getLabel()).as("%s.label", kind).isNotBlank();
            assertThat(kind.getDescription()).as("%s.description", kind).isNotBlank();
            assertThat(kind.getRecommendedAction()).as("%s.recommendedAction", kind).isNotBlank();
        }
    }

    @Test
    @DisplayName("autoApplicable=true 집합은 정확히 5종 — 종전 서버 가드 나열과 동일 (SSOT 승격 회귀 고정)")
    void autoApplicableSetIsExactlyTheFivePromotedKinds() {
        Set<DriftKind> applicable = Arrays.stream(DriftKind.values())
                .filter(DriftKind::isAutoApplicable)
                .collect(Collectors.toSet());

        assertThat(applicable).containsExactlyInAnyOrder(
                DriftKind.PATH_DRIFT,
                DriftKind.RESOURCE_RENAMED,
                DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL,
                DriftKind.TRASH_MARKER_STALE,
                DriftKind.GHOST_DB_ROW
        );
    }
}
