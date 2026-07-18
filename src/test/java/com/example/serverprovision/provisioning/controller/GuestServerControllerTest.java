package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * U1 CP4 — {@link GuestServerController} 통합 테스트 (목록 / 상세 / 인라인수정 / 회수).
 * Mocking 은 execution application service 단까지만 — controller 의 redirect/view 선택 + BindingResult 인라인 +
 * {@code @ControllerAdvice} 예외 매핑은 실제로 실행된다.
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private GuestServerSummaryResponse summary(UUID id) {
        return new GuestServerSummaryResponse(
                id, "web-01", UUID.randomUUID(), Vendor.GIGABYTE, "MS73-HB1-000",
                GuestServerStatus.REGISTERED, IpAddressVO.of("10.20.3.11"), LocalDateTime.now());
    }

    private GuestServerDetailResponse detail(UUID id) {
        return new GuestServerDetailResponse(
                id, "web-01", "RE2108", "RE2108X", UUID.randomUUID(), "memo",
                GuestServerStatus.REGISTERED, null, LocalDateTime.now(), LocalDateTime.now(),
                new GuestServerDetailResponse.Inventory(Vendor.GIGABYTE, "MS73-HB1-000", "GB-001", DiscoveryStage.IPXE_REGISTERED),
                List.of(),
                new GuestServerDetailResponse.Progress(
                        ProvisioningPhase.BOOTSTRAPPING, LocalDateTime.now(), null,
                        null, null, null, null, true),   // E1-0a — 미개시(startable) 기본 fixture
                List.of());
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("GET /provisioning/server — 목록 200 + list 뷰")
    void list_returns200() throws Exception {
        given(queryService.findAll()).willReturn(List.of(summary(UUID.randomUUID())));

        mvc.perform(get("/provisioning/server"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-list"))
                .andExpect(model().attributeExists("servers"));
    }

    @Test
    @DisplayName("GET /provisioning/server/{id} — 상세 200 + detail 뷰 + updateForm")
    void detail_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeExists("server", "updateForm"));
    }

    @Test
    @DisplayName("POST /{id}/edit — 수정 성공 302 redirect")
    void edit_success_redirects() throws Exception {
        UUID id = UUID.randomUUID();
        given(commandService.isNameTakenByOther(eq(id), any())).willReturn(false);
        given(commandService.isSerialTakenByOther(eq(id), any())).willReturn(false);

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("name", "web-01")
                        .param("modelName", "RE2108")
                        .param("serialNumber", "RE2108X")
                        .param("memo", "메모"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server/" + id));
    }

    @Test
    @DisplayName("POST /{id}/decommission — 회수 302 redirect")
    void decommission_redirects() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/provisioning/server/{id}/decommission", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server/" + id));
    }

    // ==== 400 / 검증 실패 (재렌더) ====================================

    @Test
    @DisplayName("POST /{id}/edit — name 128자 초과(@Size) → 폼 재렌더(200) + name 필드 에러")
    void edit_sizeViolation_rerenders() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("name", "a".repeat(129)))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeHasFieldErrors("updateForm", "name"));
    }

    @Test
    @DisplayName("POST /{id}/edit — 이름 중복 → 폼 재렌더(200) + name 필드 에러 (예외 아님)")
    void edit_duplicateName_rerenders() throws Exception {
        UUID id = UUID.randomUUID();
        given(commandService.isNameTakenByOther(eq(id), eq("dup"))).willReturn(true);
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("name", "dup"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeHasFieldErrors("updateForm", "name"));
    }

    @Test
    @DisplayName("POST /{id}/edit — 사내 시리얼 중복 → 폼 재렌더(200) + serialNumber 필드 에러")
    void edit_duplicateSerial_rerenders() throws Exception {
        UUID id = UUID.randomUUID();
        given(commandService.isSerialTakenByOther(eq(id), eq("S1"))).willReturn(true);
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("serialNumber", "S1"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeHasFieldErrors("updateForm", "serialNumber"));
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("GET /provisioning/server/{id} — 없는 id → GuestServerNotFound 404 (advice)")
    void detail_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new GuestServerNotFoundException(id)).given(queryService).findDetail(id);

        mvc.perform(get("/provisioning/server/{id}", id).accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }
}
