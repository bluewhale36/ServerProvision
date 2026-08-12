package com.example.serverprovision.provisioning.group.dto.response;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;

/**
 * 그룹 생성 폼에 줄지어 보이는 씨앗 후보 한 줄 (U3-4).
 *
 * <p>이미 다른 그룹에 속한 서버를 <b>목록에서 지우지 않고 사유와 함께 남긴다.</b> 스펙 묶음이 N 대라고
 * 보여줬는데 폼에서 말없이 몇 대가 사라지면 운영자가 이유를 알 수 없기 때문이다.</p>
 *
 * <p>{@code blockReason} 은 {@code GuestServerGroup.addBlockReason(...)} 이 만든 문자열 그대로다 —
 * 체크박스를 잠그는 근거와 서버가 거절하는 사유가 한 곳에서 나온다.</p>
 */
public record SeedCandidateResponse(
        GuestServerSummaryResponse server,
        GroupBadgeResponse currentGroup,
        String blockReason
) {
    public boolean selectable() {
        return blockReason == null;
    }
}
