package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.service.GuestServerStreamService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S7 CP4 — {@link GuestServerStreamController} 통합 테스트. 실 {@link GuestServerStreamService} 를
 * 물려 구독(async·text/event-stream)과 신호 프레임이 HTTP 응답에 실제로 실리는지 검증한다.
 * AFTER_COMMIT 실배선(트랜잭션 경계)은 T1 스모크가 담당한다(plan §6 규율).
 */
@WebMvcTest(controllers = GuestServerStreamController.class)
@Import(GuestServerStreamService.class)
class GuestServerStreamControllerTest {

    @Autowired MockMvc mvc;
    @Autowired GuestServerStreamService streamService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /provisioning/server/stream — 200 + text/event-stream 구독 (async)")
    void stream_subscribes() throws Exception {
        // 같은 컨텍스트의 다른 테스트가 남긴 구독이 있을 수 있다 — 증가분으로 검증
        int before = streamService.subscriberCount();

        MvcResult result = mvc.perform(get("/provisioning/server/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(streamService.subscriberCount()).isEqualTo(before + 1);
        // 구독 직후 comment 1프레임 — 연결 수립을 즉시 확정
        assertThat(result.getResponse().getContentAsString()).contains(":connected");
    }

    @Test
    @DisplayName("구독 중 변화 신호 — 응답 스트림에 event:changed + 서버 id 프레임")
    void stream_receivesChangedSignal() throws Exception {
        MvcResult result = mvc.perform(get("/provisioning/server/stream"))
                .andExpect(request().asyncStarted())
                .andReturn();

        UUID serverId = UUID.randomUUID();
        streamService.onChanged(new GuestServerChangedEvent(serverId));

        assertThat(result.getResponse().getContentAsString())
                .contains("event:changed")
                .contains("data:" + serverId);
    }
}
