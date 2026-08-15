package com.example.serverprovision.provisioning.assignment.dto.response;

import java.util.List;

/**
 * 서버 상세의 할당 폼 재료 (U3-5-a) — 폼을 열 수 있는가와 열었을 때의 선택지.
 *
 * <p>둘을 한 응답에 담는 이유는 <b>같은 판정에서 나오기 때문</b>이다. 회수된 서버는 어떤 정의서를 골라도
 * 막히므로 폼 자체를 닫고({@code serverBlockReason}), 하드웨어가 맞지 않는 정의서는 서버마다 결과가 달라
 * 옵션 단위로 잠근다({@code options} 의 각 {@code blockReason}). 화면이 두 층을 따로 계산하지 않게 한다.</p>
 *
 * @param serverBlockReason 폼을 열 수 없으면 그 사유({@code GuestServer.assignBlockReason()}), 열 수 있으면 null
 * @param options           정의서 선택지. 잠긴 것도 사유와 함께 남는다(지우지 않는다)
 */
public record AssignmentFormResponse(
        String serverBlockReason,
        List<DefinitionOptionResponse> options
) {

    public boolean blocked() {
        return serverBlockReason != null;
    }

    /** 고를 수 있는 정의서가 하나라도 있는가 — 없으면 화면이 그 사실을 안내한다. */
    public boolean hasSelectable() {
        return options.stream().anyMatch(option -> !option.blocked());
    }
}
