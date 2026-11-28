package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.engine.rhel.RhelInstallMedia;
import com.example.serverprovision.execution.engine.rhel.RhelInstallTokenRegistry;
import com.example.serverprovision.execution.engine.rhel.RhelInstaller;
import com.example.serverprovision.execution.engine.rhel.RhelServing;
import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartDocument;
import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.enums.OSName;
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
 * E4-1-b-4-2 — RHEL 서빙의 HTTP 계층. 성공(kickstart · 트리 파일 · Range) · 404(설치 중 아님 · 경로 조작 · 없는 파일 · 회수 토큰).
 * 무효 토큰의 401 은 게스트 체인이 끊으므로 {@code GuestChainSecurityTest} 가 본다.
 */
@WebMvcTest(RhelInstallAssetRestController.class)
class RhelInstallAssetRestControllerTest {

    private static final String KICKSTART = "#version=RHEL9\nurl --url=http://pxe/api/pxe/v1/rhel/t/tree\n";

    @Autowired MockMvc mvc;
    @MockitoBean RhelInstallTokenRegistry tokenRegistry;
    @MockitoBean RhelInstaller installer;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @TempDir Path tempDir;

    private final UUID token = UUID.randomUUID();

    @BeforeEach
    void serving() throws IOException {
        Path root = FakeRockyTree.writeTree(tempDir.resolve("iso-12"), "Rocky Linux", "9.5");
        Files.writeString(tempDir.resolve("outside.txt"), "secret");
        Files.writeString(root.resolve(".provision.json"), "{}");
        InstallTree tree = new InstallTree(root, new TreeRelease("Rocky Linux", "9.5"), root);
        given(tokenRegistry.resolve(token)).willReturn(Optional.of(new RhelServing(new KickstartDocument(KICKSTART), tree,
                new RhelInstallMedia(12L, OSName.ROCKY_LINUX_9, "9.5", "Rocky Linux 9.5"), new InstallDisk("wwn-0x600508e000000000"))));
    }

    @Test
    @DisplayName("200 — 설치 중이면 kickstart 를 text/plain UTF-8 로 낸다(렌더본 그대로)")
    void kickstart_whileInstalling() throws Exception {
        given(installer.kickstartWhileInstalling(token)).willReturn(Optional.of(new KickstartDocument(KICKSTART)));
        mvc.perform(get("/api/pxe/v1/rhel/{token}/ks.cfg", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string(KICKSTART));
    }

    @Test
    @DisplayName("404 — 설치 중이 아니면(실패로 닫힘 · 회수) kickstart 를 내지 않는다")
    void kickstart_notInstalling() throws Exception {
        given(installer.kickstartWhileInstalling(token)).willReturn(Optional.empty());
        mvc.perform(get("/api/pxe/v1/rhel/{token}/ks.cfg", token)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("200 — 트리 파일(여러 단계 경로 · 트리 설명서)을 낸다")
    void tree_files() throws Exception {
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/images/pxeboot/vmlinuz", token))
                .andExpect(status().isOk())
                .andExpect(content().string("kernel"));
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/BaseOS/repodata/repomd.xml", token))
                .andExpect(status().isOk())
                .andExpect(content().string("<repomd/>"));
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/.treeinfo", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("206 — Range 요청은 Spring 이 부분 응답으로 처리한다")
    void tree_range() throws Exception {
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/images/pxeboot/initrd.img", token).header("Range", "bytes=0-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 0-2/6"))
                .andExpect(content().string("ini"));
    }

    @Test
    @DisplayName("404 — 경로 조작(..) · 숨김 파일(in-tree 마커) · 디렉터리 · 없는 파일")
    void tree_rejects() throws Exception {
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/BaseOS/../../outside.txt", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/.provision.json", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/BaseOS", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/images/missing.img", token)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("404 — 회수된 토큰(발급소가 모름)은 트리도 내지 않는다")
    void tree_revokedToken() throws Exception {
        UUID revoked = UUID.randomUUID();
        given(tokenRegistry.resolve(revoked)).willReturn(Optional.empty());
        mvc.perform(get("/api/pxe/v1/rhel/{token}/tree/images/pxeboot/vmlinuz", revoked)).andExpect(status().isNotFound());
    }
}
