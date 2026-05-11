package com.example.serverprovision.management.common.confirm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S5-2-1 — confirm-action fragment 의 정적 markup 검증.
 *
 * <p>본 테스트는 fragment 파일과 5 도메인 list.html 의 textual 정합성을 검증한다.
 * 실제 Thymeleaf 렌더링 (`th:id` 등 동적 element id) 은 브라우저 E2E (CP5) 에서 사용자가 검증한다.</p>
 *
 * <p>검증 범위 :</p>
 * <ul>
 *   <li>fragment 파일에 핵심 element id 토큰 (Title / Message / ConfirmBtn / CancelBtn) 이 모두 포함</li>
 *   <li>fragment 가 `confirmActionModal(prefix)` thymeleaf fragment 선언 보유</li>
 *   <li>5 도메인 list.html (OS / Board / BIOS / BMC / Subprogram) 이 모두 fragment include + script 태그 보유</li>
 *   <li>각 도메인이 `data-confirm-action` 속성을 3 actionKey (soft-delete / deprecate / restore) 모두 활용</li>
 * </ul>
 */
class ConfirmActionFragmentRenderTest {

    private static final Path PROJECT_ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path TEMPLATES = PROJECT_ROOT.resolve("src/main/resources/templates");
    private static final Path STATIC = PROJECT_ROOT.resolve("src/main/resources/static");

    private static String read(Path p) throws Exception {
        return Files.readString(p);
    }

    @Test
    @DisplayName("F1 : fragment 파일이 핵심 element id 와 thymeleaf fragment 선언 보유")
    void fragmentFile_hasRequiredTokens() throws Exception {
        String html = read(TEMPLATES.resolve("fragments/management/confirm-action.html"));
        assertThat(html).contains("th:fragment=\"confirmActionModal(prefix)\"");
        // th:id 가 prefix + 접미사로 조합되므로 접미사 부분만 검증.
        assertThat(html).contains("'Modal'");
        assertThat(html).contains("'Backdrop'");
        assertThat(html).contains("'Title'");
        assertThat(html).contains("'Message'");
        assertThat(html).contains("'ConfirmBtn'");
        assertThat(html).contains("'CancelBtn'");
        assertThat(html).contains("'CloseBtn'");
    }

    @Test
    @DisplayName("F2 : JS 모듈에 3 actionKey (soft-delete / deprecate / restore) 모두 정의")
    void jsModule_hasAllActionKeys() throws Exception {
        String js = read(STATIC.resolve("management/common/confirm-action.js"));
        assertThat(js).contains("'soft-delete'");
        assertThat(js).contains("'deprecate'");
        assertThat(js).contains("'restore'");
        // 핵심 메서드 노출 확인.
        assertThat(js).contains("window.ConfirmActionModal");
        assertThat(js).contains("bind");
        // 책임 분리 — 두 번째 submit (requestSubmit) flag 처리 코드 보유.
        assertThat(js).contains("confirmActionApproved");
        assertThat(js).contains("stopImmediatePropagation");
    }

    @Test
    @DisplayName("L1 : OS list 가 fragment include + script tag + 3 actionKey 활용")
    void osList_includesFragmentAndScripts() throws Exception {
        String html = read(TEMPLATES.resolve("management/os/list.html"));
        assertThat(html).contains("confirm-action :: confirmActionModal('confirmAction')");
        assertThat(html).contains("/management/common/confirm-action.js");
        assertThat(html).contains("ConfirmActionModal.bind");
        assertThat(html).contains("data-confirm-action=\"soft-delete\"");
        assertThat(html).contains("data-confirm-action=\"deprecate\"");
        assertThat(html).contains("data-confirm-action=\"restore\"");
    }

    @Test
    @DisplayName("L2 : Board list 가 fragment include + script tag + 3 actionKey 활용")
    void boardList_includesFragmentAndScripts() throws Exception {
        String html = read(TEMPLATES.resolve("management/board/list.html"));
        assertThat(html).contains("confirm-action :: confirmActionModal('confirmAction')");
        assertThat(html).contains("/management/common/confirm-action.js");
        assertThat(html).contains("ConfirmActionModal.bind");
        assertThat(html).contains("data-confirm-action=\"soft-delete\"");
        assertThat(html).contains("data-confirm-action=\"deprecate\"");
        assertThat(html).contains("data-confirm-action=\"restore\"");
    }

    @Test
    @DisplayName("L3 : BIOS list 가 fragment include + script tag + 3 actionKey 활용")
    void biosList_includesFragmentAndScripts() throws Exception {
        String html = read(TEMPLATES.resolve("management/bios/list.html"));
        assertThat(html).contains("confirm-action :: confirmActionModal('confirmAction')");
        assertThat(html).contains("/management/common/confirm-action.js");
        assertThat(html).contains("ConfirmActionModal.bind");
        assertThat(html).contains("data-confirm-action=\"soft-delete\"");
        assertThat(html).contains("data-confirm-action=\"deprecate\"");
        assertThat(html).contains("data-confirm-action=\"restore\"");
    }

    @Test
    @DisplayName("L4 : BMC list 가 fragment include + script tag + 3 actionKey 활용")
    void bmcList_includesFragmentAndScripts() throws Exception {
        String html = read(TEMPLATES.resolve("management/bmc/list.html"));
        assertThat(html).contains("confirm-action :: confirmActionModal('confirmAction')");
        assertThat(html).contains("/management/common/confirm-action.js");
        assertThat(html).contains("ConfirmActionModal.bind");
        assertThat(html).contains("data-confirm-action=\"soft-delete\"");
        assertThat(html).contains("data-confirm-action=\"deprecate\"");
        assertThat(html).contains("data-confirm-action=\"restore\"");
    }

    @Test
    @DisplayName("L5 : Subprogram list + miller fragment 가 modal include + 3 actionKey 분산")
    void subprogramList_includesFragmentAndScripts() throws Exception {
        // list.html 가 modal 포함 + script 로드.
        String listHtml = read(TEMPLATES.resolve("management/subprogram/list.html"));
        assertThat(listHtml).contains("confirm-action :: confirmActionModal('confirmAction')");
        assertThat(listHtml).contains("/management/common/confirm-action.js");
        assertThat(listHtml).contains("ConfirmActionModal.bind");

        // 실제 form 들은 miller fragment 에 있으므로 거기서 data-confirm-action 검증.
        String millerHtml = read(TEMPLATES.resolve("fragments/management/subprogram/miller.html"));
        assertThat(millerHtml).contains("data-confirm-action=\"soft-delete\"");
        assertThat(millerHtml).contains("data-confirm-action=\"deprecate\"");
        assertThat(millerHtml).contains("data-confirm-action=\"restore\"");
    }

    @Test
    @DisplayName("R1 : confirm-action.js 가 delete-reject-modal.js 보다 먼저 로드 (capture phase 등록 순서)")
    void scriptLoadOrder_confirmActionBeforeDeleteReject() throws Exception {
        // confirm-action 과 delete-reject 모두 가진 4 도메인 list.html 검증 (Board 는 mk3-2-delete-form 미사용).
        String[] paths = {
                "management/os/list.html",
                "management/bios/list.html",
                "management/bmc/list.html",
                "management/subprogram/list.html"
        };
        for (String relPath : paths) {
            String html = read(TEMPLATES.resolve(relPath));
            int confirmIdx = html.indexOf("/management/common/confirm-action.js");
            int rejectIdx = html.indexOf("/management/common/delete-reject-modal.js");
            assertThat(confirmIdx).as(relPath + " 의 confirm-action.js 위치").isGreaterThan(0);
            assertThat(rejectIdx).as(relPath + " 의 delete-reject-modal.js 위치").isGreaterThan(0);
            assertThat(confirmIdx)
                    .as(relPath + " — confirm-action.js 가 delete-reject-modal.js 보다 먼저 와야 함")
                    .isLessThan(rejectIdx);
        }
    }

    @Test
    @DisplayName("L6 : S5-2-1 범위 (soft-delete / deprecate / restore) 의 onsubmit=return confirm 호출 완전 제거")
    void noLegacyOnsubmitConfirm_inScope() throws Exception {
        Path[] files = {
                TEMPLATES.resolve("management/os/list.html"),
                TEMPLATES.resolve("management/board/list.html"),
                TEMPLATES.resolve("management/bios/list.html"),
                TEMPLATES.resolve("management/bmc/list.html"),
                TEMPLATES.resolve("fragments/management/subprogram/miller.html")
        };
        for (Path p : files) {
            String html = read(p);
            // S5-2-1 범위 — soft-delete / deprecate / restore confirm 만 제거. purge (영구 삭제) 는 S5-2-2 잠정 유지.
            // 위반 신호 : "return confirm" 호출의 메시지에 "영구 삭제" 가 없으면 (= purge 가 아니면) S5-2-1 미적용.
            String[] lines = html.split("\n");
            for (String line : lines) {
                if (line.contains("onsubmit=") && line.contains("return confirm") && !line.contains("영구 삭제")) {
                    org.junit.jupiter.api.Assertions.fail(p + " 에 S5-2-1 미적용 confirm 잔존 : " + line.trim());
                }
            }
        }
    }
}
