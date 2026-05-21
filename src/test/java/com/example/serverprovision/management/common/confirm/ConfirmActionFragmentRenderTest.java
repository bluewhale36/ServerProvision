package com.example.serverprovision.management.common.confirm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5-2 — 4 종 자원 상태 변경 modal fragment 구조 + 호출측 마커 정합성 정적 검증.
 *
 * <p>2026-05-14 재설계 : 단일 confirm-action fragment → 4 fragment (confirm-soft-delete /
 * confirm-deprecate / confirm-restore / confirm-purge) 로 분리. actionKey 문자열은 제거되고
 * 각 form 의 boolean 마커 (data-confirm-soft-delete 등) 가 action 식별자로 기능.</p>
 *
 * <p>실제 Thymeleaf 렌더링 (th:id 동적 element id) 은 브라우저 E2E 에서 사용자가 검증한다.</p>
 */
class ConfirmActionFragmentRenderTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path TEMPLATES = PROJECT_ROOT.resolve("src/main/resources/templates");
    private static final Path STATIC = PROJECT_ROOT.resolve("src/main/resources/static");

    private static String read(Path p) throws Exception {
        return Files.readString(p);
    }

    // ==== F1 — 4 fragment 파일 각자 핵심 element id + th:fragment 선언 ====================

    @Test
    @DisplayName("F1 : confirm-soft-delete fragment 가 핵심 token 보유")
    void fragmentSoftDelete_hasRequiredTokens() throws Exception {
        String html = read(TEMPLATES.resolve("fragments/management/confirm-soft-delete.html"));
        assertThat(html).contains("th:fragment=\"confirmSoftDeleteModal(prefix)\"");
        assertThat(html).contains("'Modal'");
        assertThat(html).contains("'Title'");
        assertThat(html).contains("'Message'");
        assertThat(html).contains("'ConfirmBtn'");
        assertThat(html).contains("'CancelBtn'");
    }

    @Test
    @DisplayName("F2 : confirm-deprecate fragment 가 핵심 token 보유")
    void fragmentDeprecate_hasRequiredTokens() throws Exception {
        String html = read(TEMPLATES.resolve("fragments/management/confirm-deprecate.html"));
        assertThat(html).contains("th:fragment=\"confirmDeprecateModal(prefix)\"");
        assertThat(html).contains("'ConfirmBtn'");
        assertThat(html).contains("n-btn-outline-warning");
    }

    @Test
    @DisplayName("F3 : confirm-restore fragment 가 cascade 라디오 slot 보유")
    void fragmentRestore_hasCascadeSlot() throws Exception {
        String html = read(TEMPLATES.resolve("fragments/management/confirm-restore.html"));
        assertThat(html).contains("th:fragment=\"confirmRestoreModal(prefix)\"");
        assertThat(html).contains("'CascadeWrap'");
        assertThat(html).contains("'CascadeTrue'");
        assertThat(html).contains("'CascadeFalse'");
        assertThat(html).contains("'CascadeTrueTitle'");
        assertThat(html).contains("n-btn-outline-info");
    }

    @Test
    @DisplayName("F4 : confirm-purge fragment 가 typed-name input slot 보유")
    void fragmentPurge_hasTypedNameSlot() throws Exception {
        String html = read(TEMPLATES.resolve("fragments/management/confirm-purge.html"));
        assertThat(html).contains("th:fragment=\"confirmPurgeModal(prefix)\"");
        assertThat(html).contains("'TypedTargetWrap'");
        assertThat(html).contains("'TypedTarget'");
        assertThat(html).contains("'TypedInput'");
        assertThat(html).contains("n-btn-danger");
    }

    @Test
    @DisplayName("F5 : confirm-modals 통합 fragment 가 4 sub-fragment + 5 script include")
    void fragmentModalsAll_includesAll() throws Exception {
        String html = read(TEMPLATES.resolve("fragments/management/confirm-modals.html"));
        assertThat(html).contains("th:fragment=\"all\"");
        assertThat(html).contains("confirm-soft-delete :: confirmSoftDeleteModal");
        assertThat(html).contains("confirm-deprecate :: confirmDeprecateModal");
        assertThat(html).contains("confirm-restore :: confirmRestoreModal");
        assertThat(html).contains("confirm-purge :: confirmPurgeModal");
        assertThat(html).contains("confirm-modal-base.js");
        assertThat(html).contains("confirm-soft-delete.js");
        assertThat(html).contains("confirm-deprecate.js");
        assertThat(html).contains("confirm-restore.js");
        assertThat(html).contains("confirm-purge.js");
    }

    // ==== J — JS 모듈 검증 ============================================================

    @Test
    @DisplayName("J1 : confirm-modal-base.js 가 공통 API 노출 (open / openLazy / bindFormSubmit / composeMessage / approveAndSubmit)")
    void jsBase_exposesApi() throws Exception {
        String js = read(STATIC.resolve("management/common/confirm-modal-base.js"));
        assertThat(js).contains("window.ConfirmModal");
        assertThat(js).contains("function open");
        // S5-6-1 — lazy-load API.
        assertThat(js).contains("openLazy");
        assertThat(js).contains("modalLazySlot");
        assertThat(js).contains("data-modal-active");
        assertThat(js).contains("data-modal-confirm");
        assertThat(js).contains("data-modal-cancel");
        // 공통 인프라.
        assertThat(js).contains("function bindFormSubmit");
        assertThat(js).contains("function composeMessage");
        assertThat(js).contains("function approveAndSubmit");
        // submit 가로채기 패턴 코어.
        assertThat(js).contains("confirmApproved");
        assertThat(js).contains("stopImmediatePropagation");
        // resource-label / extra 합성.
        assertThat(js).contains("data-resource-label");
        assertThat(js).contains("data-resource-extra");
    }

    @Test
    @DisplayName("J2 : 4 specialized JS 가 각자 자기 boolean 마커 selector 사용 (primitive obsession 회피)")
    void jsSpecialized_useBooleanMarkers() throws Exception {
        // 각 모듈이 자기 form 마커만 select. actionKey 문자열 분기 없음.
        assertThat(read(STATIC.resolve("management/common/confirm-soft-delete.js")))
                .contains("data-confirm-soft-delete");
        assertThat(read(STATIC.resolve("management/common/confirm-deprecate.js")))
                .contains("data-confirm-deprecate");
        assertThat(read(STATIC.resolve("management/common/confirm-restore.js")))
                .contains("data-confirm-restore");
        assertThat(read(STATIC.resolve("management/common/confirm-purge.js")))
                .contains("data-confirm-purge");
    }

    @Test
    @DisplayName("J3 : confirm-purge.js 가 lazy-load 흐름 — openLazy 사용 + data-modal-* 셀렉터 + hidden typedName")
    void jsPurge_typedNameValidation() throws Exception {
        String js = read(STATIC.resolve("management/common/confirm-purge.js"));
        // S5-6-1 lazy-load : openLazy + form 의 resource-type/id + modal 내 expected/typed-input.
        assertThat(js).contains("startDisabled: true");
        assertThat(js).contains("ConfirmModal.openLazy");
        assertThat(js).contains("data-resource-type");
        assertThat(js).contains("data-resource-id");
        assertThat(js).contains("/ui/confirm-modal/PURGE");
        assertThat(js).contains("data-modal-typed-input");
        assertThat(js).contains("data-modal-expected");
        assertThat(js).contains("input[name=\"typedName\"]");
    }

    @Test
    @DisplayName("J4 : confirm-restore.js 가 data-cascade-true-title 유무로 라디오 노출 분기 + hidden cascade input 작성")
    void jsRestore_cascadeWiring() throws Exception {
        String js = read(STATIC.resolve("management/common/confirm-restore.js"));
        assertThat(js).contains("data-cascade-true-title");
        assertThat(js).contains("input[name=\"cascade\"]");
    }

    // ==== L — 호출측 페이지가 fragment include + boolean 마커 사용 ========================

    private void assertPageUsesNewModalInfra(String relPath) throws Exception {
        String html = read(TEMPLATES.resolve(relPath));
        // S5-6-1 / S5-6-2 — confirm-modals 의 3 variant (`all`, `nonPurge`, `lazyShell`) 모두 허용.
        boolean hasInclude = html.contains("confirm-modals :: all")
                || html.contains("confirm-modals :: nonPurge")
                || html.contains("confirm-modals :: lazyShell");
        assertThat(hasInclude)
                .as(relPath + " — confirm-modals 통합 fragment (all / nonPurge / lazyShell) include 누락")
                .isTrue();
    }

    @Test
    @DisplayName("L1 : 5 list + miller fragment + trash + reconciliation 모두 confirm-modals (all / nonPurge / lazyShell) include")
    void allCallerPagesIncludeModals() throws Exception {
        // miller fragment 자체는 form 만 보유 — modal 인프라는 부모 list (subprogram/list.html) 가 가짐.
        String[] pagesWithModalInfra = {
                "management/os/list.html",
                "management/board/list.html",
                "management/bios/list.html",
                "management/bmc/list.html",
                "management/subprogram/list.html",
                "maintenance/trash/list.html",
                "maintenance/reconciliation/list.html"
        };
        for (String p : pagesWithModalInfra) assertPageUsesNewModalInfra(p);
    }

    @Test
    @DisplayName("L2 : 5 list + miller + trash + reconciliation 의 form 이 boolean 마커 (data-confirm-{action}) 사용")
    void callersUseBooleanMarkers() throws Exception {
        // 4 marker 중 최소 하나라도 form 마커로 사용.
        Path[] callers = {
                TEMPLATES.resolve("management/os/list.html"),
                TEMPLATES.resolve("management/board/list.html"),
                TEMPLATES.resolve("management/bios/list.html"),
                TEMPLATES.resolve("management/bmc/list.html"),
                TEMPLATES.resolve("fragments/management/subprogram/miller.html"),
                TEMPLATES.resolve("maintenance/trash/list.html"),
                TEMPLATES.resolve("maintenance/reconciliation/list.html")
        };
        for (Path p : callers) {
            String html = read(p);
            boolean anyMarker =
                    html.contains("data-confirm-soft-delete")
                            || html.contains("data-confirm-deprecate")
                            || html.contains("data-confirm-restore")
                            || html.contains("data-confirm-purge");
            assertThat(anyMarker)
                    .as(p.getFileName() + " — boolean 마커 (data-confirm-{action}) 가 form 에 없음")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("L3 : 호출측 form 은 data-resource-label 로 자원 식별자 전달 (메시지 직접 조립 회피)")
    void callersUseResourceLabelMarker() throws Exception {
        Path[] callers = {
                TEMPLATES.resolve("management/os/list.html"),
                TEMPLATES.resolve("management/board/list.html"),
                TEMPLATES.resolve("management/bios/list.html"),
                TEMPLATES.resolve("management/bmc/list.html"),
                TEMPLATES.resolve("fragments/management/subprogram/miller.html")
        };
        for (Path p : callers) {
            String html = read(p);
            assertThat(html)
                    .as(p.getFileName() + " — data-resource-label 마커 누락")
                    .contains("data-resource-label");
        }
    }

    // ==== R — Regression guards ========================================================

    @Test
    @DisplayName("R1 : 전 templates 의 onsubmit=return confirm 잔존 0")
    void noLegacyOnsubmitConfirm_inAllTemplates() throws Exception {
        java.util.List<Path> files = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(TEMPLATES)) {
            stream.filter(p -> p.toString().endsWith(".html"))
                    .forEach(files::add);
        }
        for (Path p : files) {
            String html = read(p);
            String[] lines = html.split("\n");
            for (String line : lines) {
                if (line.contains("onsubmit=") && line.contains("return confirm")) {
                    org.junit.jupiter.api.Assertions.fail(p + " 에 legacy onsubmit=confirm 잔존 : " + line.trim());
                }
            }
        }
    }

    @Test
    @DisplayName("R2 : 전 templates 의 actionKey 문자열 마커 (data-confirm-action) 잔존 0 — primitive obsession 회피 검증")
    void noLegacyActionKeyMarker() throws Exception {
        java.util.List<Path> files = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(TEMPLATES)) {
            stream.filter(p -> p.toString().endsWith(".html"))
                    .forEach(files::add);
        }
        for (Path p : files) {
            String html = read(p);
            if (html.contains("data-confirm-action=")) {
                org.junit.jupiter.api.Assertions.fail(p + " 에 legacy data-confirm-action 마커 잔존");
            }
        }
    }

    @Test
    @DisplayName("R3 : 기존 confirm-action.{html,js} 파일 제거 검증")
    void legacyFilesRemoved() throws Exception {
        assertThat(TEMPLATES.resolve("fragments/management/confirm-action.html"))
                .doesNotExist();
        assertThat(STATIC.resolve("management/common/confirm-action.js"))
                .doesNotExist();
    }
}
