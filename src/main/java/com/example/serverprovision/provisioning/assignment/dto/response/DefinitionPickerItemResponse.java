package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;

/**
 * 정의서 선택 모달의 한 항목 (U3-5-b) — 좌측 목록 한 줄과 그것을 고르면 열리는 우측 패널 한 벌.
 *
 * <p>둘을 한 항목으로 묶는 이유는 <b>조각이 같은 목록을 두 번 훑기 때문</b>이다. 좌측 목록과 우측 패널을
 * 따로 받으면 템플릿이 {@code details.get(option.id)} 로 매번 맞춰야 하고, 한쪽에만 있는 id 가 생기면
 * 조용히 빈 패널이 남는다. 묶어서 넘기면 조립 시점에 한 번만 맞추면 된다.</p>
 *
 * <p>차단된 정의서도 {@code detail} 을 갖는다(DEC-C). 막힌 이유만 알려주고 내용을 감추면 운영자가 다음
 * 판단("이 정의서가 하려던 것을 다른 정의서로 할 수 있나")을 할 수 없기 때문이다.</p>
 */
public record DefinitionPickerItemResponse(
        DefinitionOptionResponse option,
        SettingDetailResponse detail
) {

    public Long id() {
        return option.id();
    }

    public String name() {
        return option.name();
    }

    /** 이 서버에 붙일 수 없으면 true — 좌측 목록에서 잠긴 모양이 되고 [할당] 이 열리지 않는다. */
    public boolean blocked() {
        return option.blocked();
    }

    /** 잠긴 사유. 서버 가드가 direct POST 를 거절할 때의 메시지와 같은 문자열이다. */
    public String blockReason() {
        return option.blockReason();
    }

    public boolean deprecated() {
        return option.deprecated();
    }

    /** 보드를 요구하는데 서버 하드웨어가 아직 수집 전이라 대조하지 못했는가 — 막지 않되 표식을 단다. */
    public boolean unverified() {
        return option.unverified();
    }
}
