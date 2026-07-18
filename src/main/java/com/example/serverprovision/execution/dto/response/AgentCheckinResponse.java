package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.AgentDirective;

/** 체크인 응답(E1-0b 골격) — 에이전트가 다음에 할 일. 지시 확장은 E1-2 부터. */
public record AgentCheckinResponse(AgentDirective directive) {
}
