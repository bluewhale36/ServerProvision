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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @DisplayName("S7-1 — 응답 헤더가 전달 조건을 선언한다: X-Accel-Buffering: no · Cache-Control: no-cache")
    void stream_declaresProxyDeliveryHeaders() throws Exception {
        // nginx 가 HTTP/1.0 업스트림의 identity 본문을 4 KB 까지 헤더째 붙들던 실기 결함(2026-08-27)의 회귀 가드.
        // 헤더는 첫 프레임(:connected) 전에 확정돼야 하므로 같은 응답에서 프레임과 함께 확인한다.
        MvcResult result = mvc.perform(get("/provisioning/server/stream"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andExpect(header().string(GuestServerStreamController.NGINX_BUFFERING_HEADER, "no"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();

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
