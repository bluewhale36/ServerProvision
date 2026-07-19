package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.AgentDirective;

/**
 * 체크인 응답(E1-0b 골격) — 에이전트가 다음에 할 일. 지시 확장은 E1-2 부터.
 *
 * @param serverName 운영자가 붙인 서버 이름 — 콘솔 식별 배너(DEC-33, UC-5)의 유일한 서버측 정보.
 *                   커널 인자가 아니라 JSON 으로 운반하는 이유: 이름의 공백 · 비 ASCII 가 cmdline
 *                   파싱을 깨는 표면을 만들지 않기 위해(E1-1 plan Q3).
 */
public record AgentCheckinResponse(AgentDirective directive, String serverName) {
}
