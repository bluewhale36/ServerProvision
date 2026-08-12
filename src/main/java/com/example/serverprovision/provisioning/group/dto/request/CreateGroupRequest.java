package com.example.serverprovision.provisioning.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * 그룹 생성 요청 — 씨앗 생성과 빈 그룹 생성이 같은 요청을 쓴다 (U3-4).
 *
 * <p>{@code serverIds} 가 비어 있으면 빈 그룹이다. 진입점은 둘이지만(DEC-J) 만들어지는 것은 같은 물건이라
 * 엔드포인트를 나누지 않는다.</p>
 */
public record CreateGroupRequest(
        @NotBlank(message = "그룹 이름을 입력하세요.")
        @Size(max = 128, message = "그룹 이름은 128자 이하로 입력해주세요.")
        String name,

        List<UUID> serverIds
) {
    /** 폼 바인딩에서 체크가 하나도 없으면 null 이 온다 — 호출부가 매번 null 을 살피지 않게 여기서 흡수한다. */
    public List<UUID> serverIdsOrEmpty() {
        return serverIds == null ? List.of() : serverIds;
    }
}
