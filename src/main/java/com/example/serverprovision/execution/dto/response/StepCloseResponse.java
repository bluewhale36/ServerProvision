package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.enums.AgentDirective;

/**
 * step 종료 보고 응답(E1-2) — 종결 처리(+ 소비 훅) 직후의 다음 지시를 함께 돌려준다.
 * 완주(REBOOT)는 체크인으로 전달할 수 없으므로(게이트가 PROVISIONING 한정) 이 응답이 유일한 운반로다.
 * 중복 close(멱등 no-op)도 같은 판정을 다시 계산해 돌려준다 — 응답 유실 재전송이 지시를 잃지 않는다.
 */
public record StepCloseResponse(AgentDirective directive) {
}
