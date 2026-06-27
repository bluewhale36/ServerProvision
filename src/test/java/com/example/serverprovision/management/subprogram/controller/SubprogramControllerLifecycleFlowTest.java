package com.example.serverprovision.management.subprogram.controller;

import com.example.serverprovision.global.exception.ChildLifecycleBlockedByParentException;
import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.IntegrityStatus;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService;
import com.example.serverprovision.management.common.filesystem.service.DirectoryBrowseService;
import com.example.serverprovision.management.subprogram.dto.response.BoardWithSubprogramListResponse;
import com.example.serverprovision.management.subprogram.dto.response.SubprogramResponse;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.exception.DuplicateSubprogramVersionException;
import com.example.serverprovision.management.subprogram.exception.SubprogramNotFoundException;
import com.example.serverprovision.management.subprogram.service.SubprogramService;
import com.example.serverprovision.management.subprogram.service.SubprogramUploadIntentService;
import com.example.serverprovision.management.subprogram.service.SubprogramVerificationLauncher;
import com.example.serverprovision.management.subprogram.vo.BoardScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2-2-1 — Subprogram lifecycle UI 1차 차단의 HTTP 계층 검증.
 *
 * <p>정상 흐름(성공 redirect) + UI 우회(direct POST) 시 서버 가드가 409 안전망으로 작동하는지,
 * capability 플래그가 응답·렌더에 정확히 실리는지 확인한다. 가드 본체 분기는 {@code SubprogramServiceTest}.</p>
 */
@WebMvcTest(controllers = SubprogramController.class)
@DisplayName("R2-2-1 — Subprogram lifecycle UI 1차 차단 + 서버 가드 안전망 (사용자 액션 통합)")
class SubprogramControllerLifecycleFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean SubprogramService subprogramService;
    @MockitoBean SubprogramUploadIntentService subprogramUploadIntentService;
    @MockitoBean com.example.serverprovision.management.subprogram.service.SubprogramNudgeService subprogramNudgeService;
    @MockitoBean SubprogramVerificationLauncher subprogramVerificationLauncher;
    @MockitoBean BoardModelMetadataService boardModelService;
    @MockitoBean DirectoryBrowseService directoryBrowseService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private ChildLifecycleBlockedByParentException parentBlocked(String state, String action) {
        return new ChildLifecycleBlockedByParentException(
                ResourceType.BOARD_MODEL, 10L, state,
                ResourceType.SUBPROGRAM, 5L, action, "GIGABYTE MS03-CE0");
    }

    private SubprogramResponse deletedSp(boolean parentBlocksRestore) {
        return new SubprogramResponse(
                5L, SubprogramKind.DRIVER, "드라이버", 10L, "a", "1.0", "/p", null, "h",
                1, 100L, "d", IntegrityStatus.NOT_VERIFIED,
                false, true, false, LifecycleStage.SOFT_DELETED,
                false, false, parentBlocksRestore);
    }

    /* ─────────────────── 성공 경로 (redirect) ─────────────────── */

    @Test
    @DisplayName("POST /toggle (성공) : 302 redirect")
    void toggle_success() throws Exception {
        mvc.perform(post("/management/subprogram/5/toggle"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /undeprecate (성공) : 302 redirect")
    void undeprecate_success() throws Exception {
        mvc.perform(post("/management/subprogram/5/undeprecate"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /restore (성공) : 302 redirect")
    void restore_success() throws Exception {
        mvc.perform(post("/management/subprogram/5/restore"))
                .andExpect(status().is3xxRedirection());
    }

    /* ─────────────────── 부모 가드 우회(direct-POST) → 409 안전망 ─────────────────── */

    @Test
    @DisplayName("POST /toggle : 부모 DISABLED 우회 → 409 ChildLifecycleBlockedByParent")
    void toggle_parentBlocked_409() throws Exception {
        willThrow(parentBlocked("DISABLED", "enable")).given(subprogramService).toggleEnabled(5L);
        mvc.perform(post("/management/subprogram/5/toggle").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("부모")));
    }

    @Test
    @DisplayName("POST /undeprecate : 부모 DEPRECATED 우회 → 409")
    void undeprecate_parentBlocked_409() throws Exception {
        willThrow(parentBlocked("DEPRECATED", "undeprecate")).given(subprogramService).undeprecate(5L);
        mvc.perform(post("/management/subprogram/5/undeprecate").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /restore : 부모 DELETED 우회 → 409")
    void restore_parentBlocked_409() throws Exception {
        willThrow(parentBlocked("DELETED", "restore")).given(subprogramService).restore(5L);
        mvc.perform(post("/management/subprogram/5/restore").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /restore : 동일 활성 키 충돌 → 409 (기존 가드 회귀, R2-2-2 대상)")
    void restore_duplicateKey_409() throws Exception {
        willThrow(new DuplicateSubprogramVersionException(
                SubprogramKind.DRIVER, BoardScope.COMMON, "a", "1.0"))
                .given(subprogramService).restore(5L);
        mvc.perform(post("/management/subprogram/5/restore").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    /* ─────────────────── 404 ─────────────────── */

    @Test
    @DisplayName("POST /toggle : 존재하지 않는 자원 → 404")
    void toggle_notFound_404() throws Exception {
        willThrow(new SubprogramNotFoundException(99L)).given(subprogramService).toggleEnabled(99L);
        mvc.perform(post("/management/subprogram/99/toggle").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    /* ─────────────────── capability 직렬화 ─────────────────── */

    @Test
    @DisplayName("GET /{id} : capability(parentBlocksRestore) 직렬화 노출")
    void detail_capability_serialized() throws Exception {
        given(subprogramService.findSubprogram(5L)).willReturn(deletedSp(true));
        mvc.perform(get("/management/subprogram/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentBlocksRestore").value(true))
                .andExpect(jsonPath("$.parentBlocksEnable").value(false));
    }

    /* ─────────────────── UI 1차 차단 렌더 ─────────────────── */

    @Test
    @DisplayName("GET /list : 부모 삭제 자식은 복구 버튼 disabled + tooltip 렌더")
    void list_renders_disabledRestore_withTooltip() throws Exception {
        given(subprogramService.findAllGrouped(SubprogramKind.DRIVER, true))
                .willReturn(List.of(new BoardWithSubprogramListResponse(
                        10L, Vendor.GIGABYTE, "GIGABYTE", "MS03-CE0", false, List.of(deletedSp(true)))));
        given(subprogramService.findAllGrouped(SubprogramKind.UTILITY, true)).willReturn(List.of());

        mvc.perform(get("/management/subprogram").param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("n-btn-tooltip-wrap")))
                .andExpect(content().string(containsString("부모 메인보드가 삭제 상태입니다")));
    }
}
