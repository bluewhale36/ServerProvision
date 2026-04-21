package com.example.serverprovision.domain.provisioning.controller;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.os.model.installation.InstallScriptFormat;
import com.example.serverprovision.domain.os.model.installation.InstallationContext;
import com.example.serverprovision.domain.os.model.installation.RenderedScript;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link InstallScriptController} Web MVC 슬라이스 테스트.
 *
 * <p>검증 범위:
 * <ul>
 *   <li>주 엔드포인트 {@code /pxe/v1/install/{nodeId}} 의 포맷별 Content-Type (Kickstart/YAML)</li>
 *   <li>Content-Disposition 의 포맷 파일명 파생</li>
 *   <li>nocloud-net 보조 엔드포인트 ({@code /user-data}, {@code /meta-data})</li>
 *   <li>노드·세팅·프로세스 누락 시 404, installSourceUrl 미설정 시 500 구분</li>
 * </ul></p>
 */
@WebMvcTest(InstallScriptController.class)
@TestPropertySource(properties = "pxe.server.install-source-url=http://192.168.1.1/rocky9")
class InstallScriptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstallScriptController installScriptController;

    @MockitoBean
    private ServerNodeService serverNodeService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ───────────────────────────────────────────────────────────
    // Mock 체인 빌더
    // ───────────────────────────────────────────────────────────

    /**
     * RHEL 계열 (Kickstart 포맷) OSInstallation 을 반환하는 ServerNode mock 체인.
     */
    private ServerNode buildRhelNodeMock() {
        var domainOsInstallation = mock(
                com.example.serverprovision.domain.os.model.installation.OSInstallation.class
        );
        when(domainOsInstallation.getInstallScript(any(InstallationContext.class)))
                .thenReturn(new RenderedScript(
                        "#version=RHEL9\nurl --url=http://192.168.1.1/rocky9\ntext\nreboot\n",
                        InstallScriptFormat.KICKSTART
                ));

        var appOsInstallation = mock(
                com.example.serverprovision.application.setting.model.OSInstallation.class
        );
        when(appOsInstallation.getOsInstallation()).thenReturn(domainOsInstallation);

        SettingProcess settingProcess = new SettingProcess(List.of(appOsInstallation));

        ServerSetting serverSetting = mock(ServerSetting.class);
        when(serverSetting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(serverSetting);
        when(node.getHostname()).thenReturn("test-server");
        when(node.getAssignedIp()).thenReturn("10.0.0.1");

        return node;
    }

    /**
     * Ubuntu 계열 (autoinstall YAML 포맷) OSInstallation 을 반환하는 ServerNode mock 체인.
     */
    private ServerNode buildUbuntuNodeMock() {
        var domainOsInstallation = mock(
                com.example.serverprovision.domain.os.model.installation.OSInstallation.class
        );
        when(domainOsInstallation.getInstallScript(any(InstallationContext.class)))
                .thenReturn(new RenderedScript(
                        "#cloud-config\nautoinstall:\n  version: 1\n  identity:\n    hostname: test-server\n",
                        InstallScriptFormat.AUTOINSTALL_YAML
                ));

        var appOsInstallation = mock(
                com.example.serverprovision.application.setting.model.OSInstallation.class
        );
        when(appOsInstallation.getOsInstallation()).thenReturn(domainOsInstallation);

        SettingProcess settingProcess = new SettingProcess(List.of(appOsInstallation));

        ServerSetting serverSetting = mock(ServerSetting.class);
        when(serverSetting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(serverSetting);
        when(node.getHostname()).thenReturn("ubuntu-host");
        when(node.getAssignedIp()).thenReturn("10.0.0.2");

        return node;
    }

    // ───────────────────────────────────────────────────────────
    // 주 엔드포인트 — 포맷별 Content-Type
    // ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}: RHEL OSInstallation → text/plain + Kickstart 본문")
    void returnsKickstart_whenRhelOSInstallation() throws Exception {
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(buildRhelNodeMock()));

        mockMvc.perform(get("/pxe/v1/install/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(header().string("Content-Disposition", containsString("install.ks")))
                .andExpect(content().string(containsString("#version=RHEL9")));
    }

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}: Ubuntu OSInstallation → text/yaml + autoinstall 본문")
    void returnsYaml_whenUbuntuOSInstallation() throws Exception {
        when(serverNodeService.findNodeById(2L)).thenReturn(Optional.of(buildUbuntuNodeMock()));

        mockMvc.perform(get("/pxe/v1/install/2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/yaml"))
                .andExpect(header().string("Content-Disposition", containsString("user-data")))
                .andExpect(content().string(containsString("#cloud-config")));
    }

    // ───────────────────────────────────────────────────────────
    // nocloud-net 보조 엔드포인트
    // ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}/user-data: Ubuntu nocloud-net 별칭으로 YAML 반환")
    void userDataAliasReturnsYaml() throws Exception {
        when(serverNodeService.findNodeById(2L)).thenReturn(Optional.of(buildUbuntuNodeMock()));

        mockMvc.perform(get("/pxe/v1/install/2/user-data"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/yaml"))
                .andExpect(content().string(containsString("#cloud-config")));
    }

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}/meta-data: nocloud-net 최소 meta-data 반환 (instance-id 포함)")
    void metaDataReturnsMinimalYaml() throws Exception {
        ServerNode node = mock(ServerNode.class);
        when(node.getHostname()).thenReturn("ubuntu-host");
        when(serverNodeService.findNodeById(2L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/install/2/meta-data"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/yaml"))
                .andExpect(content().string(containsString("instance-id")))
                .andExpect(content().string(containsString("local-hostname: ubuntu-host")));
    }

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}/meta-data: hostname 이 null 이어도 기본값으로 동작")
    void metaDataWorksWithNullHostname() throws Exception {
        ServerNode node = mock(ServerNode.class);
        when(node.getHostname()).thenReturn(null);
        when(serverNodeService.findNodeById(5L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/install/5/meta-data"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("node-5")));
    }

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}/meta-data: 노드 미존재 시 404")
    void metaDataReturns404WhenNodeNotFound() throws Exception {
        when(serverNodeService.findNodeById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/pxe/v1/install/99/meta-data"))
                .andExpect(status().isNotFound());
    }

    // ───────────────────────────────────────────────────────────
    // 404 — 조회 미스
    // ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}: 노드 미존재 시 404")
    void returns404_whenNodeNotFound() throws Exception {
        when(serverNodeService.findNodeById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/pxe/v1/install/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}: ServerSetting 이 null 이면 404")
    void returns404_whenServerSettingIsNull() throws Exception {
        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(null);
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/install/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}: SettingProcess 가 null 이면 404")
    void returns404_whenSettingProcessIsNull() throws Exception {
        ServerSetting serverSetting = mock(ServerSetting.class);
        when(serverSetting.getSettingProcess()).thenReturn(null);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(serverSetting);
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/install/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}: 프로세스 목록에 OSInstallation 이 없으면 404")
    void returns404_whenNoOSInstallationProcess() throws Exception {
        AbstractSettingProcess otherProcess = mock(AbstractSettingProcess.class);
        SettingProcess settingProcess = new SettingProcess(List.of(otherProcess));

        ServerSetting serverSetting = mock(ServerSetting.class);
        when(serverSetting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(serverSetting);
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/install/1"))
                .andExpect(status().isNotFound());
    }

    // ───────────────────────────────────────────────────────────
    // 500 — 설정 오류 (installSourceUrl 미설정)
    // ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /pxe/v1/install/{nodeId}: installSourceUrl 이 빈 문자열이면 500")
    void returns500_whenInstallSourceUrlIsBlank() throws Exception {
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(buildRhelNodeMock()));

        String originalUrl = (String) ReflectionTestUtils.getField(installScriptController, "installSourceUrl");
        ReflectionTestUtils.setField(installScriptController, "installSourceUrl", "");

        try {
            mockMvc.perform(get("/pxe/v1/install/1"))
                    .andExpect(status().isInternalServerError());
        } finally {
            ReflectionTestUtils.setField(installScriptController, "installSourceUrl", originalUrl);
        }
    }
}
