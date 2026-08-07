package com.example.serverprovision.provisioning.assignment.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 세팅 정의서 재할당 요청(XHR JSON) — 갈아끼울 정의서 id 만. 게스트 id 는 경로 변수.
 */
public record ReassignSettingRequest(

        @NotNull(message = "세팅 정의서를 선택해야 합니다.")
        Long definitionId
) {
}
