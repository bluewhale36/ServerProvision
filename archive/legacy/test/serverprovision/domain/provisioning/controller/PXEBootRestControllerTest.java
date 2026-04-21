package com.example.serverprovision.domain.provisioning.controller;

import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.provisioning.service.ProvisioningScriptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link PXEBootRestController} WebMvc 슬라이스 테스트.
 */
@WebMvcTest(PXEBootRestController.class)
class PXEBootRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServerNodeService serverNodeService;

    @MockitoBean
    private ProvisioningScriptService provisioningScriptService;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // --- 정상 케이스 ---

    @Test
    @DisplayName("GET /pxe/v1/api/boot?mac=...: 정상 요청 시 200 + text/plain 응답")
    void getBootScript_validMac_returns200TextPlain() throws Exception {
        // given
        ServerNode node = mock(ServerNode.class);
        given(serverNodeService.getOrRegisterNode(eq("AA:BB:CC:DD:EE:FF"), isNull(), isNull()))
                .willReturn(node);
        given(provisioningScriptService.generateIPXEScript(node))
                .willReturn("#!ipxe\nboot\n");

        // when & then
        mockMvc.perform(get("/pxe/v1/api/boot")
                        .param("mac", "AA:BB:CC:DD:EE:FF"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string("#!ipxe\nboot\n"));
    }

    @Test
    @DisplayName("GET /pxe/v1/api/boot: 응답이 #!ipxe 로 시작하는 iPXE 스크립트이다")
    void getBootScript_responseStartsWithIpxeShebang() throws Exception {
        // given
        ServerNode node = mock(ServerNode.class);
        given(serverNodeService.getOrRegisterNode(anyString(), isNull(), isNull()))
                .willReturn(node);
        given(provisioningScriptService.generateIPXEScript(node))
                .willReturn("#!ipxe\nset base-url ...\nkernel ...\nboot\n");

        // when & then
        mockMvc.perform(get("/pxe/v1/api/boot")
                        .param("mac", "11:22:33:44:55:66"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("#!ipxe")));
    }

    // --- mac 파라미터 누락 ---

    @Test
    @DisplayName("GET /pxe/v1/api/boot: mac 파라미터 누락 시 400 응답")
    void getBootScript_missingMac_returns400() throws Exception {
        mockMvc.perform(get("/pxe/v1/api/boot"))
                .andExpect(status().isBadRequest());
    }

    // --- vendor, board-model 선택 파라미터 ---

    @Test
    @DisplayName("GET /pxe/v1/api/boot: vendor, board-model 파라미터가 ServerNodeService로 전달된다")
    void getBootScript_withVendorAndBoardModel_passesToService() throws Exception {
        // given
        ServerNode node = mock(ServerNode.class);
        given(serverNodeService.getOrRegisterNode("AA:BB:CC:DD:EE:FF", "SUPERMICRO", "X11DPH-T"))
                .willReturn(node);
        given(provisioningScriptService.generateIPXEScript(node))
                .willReturn("#!ipxe\nexit\n");

        // when & then
        mockMvc.perform(get("/pxe/v1/api/boot")
                        .param("mac", "AA:BB:CC:DD:EE:FF")
                        .param("vendor", "SUPERMICRO")
                        .param("board-model", "X11DPH-T"))
                .andExpect(status().isOk());

        verify(serverNodeService).getOrRegisterNode("AA:BB:CC:DD:EE:FF", "SUPERMICRO", "X11DPH-T");
    }

    @Test
    @DisplayName("GET /pxe/v1/api/boot: vendor 없이 board-model만 전달 가능하다")
    void getBootScript_onlyBoardModel_passesToService() throws Exception {
        // given
        ServerNode node = mock(ServerNode.class);
        given(serverNodeService.getOrRegisterNode(eq("AA:BB:CC:DD:EE:FF"), isNull(), eq("X11DPH-T")))
                .willReturn(node);
        given(provisioningScriptService.generateIPXEScript(node))
                .willReturn("#!ipxe\nexit\n");

        // when & then
        mockMvc.perform(get("/pxe/v1/api/boot")
                        .param("mac", "AA:BB:CC:DD:EE:FF")
                        .param("board-model", "X11DPH-T"))
                .andExpect(status().isOk());

        verify(serverNodeService).getOrRegisterNode("AA:BB:CC:DD:EE:FF", null, "X11DPH-T");
    }

    // --- ServerNodeService 예외 전파 ---

    @Test
    @DisplayName("GET /pxe/v1/api/boot: ServerNodeService가 예외를 던지면 ServletException으로 전파된다")
    void getBootScript_serviceThrows_propagatesAsServletException() {
        // given
        given(serverNodeService.getOrRegisterNode(anyString(), isNull(), isNull()))
                .willThrow(new RuntimeException("보드 모델을 찾을 수 없습니다"));

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                mockMvc.perform(get("/pxe/v1/api/boot")
                        .param("mac", "AA:BB:CC:DD:EE:FF"))
        ).isInstanceOf(jakarta.servlet.ServletException.class);
    }

    // --- ProvisioningScriptService 위임 검증 ---

    @Test
    @DisplayName("GET /pxe/v1/api/boot: ProvisioningScriptService.generateIPXEScript에 올바른 node가 전달된다")
    void getBootScript_delegatesToProvisioningService() throws Exception {
        // given
        ServerNode node = mock(ServerNode.class);
        given(serverNodeService.getOrRegisterNode(anyString(), isNull(), isNull()))
                .willReturn(node);
        given(provisioningScriptService.generateIPXEScript(node))
                .willReturn("#!ipxe\nboot\n");

        // when
        mockMvc.perform(get("/pxe/v1/api/boot")
                        .param("mac", "11:22:33:44:55:66"))
                .andExpect(status().isOk());

        // then
        verify(provisioningScriptService).generateIPXEScript(node);
    }
}
