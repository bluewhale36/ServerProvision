package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.engine.windows.WindowsInstallBundle;
import com.example.serverprovision.execution.engine.windows.WindowsInstallTokenRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E4-1-a-3 CP4 — 토큰 번들 서빙. 성공(파일 5종 · Range) · 404(위조 · 회수 · 목록 밖 · 경로 조작 · 파일 소실) · 400(토큰 형식).
 * 위조와 만료를 같은 404 로 다뤄 존재 여부를 흘리지 않는 것이 {@code FirmwareImageRestController} 와 같은 관례다.
 */
@WebMvcTest(WindowsInstallAssetRestController.class)
class WindowsInstallAssetRestControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean WindowsInstallTokenRegistry tokenRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @TempDir Path tempDir;

    private final UUID token = UUID.randomUUID();

    @BeforeEach
    void bundle() throws IOException {
        Path wimboot = Files.writeString(tempDir.resolve("wimboot"), "WIMBOOT-BYTES");
        Path bootWim = Files.writeString(tempDir.resolve("boot.wim"), "0123456789ABCDEF");
        given(tokenRegistry.resolve(token)).willReturn(Optional.of(new WindowsInstallBundle(
                wimboot, bootWim, "[LaunchApps]\r\ncmd.exe, /k X:\\install.bat\r\n",
                "@echo off\nnet use N: \\\\h\\s /user:deploy \"pw\"\n",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><unattend/>")));
    }

    @Test
    @DisplayName("200 — 정적 둘은 디스크 스트리밍(octet-stream), 렌더본 셋은 메모리(text/plain US-ASCII · application/xml)")
    void serve_fiveFiles() throws Exception {
        mvc.perform(get("/api/pxe/v1/windows/{token}/wimboot", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().string("WIMBOOT-BYTES"));
        mvc.perform(get("/api/pxe/v1/windows/{token}/boot.wim", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Length", "16"));
        mvc.perform(get("/api/pxe/v1/windows/{token}/winpeshl.ini", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=US-ASCII"));
        mvc.perform(get("/api/pxe/v1/windows/{token}/install.bat", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=US-ASCII"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("net use N:")));
        mvc.perform(get("/api/pxe/v1/windows/{token}/autounattend.xml", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/xml;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<unattend/>")));
    }

    @Test
    @DisplayName("206 — boot.wim 은 Range 요청에 부분 응답(수백 MB 전송이 끊겨도 wimboot 가 이어받는다)")
    void serve_bootWimRange() throws Exception {
        mvc.perform(get("/api/pxe/v1/windows/{token}/boot.wim", token).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-3/16"))
                .andExpect(content().string("0123"));
    }

    @Test
    @DisplayName("404 — 위조 · 회수된 토큰 (같은 응답 — 존재 여부를 흘리지 않는다)")
    void notFound_forgedOrRevoked() throws Exception {
        given(tokenRegistry.resolve(any())).willReturn(Optional.empty());
        mvc.perform(get("/api/pxe/v1/windows/{token}/wimboot", UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/windows/{token}/install.bat", token)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404 — 목록 밖 파일명(install.wim · 소스의 다른 파일) · 경로 조작 세그먼트는 살아 있는 토큰으로도 열리지 않는다")
    void notFound_outsideList_andTraversal() throws Exception {
        mvc.perform(get("/api/pxe/v1/windows/{token}/install.wim", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/windows/{token}/{file}", token, "..%2Fapplication.properties")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/pxe/v1/windows/{token}/{file}", token, "WIMBOOT")).andExpect(status().isNotFound());   // 대소문자 정확 일치
    }

    @Test
    @DisplayName("404 — 토큰은 살아 있으나 정적 파일이 사라졌다(소스 교체 중) · 500 으로 새지 않는다")
    void notFound_missingStaticFile() throws Exception {
        given(tokenRegistry.resolve(token)).willReturn(Optional.of(new WindowsInstallBundle(
                tempDir.resolve("gone"), tempDir.resolve("gone.wim"), "i", "b", "x")));
        mvc.perform(get("/api/pxe/v1/windows/{token}/wimboot", token)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("400 계열 — 토큰 자리가 UUID 가 아니면 경로 자체가 성립하지 않는다")
    void malformedToken() throws Exception {
        mvc.perform(get("/api/pxe/v1/windows/{token}/wimboot", "not-a-uuid")).andExpect(status().is4xxClientError());
    }
}
