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

    /**
     * MK4-4-2 — 확인 창의 제목은 그 종류를 해결한다는 것이 <b>무슨 행위인가</b>를 말한다.
     *
     * <p>종전에는 전 종류가 "드리프트 해결" 하나로 떴다. 경로 기록만 고치는 일과 파일을 지우는
     * 일이 같은 제목이라, 무엇을 승인하는지가 본문 문장 안에 묻혀 있었다.</p>
     */
    @Test
    @DisplayName("시스템이 처리하는 종류는 확인 창 제목(행위 이름)을 들고 있다")
    void resolvableKindsCarryActionTitle() {
        for (DriftKind kind : DriftKind.values()) {
            if (kind.getMode() == DriftResolutionMode.NONE) {
                // 시스템이 할 수 있는 일이 없으면 확인 창 자체가 뜨지 않는다.
                assertThat(kind.getResolveTitle()).as("%s.resolveTitle", kind).isNull();
                continue;
            }
            assertThat(kind.getResolveTitle()).as("%s.resolveTitle", kind).isNotBlank();
            // 제목은 행위지 종류 이름이 아니다 — 둘이 같으면 "무엇을 하는가" 를 말하지 못한다.
            assertThat(kind.getResolveTitle()).as("%s — 제목이 종류 이름과 같다", kind)
                    .isNotEqualTo(kind.getLabel());
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
    @DisplayName("mode 배정 : AUTO 5 / MANUAL 6 / NONE 1 — 확인만 받으면 시스템이 처리하는 종류는 전용 폼이어도 MANUAL")
    void modeAssignments() {
        assertThat(byMode(DriftResolutionMode.AUTO)).containsExactlyInAnyOrder(
                DriftKind.PATH_DRIFT,
                DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL,
                DriftKind.TRASH_MARKER_STALE,
                DriftKind.GHOST_DB_ROW,
                // S11-1 — 미아 마커 정리는 실물 무손실(마커 재발급 가능)이라 AUTO. 무인 발동은 옵트인 뒤.
                DriftKind.SOFTDEL_MARKER_STRAY
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
    @DisplayName("처리 입구 축 : 표준 [해결] 9 / 전용 폼 2 / 없음 1 — mode 와 독립 (등급을 올려도 버튼 배선은 그대로)")
    void resolveEntryAxis() {
        assertThat(byEntry(DriftResolveEntry.DEDICATED))
                .containsExactlyInAnyOrder(DriftKind.HASH_MISMATCH, DriftKind.RESOURCE_REPLICA);
        assertThat(byEntry(DriftResolveEntry.NONE)).containsExactlyInAnyOrder(DriftKind.MISSING);
        assertThat(byEntry(DriftResolveEntry.STANDARD)).hasSize(9);
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
        // HF4-5 RESOURCE_REPLICA + S11-1 SOFTDEL_MARKER_STRAY 로 12종 (kind 증감 시 이 잠금이 의도 확인을 강제)
        assertThat(DriftKind.values()).hasSize(12);
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
        // S11-1 미아 마커 — 이동 전후가 아니라 '있어야 할 자리' 와 '마커가 발견된 자리' 다.
        assertThat(DriftKind.SOFTDEL_MARKER_STRAY.getOldPathLabel()).isEqualTo("자원 기대 경로 (기록 기준)");
        assertThat(DriftKind.SOFTDEL_MARKER_STRAY.getNewPathLabel()).isEqualTo("마커 발견 경로");
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

    /**
     * MK4-2 재개 조건 1 의 안전망. 순서 산정은 종류가 늘어날 것을 전제로 enum 속성에 기대므로,
     * 새 종류가 위험도를 빠뜨리면 순서 계산에서 조용히 빠지는 것이 아니라 여기서 빨간불이 켜져야 한다.
     */
    @Test
    @DisplayName("MK4-2 : 모든 종류가 위험도를 갖는다 — 신설 종류의 누락 방지")
    void everyKindDeclaresSeverity() {
        for (DriftKind kind : DriftKind.values()) {
            assertThat(kind.getSeverity())
                    .as("%s 에 위험도가 없다 — DriftKind 선언에 DriftSeverity 를 추가하라", kind)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("MK4-2 : 위험도 배정이 계획서 표와 일치한다")
    void severityAssignmentMatchesPlan() {
        // 즉시 — 이 상태로 프로비저닝하면 실패하거나 잘못된 것이 설치된다.
        assertThat(DriftKind.MISSING.getSeverity()).isEqualTo(DriftSeverity.IMMEDIATE);
        assertThat(DriftKind.PATH_DRIFT.getSeverity()).isEqualTo(DriftSeverity.IMMEDIATE);
        assertThat(DriftKind.HASH_MISMATCH.getSeverity()).isEqualTo(DriftSeverity.IMMEDIATE);
        assertThat(DriftKind.SIGNATURE_INVALID.getSeverity()).isEqualTo(DriftSeverity.IMMEDIATE);
        // 주의 — 지금은 되지만 방치하면 잘못된 것을 집는다.
        assertThat(DriftKind.RESOURCE_REPLICA.getSeverity()).isEqualTo(DriftSeverity.ATTENTION);
        assertThat(DriftKind.SOFTDEL_ESCAPE_TO_OTHER.getSeverity()).isEqualTo(DriftSeverity.ATTENTION);
        // 기록 정리 — 이미 삭제된 자원의 기록 문제라 프로비저닝에 영향이 없다.
        assertThat(DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL.getSeverity()).isEqualTo(DriftSeverity.RECORD);
        assertThat(DriftKind.TRASH_LOST.getSeverity()).isEqualTo(DriftSeverity.RECORD);
        assertThat(DriftKind.GHOST_DB_ROW.getSeverity()).isEqualTo(DriftSeverity.RECORD);
        // 정리 — 실물 무손실, 마커만 남았다.
        assertThat(DriftKind.ORPHAN.getSeverity()).isEqualTo(DriftSeverity.TIDY);
        assertThat(DriftKind.TRASH_MARKER_STALE.getSeverity()).isEqualTo(DriftSeverity.TIDY);
        assertThat(DriftKind.SOFTDEL_MARKER_STRAY.getSeverity()).isEqualTo(DriftSeverity.TIDY);
    }
}
