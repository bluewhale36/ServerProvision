package com.example.serverprovision.management.os.controller;

import com.example.serverprovision.management.common.exception.RestorePathOccupiedException;
import com.example.serverprovision.management.common.exception.RestoreTrashLostException;
import com.example.serverprovision.management.os.service.iso.IsoLifecycleService;
import com.example.serverprovision.management.os.service.metadata.OSMetadataService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HF-1 — ISO restore controller flow 통합 테스트. 멱등 self-heal/정상 복원의 2xx 라우팅과
 * RestoreTrashLost 404→409 전환 + 형제 예외 계층 일관성 + async-submit (Accept JSON) 라우팅을 검증한다.
 *
 * <p>service 단까지만 mocking — controller redirect 와 GlobalExceptionHandler (Web/Api advice) 의
 * polymorphic 매핑은 실제로 실행된다.</p>
 */
@WebMvcTest(controllers = IsoLifecycleController.class)
class IsoLifecycleControllerRestoreFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean OSMetadataService osMetadataService;
    @MockitoBean IsoLifecycleService isoLifecycleService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // controller 는 LifecycleService.restore(Long) default (→ restore(id,false)) 를 호출.
    // mock 은 default 를 가로채므로 단일 인자 restore(Long) 에 직접 stub/throw 한다.

    @Test
    @DisplayName("정상 / self-heal 복원 — 302 redirect (멱등 게이트 통과는 service 내부)")
    void restore_success_returns302() throws Exception {
        // restore(Long) 은 void — default mock 으로 예외 없이 통과.
        mvc.perform(post("/management/os/1/iso/10/restore"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("멱등 재시도 (self-heal) — 다시 눌러도 2xx redirect (예외 미발생)")
    void restore_idempotentRetry_returns302() throws Exception {
        // (원O·trashX) self-heal 은 service 가 조용히 정상 복원 → controller 는 동일 redirect (A-2)
        mvc.perform(post("/management/os/1/iso/10/restore"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("진짜 분실 — RestoreTrashLostException → 409 (A-1, 기존 404 아님)")
    void restore_reallyLost_returns409() throws Exception {
        willThrow(new RestoreTrashLostException("/trash/gone.iso"))
                .given(isoLifecycleService).restore(eq(10L));

        mvc.perform(post("/management/os/1/iso/10/restore"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("진짜 분실 + async-submit (Accept: application/json) — 409 + JSON 라우팅 (native nav error.html 아님)")
    void restore_reallyLost_asyncSubmit_returns409Json() throws Exception {
        willThrow(new RestoreTrashLostException("/trash/gone.iso"))
                .given(isoLifecycleService).restore(eq(10L));

        mvc.perform(post("/management/os/1/iso/10/restore")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(result -> {
                    String ct = result.getResponse().getContentType();
                    org.assertj.core.api.Assertions.assertThat(ct).contains(MediaType.APPLICATION_JSON_VALUE);
                });
    }

    @Test
    @DisplayName("경로 점유 — RestorePathOccupiedException → 409 (형제 계층 일관성)")
    void restore_pathOccupied_returns409() throws Exception {
        willThrow(new RestorePathOccupiedException("/active/original.iso"))
                .given(isoLifecycleService).restore(eq(10L));

        mvc.perform(post("/management/os/1/iso/10/restore"))
                .andExpect(status().isConflict());
    }
}
