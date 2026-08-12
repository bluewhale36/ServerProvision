package com.example.serverprovision.provisioning.group.dto.request;

import java.util.List;
import java.util.UUID;

/**
 * 그룹에 서버를 넣는 요청 (U3-4).
 *
 * <p>아무것도 고르지 않은 제출은 오류가 아니라 <b>아무 일도 일어나지 않는 것</b>으로 본다.
 * 실수로 빈 채 눌렀을 때 오류 모달을 띄우는 것은 얻는 것 없이 흐름만 끊는다.</p>
 */
public record AddMembersRequest(List<UUID> serverIds) {

    public List<UUID> serverIdsOrEmpty() {
        return serverIds == null ? List.of() : serverIds;
    }
}
