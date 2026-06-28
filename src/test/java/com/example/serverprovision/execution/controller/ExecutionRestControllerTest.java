package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.service.GuestServerRegistrationService;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U1 CP4 — {@link ExecutionRestController} 통합 테스트 (iPXE 등록 진입점).
 * 등록 write-set 자체는 service 단위 테스트가 검증하고, 여기서는 advice 의 HTTP status 매핑(200/404/400)을 검증한다.
 */
@WebMvcTest(controllers = ExecutionRestController.class)
class ExecutionRestControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerRegistrationService registrationService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /api/pxe/v1/boot — 등록 성공 200")
    void boot_success_returns200() throws Exception {
        // registrationService.initialRegistry 는 void — 기본 mock(do nothing).
        mvc.perform(get("/api/pxe/v1/boot")
                        .param("macAddress", "aa:bb:cc:dd:ee:ff")
                        .param("ipAddress", "10.20.3.11")
                        .param("systemUUID", "11111111-1111-1111-1111-111111111111")
                        .param("vendor", "Giga Computing")
                        .param("boardModel", "MS73-HB1-000"))
                .andExpect(status().isOk());

        verify(registrationService).initialRegistry(any(BootIPXEInfoRequest.class));
    }

    @Test
    @DisplayName("GET /api/pxe/v1/boot — 미등록 보드 모델 → 404 (advice)")
    void boot_unknownBoard_returns404() throws Exception {
        willThrow(new BoardModelNotFoundException(Vendor.GIGABYTE, "NOPE"))
                .given(registrationService).initialRegistry(any());

        mvc.perform(get("/api/pxe/v1/boot")
                        .param("macAddress", "aa:bb:cc:dd:ee:ff")
                        .param("ipAddress", "10.20.3.11")
                        .param("systemUUID", "11111111-1111-1111-1111-111111111111")
                        .param("vendor", "Giga Computing")
                        .param("boardModel", "NOPE"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/pxe/v1/boot — 형식 오류(IllegalArgument) → 400 (advice §D10)")
    void boot_malformed_returns400() throws Exception {
        willThrow(new IllegalArgumentException("systemUUID 형식이 올바르지 않습니다 : bad"))
                .given(registrationService).initialRegistry(any());

        mvc.perform(get("/api/pxe/v1/boot")
                        .param("macAddress", "aa:bb:cc:dd:ee:ff")
                        .param("ipAddress", "10.20.3.11")
                        .param("systemUUID", "bad")
                        .param("vendor", "Giga Computing")
                        .param("boardModel", "MS73-HB1-000"))
                .andExpect(status().isBadRequest());
    }
}
