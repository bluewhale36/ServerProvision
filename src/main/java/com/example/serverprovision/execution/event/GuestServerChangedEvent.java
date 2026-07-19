package com.example.serverprovision.execution.event;

import java.util.UUID;

/**
 * 게스트 서버 변화 신호(S7) — payload 는 서버 id 뿐이다(신호-재조회 패턴). 브라우저는 이 신호를 받으면
 * 보던 페이지를 같은 URL 로 재조회해 관심 영역만 교체하므로, 상태 데이터의 SSOT 가 기존 조회 서비스
 * 하나로 유지되고 SSE payload 스키마가 생기지 않는다. 이벤트 종류 세분(전이/실패/접촉 …)은 소비자가
 * 구분할 필요가 생길 때 확장한다(plan §2 비목표).
 */
public record GuestServerChangedEvent(UUID serverId) {
}
