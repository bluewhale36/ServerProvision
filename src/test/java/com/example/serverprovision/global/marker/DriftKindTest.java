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
    @DisplayName("전 종 label / description 이 비어있지 않고, 해결 등급에 맞는 안내가 빠지지 않는다")
    void allKindsHaveUserFacingTexts() {
        for (DriftKind kind : DriftKind.values()) {
            assertThat(kind.getLabel()).as("%s.label", kind).isNotBlank();
            assertThat(kind.getDescription()).as("%s.description", kind).isNotBlank();

            // 시스템이 처리할 수 있는 종류는 [해결] 이 무엇을 하는지 반드시 밝혀야 한다.
            if (kind.getMode() != DriftResolutionMode.NONE) {
                assertThat(kind.getResolveAction()).as("%s.resolveAction", kind).isNotBlank();
            } else {
                // 시스템이 못 하는 종류는 사람이 할 일이 반드시 있어야 한다 — 둘 다 비면 화면이 백지가 된다.
                assertThat(kind.getOperatorAction()).as("%s.operatorAction", kind).isNotBlank();
            }
            // 어느 쪽이든 최소 하나는 있어야 상세가 안내를 낸다.
            assertThat(kind.getResolveAction() != null || kind.getOperatorAction() != null)
                    .as("%s — 안내가 하나도 없다", kind).isTrue();
        }
    }

    @Test
    @DisplayName("해결 등급은 사용자 문구와 배지 색을 직접 들고 있다 (템플릿에 종류별 조건식이 자라지 않는 근거)")
    void modesCarryTheirOwnPresentation() {
        for (DriftResolutionMode mode : DriftResolutionMode.values()) {
            assertThat(mode.getLabel()).as("%s.label", mode).isNotBlank();
            assertThat(mode.getDescription()).as("%s.description", mode).isNotBlank();
            assertThat(mode.getBadgeColor()).as("%s.badgeColor", mode).isNotBlank();
        }
    }

    @Test
    @DisplayName("mode 배정 : AUTO 4 / MANUAL 6 / NONE 1 — 확인만 받으면 시스템이 처리하는 종류는 전용 폼이어도 MANUAL")
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
                DriftKind.ORPHAN,
                // 전용 폼(자원명 입력 · 남길 쪽 택일)을 쓰지만, 확인만 받으면 시스템이 파일과 DB 를
                // 바꿔 주므로 사용자가 보는 등급은 '확인 후 자동 해결 가능' 이다.
                DriftKind.HASH_MISMATCH,
                DriftKind.RESOURCE_REPLICA
        );
        // 시스템이 대신 해 줄 것이 하나도 없는 종류는 자원 소실뿐이다 — 파일을 사람이 되돌려 놓아야 한다.
        assertThat(byMode(DriftResolutionMode.NONE)).containsExactlyInAnyOrder(DriftKind.MISSING);
    }

    @Test
    @DisplayName("처리 입구 축 : 표준 [해결] 8 / 전용 폼 2 / 없음 1 — mode 와 독립 (등급을 올려도 버튼 배선은 그대로)")
    void resolveEntryAxis() {
        assertThat(byEntry(DriftResolveEntry.DEDICATED))
                .containsExactlyInAnyOrder(DriftKind.HASH_MISMATCH, DriftKind.RESOURCE_REPLICA);
        assertThat(byEntry(DriftResolveEntry.NONE)).containsExactlyInAnyOrder(DriftKind.MISSING);
        assertThat(byEntry(DriftResolveEntry.STANDARD)).hasSize(8);
    }

    private static Set<DriftKind> byEntry(DriftResolveEntry entry) {
        return Arrays.stream(DriftKind.values())
                .filter(k -> k.getResolveEntry() == entry)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("파생 접근자 : isAutoApplicable=(mode==AUTO), isManuallyResolvable=(입구==STANDARD)")
    void derivedAccessorsAreConsistentWithTheirOwnAxis() {
        for (DriftKind kind : DriftKind.values()) {
            assertThat(kind.isAutoApplicable()).as("%s.isAutoApplicable", kind)
                    .isEqualTo(kind.getMode() == DriftResolutionMode.AUTO);
            // 표준 버튼 노출은 등급이 아니라 처리 입구가 정한다 — 전용 폼 종류에 표준 버튼이
            // 하나 더 뜨면 공용 apply 가 전략 bean 없이 거절한다.
            assertThat(kind.isManuallyResolvable()).as("%s.isManuallyResolvable", kind)
                    .isEqualTo(kind.getResolveEntry() == DriftResolveEntry.STANDARD);
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
        // HF4-5 — RESOURCE_REPLICA 추가로 11종 (kind 증감 시 이 잠금이 의도 확인을 강제)
        assertThat(DriftKind.values()).hasSize(11);
        for (String removed : new String[]{"RESOURCE_RENAMED", "RESOURCE_RENAMED_ORPHAN"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DriftKind.valueOf(removed))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("경로 2필드 라벨 다형 — 기본은 'DB 기록'/'실제 파일 경로', 뜻이 다른 종류만 재정의")
    void pathLabelsArePolymorphic() {
        // 기본값 : 대부분의 종류에서 두 값은 'DB 가 안다고 하는 위치' 와 '디스크에서 실제로 발견된 위치' 다.
        assertThat(DriftKind.PATH_DRIFT.getOldPathLabel()).isEqualTo("DB 기록");
        assertThat(DriftKind.PATH_DRIFT.getNewPathLabel()).isEqualTo("실제 파일 경로");

        // 재정의 : 그 뜻이 아닌 종류들.
        assertThat(DriftKind.RESOURCE_REPLICA.getOldPathLabel()).isEqualTo("원본 경로 (DB 기록)");
        assertThat(DriftKind.RESOURCE_REPLICA.getNewPathLabel()).isEqualTo("복제본 경로");
        // DB 에 주인이 없어 '기록' 이 존재하지 않는다 — 디스크에서 마커를 발견한 자리다.
        assertThat(DriftKind.ORPHAN.getOldPathLabel()).isEqualTo("감지된 실제 파일 경로");
        // 휴지통 자리를 가리키는 두 종류 — DB 경로도 발견 위치도 아니다.
        assertThat(DriftKind.TRASH_MARKER_STALE.getOldPathLabel()).isEqualTo("휴지통 보관 경로");
        assertThat(DriftKind.TRASH_LOST.getOldPathLabel()).isEqualTo("휴지통 기록 경로");
    }

    @Test
    @DisplayName("실제 파일 경로가 없을 때 : 유령 기록만 '없다' 를 띄우고 나머지는 줄을 감춘다")
    void absentNewPathTextOnlyWhereAbsenceIsThePoint() {
        // 파일이 어디에도 없다는 사실이 문제의 전부인 종류라, 줄을 지우면 그 사실이 화면에서 사라진다.
        assertThat(DriftKind.GHOST_DB_ROW.getAbsentNewPathText()).isEqualTo("(찾을 수 없습니다.)");
        for (DriftKind kind : DriftKind.values()) {
            if (kind == DriftKind.GHOST_DB_ROW) continue;
            assertThat(kind.getAbsentNewPathText()).as("%s.absentNewPathText", kind).isNull();
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
