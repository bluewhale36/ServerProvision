package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.engine.ubuntu.UbuntuInstallTokenRegistry;
import com.example.serverprovision.execution.engine.ubuntu.UbuntuServing;
import com.example.serverprovision.execution.installtree.FakeUbuntuIso;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ubuntu 서빙의 HTTP 계층. 성공(커널 · initrd · ISO · Range) · 404(숨김 판별 파일 · 경로 조작 · 없는 파일 · 사라진 ISO · 회수 토큰).
 * 무효 토큰의 401 은 게스트 체인이 끊으므로 {@code GuestChainSecurityTest} 가 본다.
 */
@WebMvcTest(UbuntuInstallAssetRestController.class)
class UbuntuInstallAssetRestControllerTest {

    private static final String ISO_BYTES = "ISO9660-fake-content";

    @Autowired MockMvc mvc;
    @MockitoBean UbuntuInstallTokenRegistry tokenRegistry;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @TempDir Path tempDir;

    private final UUID token = UUID.randomUUID();
    private Path iso;

    @BeforeEach
    void serving() throws IOException {
        Path root = FakeUbuntuIso.writeTree(tempDir.resolve("iso-31"), FakeUbuntuIso.SERVER_2404);
        Files.writeString(tempDir.resolve("outside.txt"), "secret");
        iso = Files.writeString(tempDir.resolve("ubuntu-24.04.1-live-server-amd64.iso"), ISO_BYTES);
        InstallTree tree = new InstallTree(root, new TreeRelease("Ubuntu", "24.04.1"), iso);
        given(tokenRegistry.resolve(token)).willReturn(Optional.of(new UbuntuServing(tree)));
    }

    @Test
    @DisplayName("200 — 작업 트리의 커널 · initrd 를 낸다")
    void tree_bootFiles() throws Exception {
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/tree/casper/vmlinuz", token))
                .andExpect(status().isOk())
                .andExpect(content().string("kernel"));
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/tree/casper/initrd", token))
                .andExpect(status().isOk())
                .andExpect(content().string("initrd"));
    }

    @Test
    @DisplayName("200 — install.iso 는 등록 원본 ISO 전체를 낸다")
    void iso_whole() throws Exception {
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/install.iso", token))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", ISO_BYTES.length()))
                .andExpect(content().string(ISO_BYTES));
    }

    @Test
    @DisplayName("206 — ISO 의 Range 요청은 부분 응답이다")
    void iso_range() throws Exception {
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/install.iso", token).header("Range", "bytes=0-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-3/" + ISO_BYTES.length()))
                .andExpect(content().string("ISO9"));
    }

    @Test
    @DisplayName("404 — 판별 파일(.disk/info) · 경로 조작(..) · 디렉터리 · 꺼내지 않은 squashfs")
    void tree_rejects() throws Exception {
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/tree/.disk/info", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/tree/casper/../../outside.txt", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/tree/casper", token)).andExpect(status().isNotFound());
        Files.delete(tempDir.resolve("iso-31/casper/ubuntu-server-minimal.squashfs"));
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/tree/casper/ubuntu-server-minimal.squashfs", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404 — 설치 중 등록 ISO 가 사라지면(휴지통 이동 등) ISO 를 내지 않는다")
    void iso_vanished() throws Exception {
        Files.delete(iso);
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/install.iso", token)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404 — 회수된 토큰(발급소가 모름)은 트리도 ISO 도 내지 않는다")
    void revokedToken() throws Exception {
        UUID revoked = UUID.randomUUID();
        given(tokenRegistry.resolve(revoked)).willReturn(Optional.empty());
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/tree/casper/vmlinuz", revoked)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/ubuntu/{token}/install.iso", revoked)).andExpect(status().isNotFound());
    }
}
