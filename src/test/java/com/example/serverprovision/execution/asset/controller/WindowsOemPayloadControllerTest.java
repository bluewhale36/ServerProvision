package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.exception.WindowsOemAssemblyRejectedException;
import com.example.serverprovision.execution.engine.windows.WindowsOemPayloadAssembler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E4-1-a-4 CP4 — [드라이버 페이로드 조립] 액션(D-6)의 HTTP 계층: PRG + flash(n종 · 크기 · 제외) · 409(미설정 · 쓰기 불가) · 500(IO 실패).
 * 조립 자체는 {@code WindowsOemPayloadAssemblerTest}.
 */
@WebMvcTest(controllers = WindowsOemPayloadController.class)
class WindowsOemPayloadControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean WindowsOemPayloadAssembler assembler;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("POST /system/windows-install/oem-sync — 302 대시보드 PRG + flash 'n종 · 크기 · 제외 k건'")
    void sync_redirectsWithFlash() throws Exception {
        given(assembler.sync()).willReturn(new WindowsOemPayloadAssembler.OemAssemblyResult(2, 1, 52_428_800L));

        mvc.perform(post("/system/windows-install/oem-sync"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/asset"))
                .andExpect(flash().attribute("flashMessage", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("드라이버 2종"),
                        org.hamcrest.Matchers.containsString("50.0 MB"),
                        org.hamcrest.Matchers.containsString("제외 1건"))));
    }

    @Test
    @DisplayName("POST — 제외 0 이면 flash 에 '제외' 문구 없음")
    void sync_noExcluded() throws Exception {
        given(assembler.sync()).willReturn(new WindowsOemPayloadAssembler.OemAssemblyResult(3, 0, 1024L));

        mvc.perform(post("/system/windows-install/oem-sync"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashMessage", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("제외"))));
    }

    @Test
    @DisplayName("POST — 소스 미설정 · 쓰기 불가는 409(direct POST 안전망 — 화면은 버튼 disabled 가 1차 차단)")
    void sync_rejected409() throws Exception {
        willThrow(WindowsOemAssemblyRejectedException.of("sources/$OEM$ 에 쓰기 권한이 없습니다")).given(assembler).sync();

        mvc.perform(post("/system/windows-install/oem-sync"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST — 복사 도중 IO 실패(UncheckedIOException)는 어떤 advice 도 삼키지 않고 그대로 새어 나간다(컨테이너 500 · 옛 페이로드 유지)")
    void sync_ioFailurePropagates() {
        willThrow(new UncheckedIOException("$OEM$ 조립 실패", new IOException("disk full"))).given(assembler).sync();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> mvc.perform(post("/system/windows-install/oem-sync")))
                .hasCauseInstanceOf(UncheckedIOException.class);
    }
}
