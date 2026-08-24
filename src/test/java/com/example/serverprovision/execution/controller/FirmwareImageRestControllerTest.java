package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.service.FirmwareImageTokenRegistry;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E2-2 D-5 — 펌웨어 파일 서빙. 부르는 쪽이 게스트가 아니라 그 게스트의 <b>BMC</b> 라는 점이 다른 PXE
 * 엔드포인트와 다르다. BMC 는 우리 인증 체계를 모르므로 자격증명을 요구할 수 없고, 그렇다고 자원
 * 트리를 무인증으로 열 수도 없어 <b>일회용 토큰</b>이 인증을 대신한다.
 *
 * <p>위조와 만료를 같은 404 로 다루는 것이 이 시험의 요점이다 — 응답으로 존재 여부를 흘리지 않는다.</p>
 */
@WebMvcTest(FirmwareImageRestController.class)
class FirmwareImageRestControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean FirmwareImageTokenRegistry tokenRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @TempDir Path tempDir;

    @Test
    @DisplayName("200 — 발급된 토큰이면 파일을 그대로 내준다(BMC 가 이 URL 로 당겨 간다)")
    void download_returnsFile() throws Exception {
        Path image = Files.writeString(tempDir.resolve("image.RBU"), "FIRMWARE-BYTES");
        UUID token = UUID.randomUUID();
        given(tokenRegistry.resolve(token)).willReturn(Optional.of(image));

        mvc.perform(get("/api/pxe/v1/firmware/{token}/image.RBU", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(content().string("FIRMWARE-BYTES"));
    }

    @Test
    @DisplayName("404 — 위조 토큰. 존재 여부를 응답으로 흘리지 않는다")
    void download_forgedTokenIsNotFound() throws Exception {
        given(tokenRegistry.resolve(any())).willReturn(Optional.empty());

        mvc.perform(get("/api/pxe/v1/firmware/{token}/image.RBU", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404 — 회수된 토큰도 위조와 같은 응답이다(집행이 끝나면 파일을 닫는다)")
    void download_revokedTokenIsNotFound() throws Exception {
        UUID token = UUID.randomUUID();
        given(tokenRegistry.resolve(token)).willReturn(Optional.empty());

        mvc.perform(get("/api/pxe/v1/firmware/{token}/image.RBU", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404 — 토큰은 살아 있으나 파일이 사라졌다. 500 으로 새지 않는다")
    void download_missingFileIsNotFound() throws Exception {
        UUID token = UUID.randomUUID();
        given(tokenRegistry.resolve(token)).willReturn(Optional.of(tempDir.resolve("gone.RBU")));

        mvc.perform(get("/api/pxe/v1/firmware/{token}/image.RBU", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("400 — 토큰 자리가 UUID 가 아니면 경로 자체가 성립하지 않는다")
    void download_malformedTokenIsBadRequest() throws Exception {
        mvc.perform(get("/api/pxe/v1/firmware/{token}/image.RBU", "not-a-uuid"))
                .andExpect(status().is4xxClientError());
    }
}
