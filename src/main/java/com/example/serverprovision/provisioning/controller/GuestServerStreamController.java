package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.service.GuestServerStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 게스트 서버 실시간 상태 스트림 구독 진입점(S7). 목록·상세 페이지의 server-stream.js 가
 * EventSource 로 구독하고, 변화 신호(changed: 서버 id)를 받으면 같은 URL 을 재조회해 화면의
 * {@code [data-live]} 영역만 교체한다 — 상태 데이터는 이 스트림이 아니라 기존 조회 경로가 SSOT.
 */
@Controller
@RequestMapping("/provisioning/server")
@RequiredArgsConstructor
public class GuestServerStreamController {

    /** nginx 가 응답 단위로 버퍼링을 끄는 헤더 — 값 {@code no}. */
    static final String NGINX_BUFFERING_HEADER = "X-Accel-Buffering";

    private final GuestServerStreamService guestServerStreamService;

    /**
     * 응답 헤더로 전달 조건을 선언한다(S7-1). 리버스 프록시(nginx)는 업스트림에 HTTP/1.0 으로 묻고
     * identity 본문을 4 KB 버퍼가 찰 때까지 헤더째 붙들므로, 앱이 여기서 비버퍼링을 선언하지 않으면
     * 프레임이 브라우저에 닿지 않는다(2026-08-27 실기). 헤더는 첫 프레임 전에 확정돼야 하므로
     * {@link ResponseEntity} 로 싣는다.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream() {
        return ResponseEntity.ok()
                .header(NGINX_BUFFERING_HEADER, "no")
                .cacheControl(CacheControl.noCache())
                .body(guestServerStreamService.subscribe());
    }
}
