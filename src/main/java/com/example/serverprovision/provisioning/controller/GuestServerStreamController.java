package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.service.GuestServerStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

    private final GuestServerStreamService guestServerStreamService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return guestServerStreamService.subscribe();
    }
}
