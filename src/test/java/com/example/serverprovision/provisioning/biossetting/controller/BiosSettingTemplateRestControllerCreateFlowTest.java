package com.example.serverprovision.provisioning.biossetting.controller;

import com.example.serverprovision.provisioning.biossetting.dto.response.BiosSettingTemplateSummaryResponse;
import com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateNotFoundException;
import com.example.serverprovision.provisioning.biossetting.exception.DuplicateBiosSettingTemplateNameException;
import com.example.serverprovision.provisioning.biossetting.service.BiosSettingTemplateCommandService;
import com.example.serverprovision.provisioning.domain.vo.BiosAttributeName;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.provisioning.exception.BiosBoardNotFoundException;
import com.example.serverprovision.provisioning.exception.InvalidBiosValueException;
import com.example.serverprovision.provisioning.exception.UnknownBiosAttributeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U2-2-1 CP4 — {@link BiosSettingTemplateRestController} 생성 플로우 통합 테스트.
 * Mocking 은 Service 까지만 — Bean Validation·advice 매핑(400 FieldBound / 404 / 409)이 실제 실행된다.
 * (검증·coerce 루프 자체는 {@code BiosSettingTemplateCommandServiceTest} 단위가 실 registry 로 커버)
 */
@WebMvcTest(controllers = BiosSettingTemplateRestController.class)
class BiosSettingTemplateRestControllerCreateFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean BiosSettingTemplateCommandService commandService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String VALID_BODY = """
            {"name": "Rocky9 표준", "description": "웹서버용", "boardModelId": 2,
             "attributes": {"BirchStream0064_PowerPerformanceTuning": "OS Controls EPB"}}
            """;

    @Test
    @DisplayName("POST — 생성 201 + Location + 바디 필드")
    void create_returns201() throws Exception {
        given(commandService.create(any())).willReturn(new BiosSettingTemplateSummaryResponse(
                7L, "Rocky9 표준", "웹서버용", 2L, "MS74-HB0", 1, false, LocalDateTime.now()));

        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/provisioning/bios-setting/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.boardModelName").value("MS74-HB0"))
                .andExpect(jsonPath("$.attributeCount").value(1));
    }

    @Test
    @DisplayName("POST — name blank → 400 + fieldErrors[name] (Bean Validation)")
    void create_blankName_returns400() throws Exception {
        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"boardModelId\": 2, \"attributes\": {\"A\": \"v\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("POST — 빈 diff → InvalidBiosValue(FieldBound) 400 + fieldErrors[attributes]")
    void create_emptyDiff_returns400() throws Exception {
        given(commandService.create(any())).willThrow(InvalidBiosValueException.emptyDiff());

        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Rocky9 표준\", \"boardModelId\": 2, \"attributes\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'attributes')]").exists());
    }

    @Test
    @DisplayName("POST — PASSWORD 속성(비-템플릿 타입) → 400 + fieldErrors[해당 AttributeName] (direct POST 안전망)")
    void create_notTemplatable_returns400_fieldBound() throws Exception {
        given(commandService.create(any())).willThrow(
                InvalidBiosValueException.notTemplatable(BiosAttributeName.of("SETUP001_AdministratorPassword")));

        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'SETUP001_AdministratorPassword')]").exists());
    }

    @Test
    @DisplayName("POST — registry 에 없는 속성 → UnknownBiosAttribute 404 (기존 계약 재사용)")
    void create_unknownAttribute_returns404() throws Exception {
        given(commandService.create(any())).willThrow(
                new UnknownBiosAttributeException(BiosAttributeName.of("GHOST_Attr")));

        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST — 없는/삭제된 boardModelId → BoardModelNotFound 404 · 카탈로그 미보유 → BiosBoardNotFound 404")
    void create_unknownBoard_returns404() throws Exception {
        given(commandService.create(any())).willThrow(new BoardModelNotFoundException(99L));

        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());

        // 카탈로그 미보유 보드(UI disabled 의 direct POST 안전망) — 로더의 기존 404 재사용.
        // 재스터빙: 이미 throw 로 스터빙된 mock 은 given(mock.call()) 호출 자체가 던지므로 will-먼저 형태를 쓴다.
        willThrow(new BiosBoardNotFoundException("RX1330M6")).given(commandService).create(any());
        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST — name 전역 중복 → Duplicate 409 (신규 예외 시나리오 의무)")
    void create_duplicateName_returns409() throws Exception {
        given(commandService.create(any())).willThrow(new DuplicateBiosSettingTemplateNameException("Rocky9 표준"));

        mvc.perform(post("/provisioning/bios-setting")
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    /* ─────────────────────────── U2-2-2 — PUT / DELETE ─────────────────────────── */

    private static final String VALID_UPDATE_BODY = """
            {"name": "Rocky9 표준 v2", "description": "개정",
             "attributes": {"TCG003": "Enable"}}
            """;

    @Test
    @DisplayName("PUT /{id} — 수정 200 + 바디 (boardKey 는 계약 미포함 — 불변)")
    void update_returns200() throws Exception {
        given(commandService.update(eq(1L), any())).willReturn(new BiosSettingTemplateSummaryResponse(
                1L, "Rocky9 표준 v2", "개정", 6L, "MS73-HB1", 1, false, LocalDateTime.now()));

        mvc.perform(put("/provisioning/bios-setting/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rocky9 표준 v2"));
    }

    @Test
    @DisplayName("PUT — name blank → 400 + fieldErrors[name]")
    void update_blankName_returns400() throws Exception {
        mvc.perform(put("/provisioning/bios-setting/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"attributes\": {\"A\": \"v\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("PUT — 없는 id → 404 · 타 행 name 중복 → 409 (신규 예외 4경로 중 REST 2경로)")
    void update_notFoundAndDuplicate() throws Exception {
        given(commandService.update(eq(99L), any())).willThrow(new BiosSettingTemplateNotFoundException(99L));
        given(commandService.update(eq(2L), any())).willThrow(new DuplicateBiosSettingTemplateNameException("Rocky9 표준 v2"));

        mvc.perform(put("/provisioning/bios-setting/{id}", 99L)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY))
                .andExpect(status().isNotFound());
        mvc.perform(put("/provisioning/bios-setting/{id}", 2L)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(VALID_UPDATE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE — 정의서 사용 중 → 409 (U2-2-3 안전망, 신규 예외 시나리오)")
    void delete_inUse_returns409() throws Exception {
        org.mockito.BDDMockito.willThrow(
                new com.example.serverprovision.provisioning.biossetting.exception.BiosSettingTemplateInUseException(1L))
                .given(commandService).delete(1L);

        mvc.perform(delete("/provisioning/bios-setting/{id}", 1L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /{id} — 204 · 없는 id → 404")
    void delete_returns204_and404() throws Exception {
        willThrow(new BiosSettingTemplateNotFoundException(99L)).given(commandService).delete(99L);

        mvc.perform(delete("/provisioning/bios-setting/{id}", 1L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/provisioning/bios-setting/{id}", 99L).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
