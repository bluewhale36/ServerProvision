package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;

/**
 * 서버 상세의 정의서 선택지 한 줄 (U3-5-a) — 요약에 <b>이 서버에 붙일 수 있는가</b>를 덧댄 것.
 *
 * <p>{@code blockReason} 이 있으면 옵션을 {@code disabled} 로 잠그고 그 문구를 라벨에 덧붙인다.
 * <b>목록에서 지우지 않는 이유</b>는 조용히 사라지면 운영자가 그 정의서가 없어진 것인지 자기 서버에 맞지
 * 않는 것인지 알 수 없기 때문이다. 사용 중단 권고를 라벨에 덧붙이는 기존 규칙과 같은 자리다.</p>
 *
 * <p>판정 문구는 {@code AssignmentBlockKind.evaluate} 가 만든 것을 그대로 쓴다 — 서버 가드가 direct POST 를
 * 거절할 때의 메시지와 같은 문자열이라 화면과 서버가 어긋날 수 없다.</p>
 *
 * @param blockReason 붙일 수 있으면 {@code null}
 * @param unverified  정의서가 보드를 요구하는데 서버 하드웨어가 아직 수집 전이라 대조하지 못했는가.
 *                    막지는 않되 조용히 넘어가지 않도록 화면이 표식을 단다
 */
public record DefinitionOptionResponse(
        SettingSummaryResponse definition,
        String blockReason,
        boolean unverified
) {

    public Long id() {
        return definition.id();
    }

    public String name() {
        return definition.name();
    }

    public boolean deprecated() {
        return definition.deprecated();
    }

    public boolean blocked() {
        return blockReason != null;
    }
}
