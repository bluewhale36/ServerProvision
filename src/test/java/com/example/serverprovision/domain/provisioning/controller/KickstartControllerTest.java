package com.example.serverprovision.domain.provisioning.controller;

import com.example.serverprovision.application.setting.model.AbstractSettingProcess;
import com.example.serverprovision.application.setting.model.SettingProcess;
import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.os.model.installation.KickstartContext;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KickstartController.class)
@TestPropertySource(properties = "pxe.server.install-source-url=http://192.168.1.1/rocky9")
class KickstartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KickstartController kickstartController;

    @MockitoBean
    private ServerNodeService serverNodeService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    /**
     * 유효한 ServerNode mock 체인을 구성한다.
     * ServerNode -> ServerSetting -> SettingProcess -> [OSInstallation] -> domain.OSInstallation -> getKickstartScript()
     */
    private ServerNode buildValidNodeMock() {
        // domain OSInstallation mock
        var domainOsInstallation = mock(
                com.example.serverprovision.domain.os.model.installation.OSInstallation.class
        );
        when(domainOsInstallation.getKickstartScript(any(KickstartContext.class)))
                .thenReturn("#version=RHEL9\nurl --url=http://192.168.1.1/rocky9\ntext\nreboot\n");

        // application OSInstallation mock
        var appOsInstallation = mock(
                com.example.serverprovision.application.setting.model.OSInstallation.class
        );
        when(appOsInstallation.getOsInstallation()).thenReturn(domainOsInstallation);

        // SettingProcess with the appOsInstallation
        SettingProcess settingProcess = new SettingProcess(List.of(appOsInstallation));

        // ServerSetting mock
        ServerSetting serverSetting = mock(ServerSetting.class);
        when(serverSetting.getSettingProcess()).thenReturn(settingProcess);

        // ServerNode mock
        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(serverSetting);
        when(node.getHostname()).thenReturn("test-server");
        when(node.getAssignedIp()).thenReturn("10.0.0.1");

        return node;
    }

    @Test
    @DisplayName("GET /pxe/v1/ks/{nodeId}: 유효한 요청 시 200 + text/plain + Kickstart 스크립트")
    void returns200WithScript_whenValid() throws Exception {
        ServerNode node = buildValidNodeMock();
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/ks/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("#version=RHEL9")));
    }

    @Test
    @DisplayName("GET /pxe/v1/ks/{nodeId}: 노드 미존재 시 404")
    void returns404_whenNodeNotFound() throws Exception {
        when(serverNodeService.findNodeById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/pxe/v1/ks/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pxe/v1/ks/{nodeId}: ServerSetting이 null이면 404")
    void returns404_whenServerSettingIsNull() throws Exception {
        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(null);
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/ks/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pxe/v1/ks/{nodeId}: SettingProcess가 null이면 404")
    void returns404_whenSettingProcessIsNull() throws Exception {
        ServerSetting serverSetting = mock(ServerSetting.class);
        when(serverSetting.getSettingProcess()).thenReturn(null);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(serverSetting);
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/ks/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pxe/v1/ks/{nodeId}: 프로세스 목록에 OSInstallation이 없으면 404")
    void returns404_whenProcessListIsEmpty() throws Exception {
        // SettingProcess with a non-OSInstallation type process
        AbstractSettingProcess otherProcess = mock(AbstractSettingProcess.class);
        SettingProcess settingProcess = new SettingProcess(List.of(otherProcess));

        ServerSetting serverSetting = mock(ServerSetting.class);
        when(serverSetting.getSettingProcess()).thenReturn(settingProcess);

        ServerNode node = mock(ServerNode.class);
        when(node.getServerSetting()).thenReturn(serverSetting);
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        mockMvc.perform(get("/pxe/v1/ks/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /pxe/v1/ks/{nodeId}: installSourceUrl이 빈 문자열이면 500")
    void returns500_whenInstallSourceUrlIsBlank() throws Exception {
        ServerNode node = buildValidNodeMock();
        when(serverNodeService.findNodeById(1L)).thenReturn(Optional.of(node));

        // installSourceUrl을 빈 문자열로 덮어씀
        String originalUrl = (String) ReflectionTestUtils.getField(kickstartController, "installSourceUrl");
        ReflectionTestUtils.setField(kickstartController, "installSourceUrl", "");

        try {
            mockMvc.perform(get("/pxe/v1/ks/1"))
                    .andExpect(status().isInternalServerError());
        } finally {
            // 원래 값 복원
            ReflectionTestUtils.setField(kickstartController, "installSourceUrl", originalUrl);
        }
    }
}
