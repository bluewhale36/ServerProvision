package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.provisioning.assignment.enums.MemberApplyOutcome;

import java.util.UUID;

/**
 * 그룹 멤버 하나가 고른 정의서에 대해 어떻게 되는가 (U3-5-c) — 미리보기 표의 한 줄.
 *
 * @param reason 건너뛰는 사유. 붙는 멤버는 {@code null}. 문구는 도메인이 만든 것이라
 *               direct POST 를 거절하는 서버 가드의 메시지와 같은 문자열이다
 */
public record MemberOutcomeResponse(
        GuestServerSummaryResponse server,
        MemberApplyOutcome outcome,
        String reason
) {

    public UUID serverId() {
        return server.id();
    }

    public String serverName() {
        return server.name();
    }

    /** 이 멤버에 실제로 붙는가 — 집계와 실행 대상 선별이 같은 술어를 쓴다. */
    public boolean assigns() {
        return outcome.assigns();
    }
}
