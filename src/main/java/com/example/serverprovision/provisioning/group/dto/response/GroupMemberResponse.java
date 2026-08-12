package com.example.serverprovision.provisioning.group.dto.response;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;

/**
 * 그룹 상세의 멤버 한 줄 — 서버 요약에 그룹 문맥에서만 의미 있는 판정을 덧댄 것 (U3-4).
 *
 * <p>{@code minoritySpec} 은 <b>이 그룹 안에서</b> 소수파 구성인가다. 그룹 밖에서는 뜻이 없으므로
 * 서버 요약에 넣지 않고 여기에 둔다. 혼재 그룹에서 어느 서버가 겉도는지 눈에 띄게 하는 용도이며,
 * 멤버가 한 종류뿐이면 전부 {@code false} 다.</p>
 */
public record GroupMemberResponse(
        GuestServerSummaryResponse server,
        boolean minoritySpec
) {
    /** 화면이 서버 요약을 한 번 더 파고들지 않게 지름길을 둔다. */
    public String specLabel() {
        return server.specLabel();
    }
}
