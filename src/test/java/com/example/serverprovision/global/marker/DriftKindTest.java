package com.example.serverprovision.global.marker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R9-2 → S6-2-1 — DriftKind 사용자 문구·해결 방식({@code mode}) SSOT 의 회귀 고정.
 * <p>mode 배정은 서버 가드({@code PathReconciliationService.apply})·템플릿 버튼 노출·스캔 무인 적용
 * 자격이 공유하는 유일 소스이므로, 배정이 바뀌면 이 테스트가 의도 변경을 강제로 드러낸다.</p>
 */
class DriftKindTest {

    @Test
    @DisplayName("10종 전부 label / description / recommendedAction 이 비어있지 않다 (사용자 가이드 원문)")
    void allKindsHaveUserFacingTexts() {
        for (DriftKind kind : DriftKind.values()) {
            assertThat(kind.getLabel()).as("%s.label", kind).isNotBlank();
            assertThat(kind.getDescription()).as("%s.description", kind).isNotBlank();
            assertThat(kind.getRecommendedAction()).as("%s.recommendedAction", kind).isNotBlank();
        }
    }

    @Test
    @DisplayName("mode 배정 : AUTO 4 / MANUAL 4 / NONE 2 — S6-3-2 까지의 승격 반영 (SSOT 회귀 고정)")
    void modeAssignments() {
        assertThat(byMode(DriftResolutionMode.AUTO)).containsExactlyInAnyOrder(
                DriftKind.PATH_DRIFT,
                DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL,
                DriftKind.TRASH_MARKER_STALE,
                DriftKind.GHOST_DB_ROW
        );
        assertThat(byMode(DriftResolutionMode.MANUAL)).containsExactlyInAnyOrder(
                DriftKind.SOFTDEL_ESCAPE_TO_OTHER,
                DriftKind.TRASH_LOST,
                DriftKind.SIGNATURE_INVALID,
                DriftKind.ORPHAN
        );
        assertThat(byMode(DriftResolutionMode.NONE)).containsExactlyInAnyOrder(
                DriftKind.MISSING,
                DriftKind.HASH_MISMATCH
        );
    }

    @Test
    @DisplayName("파생 접근자 일관성 : isAutoApplicable=(mode==AUTO), isManuallyResolvable=(mode!=NONE)")
    void derivedAccessorsAreConsistentWithMode() {
        for (DriftKind kind : DriftKind.values()) {
            assertThat(kind.isAutoApplicable()).as("%s.isAutoApplicable", kind)
                    .isEqualTo(kind.getMode() == DriftResolutionMode.AUTO);
            assertThat(kind.isManuallyResolvable()).as("%s.isManuallyResolvable", kind)
                    .isEqualTo(kind.getMode() != DriftResolutionMode.NONE);
        }
    }

    @Test
    @DisplayName("S6-3-3 recheckable 축 : MISSING·ORPHAN·SIGNATURE_INVALID 3종 — mode 와 독립 (HASH 는 대용량 재계산 문제로 S6-3-4 와 함께)")
    void recheckableAxis() {
        Set<DriftKind> recheckable = Arrays.stream(DriftKind.values())
                .filter(DriftKind::isRecheckable)
                .collect(Collectors.toSet());
        assertThat(recheckable).containsExactlyInAnyOrder(
                DriftKind.MISSING, DriftKind.ORPHAN, DriftKind.SIGNATURE_INVALID);
    }

    private static Set<DriftKind> byMode(DriftResolutionMode mode) {
        return Arrays.stream(DriftKind.values())
                .filter(k -> k.getMode() == mode)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("S6-1 : RESOURCE_RENAMED 계열 2종은 삭제됨 — 재도입 시 이 테스트가 의도 확인을 강제")
    void renamedKindsAreRemoved() {
        assertThat(DriftKind.values()).hasSize(10);
        for (String removed : new String[]{"RESOURCE_RENAMED", "RESOURCE_RENAMED_ORPHAN"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DriftKind.valueOf(removed))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("S6-2-2 : 모든 상수명은 32자 이하 — drift.kind 컬럼(VARCHAR 32)과의 계약 고정")
    void allKindNamesFitInKindColumn() {
        for (DriftKind kind : DriftKind.values()) {
            assertThat(kind.name().length()).as("%s 길이", kind).isLessThanOrEqualTo(32);
        }
    }
}
