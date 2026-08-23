package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import com.example.serverprovision.global.redfish.RedfishResetType;
import com.example.serverprovision.global.redfish.RedfishTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1.5 CP4 — 전원 제어 XHR 통합. 실패도 결과 타입이라 2xx 이고, 4xx 는 게스트 404 · 요청 형식 400 뿐임을
 * HTTP 계층에서 고정한다(Mockito 는 Service 까지만 — advice 매핑은 실제로 돈다).
 */
@WebMvcTest(controllers = GuestServerPowerRestController.class)
class GuestServerPowerRestControllerTest {

    private static final UUID ID = UUID.fromString("6a3f8a34-0000-0000-0000-000000000001");

    @Autowired MockMvc mvc;
    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean RedfishPowerService powerService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static GuestServerDetailResponse detail(String bmcIp, String boardSerial) {
        return new GuestServerDetailResponse(
                ID, "web-01", "RE2108", "RE2108X", UUID.randomUUID(), "memo",
                GuestServerStatus.REGISTERED, null, LocalDateTime.now(), LocalDateTime.now(),
                null,
                new GuestServerDetailResponse.Inventory(Vendor.GIGABYTE, 3L, "MS73-HB1-000", boardSerial,
                        DiscoveryStage.DIAGNOSTIC_ENRICHED, null, null,
                        bmcIp == null ? null : IpAddressVO.of(bmcIp), null),
                List.of(), null, null, List.of());
    }

    @Test
    @DisplayName("GET /power — 200 + kind · powerState · message, 컨트롤러가 VO 를 풀어 RedfishTarget 으로 넘긴다")
    void state_returns200() throws Exception {
        given(queryService.findDetail(ID)).willReturn(detail("192.168.10.21", "QG260700082"));
        given(powerService.powerState(any())).willReturn(
                PowerControlResult.sent(RedfishPowerState.ON, "현재 전원 상태 : ON"));

        mvc.perform(get("/provisioning/server/{id}/power", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("SENT"))
                .andExpect(jsonPath("$.powerState").value("ON"))
                .andExpect(jsonPath("$.message").value("현재 전원 상태 : ON"));

        ArgumentCaptor<RedfishTarget> captor = ArgumentCaptor.forClass(RedfishTarget.class);
        verify(powerService).powerState(captor.capture());
        assertThat(captor.getValue().bmcIp()).isEqualTo("192.168.10.21");
        assertThat(captor.getValue().boardSerial()).isEqualTo("QG260700082");
    }

    @Test
    @DisplayName("POST /power/reset — 200, FAILED 도 200(결과 타입) · bmcIp 없는 게스트는 UNSUPPORTED 결과를 그대로 나른다")
    void reset_returns200() throws Exception {
        given(queryService.findDetail(ID)).willReturn(detail(null, null));
        given(powerService.reset(any(), eq(RedfishResetType.FORCE_OFF))).willReturn(PowerControlResult.unsupported());

        mvc.perform(post("/provisioning/server/{id}/power/reset", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"resetType\": \"FORCE_OFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("BMC 미검출")));
    }

    @Test
    @DisplayName("POST — PowerCycle 은 400(폴백 전용, OQ1) · resetType 누락 400 — 서비스 미호출")
    void reset_rejectsPowerCycleAndMissing() throws Exception {
        mvc.perform(post("/provisioning/server/{id}/power/reset", ID)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"resetType\": \"POWER_CYCLE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'screenAllowed')]").exists());

        mvc.perform(post("/provisioning/server/{id}/power/reset", ID)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'resetType')]").exists());
        verify(powerService, never()).reset(any(), any());
    }

    @Test
    @DisplayName("없는 게스트 — 404 (GuestServerNotFoundException → advice)")
    void unknownGuest_returns404() throws Exception {
        given(queryService.findDetail(ID)).willThrow(new GuestServerNotFoundException(ID));
        mvc.perform(get("/provisioning/server/{id}/power", ID).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
