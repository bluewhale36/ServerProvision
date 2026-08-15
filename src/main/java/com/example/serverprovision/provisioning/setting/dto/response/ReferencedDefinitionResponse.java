package com.example.serverprovision.provisioning.setting.dto.response;

/**
 * 소프트참조가 가리키는 세팅 정의서를 <b>지금</b> 해석한 결과 (U3-5-d).
 *
 * <p>그룹 표준처럼 참조 무결성 없이 id 만 들고 있는 자리가 쓴다. 스냅샷({@code SourceDefinitionRef})이
 * 이름까지 함께 얼려 두는 것과 정반대다 — 스냅샷은 "정의서가 나중에 바뀌어도 이 서버가 밟을 것은 얼린
 * 그 값" 이라 얼리지만, 표준은 개정을 따라가는 편이 자연스러우므로 이름도 상태도 <b>읽는 시점에</b>
 * 푼다(DEC-F).</p>
 *
 * <p>그래서 <b>가리키던 정의서가 사라진 것은 오류가 아니라 정상 상태</b>다. 부재를 예외로 던지면 화면이
 * 표준 절 자체를 그리지 못해 해제할 방법까지 함께 사라진다. 부재도 값의 한 형태로 담는다.</p>
 *
 * @param definitionId 참조가 들고 있는 id — 정의서가 사라져도 이 값은 남는다
 * @param definition   지금 해석된 정의서. 사라졌으면 null
 * @param blockReason  지금 붙일 수 없으면 그 사유, 붙일 수 있으면 null. 문자열은 도메인 SSOT
 *                     {@code SettingDefinition.assignBlockReason()} 이 만든 것 그대로라 할당 경로의
 *                     거절과 같은 말을 쓴다
 */
public record ReferencedDefinitionResponse(
        Long definitionId,
        SettingSummaryResponse definition,
        String blockReason
) {

    /** 가리키던 정의서가 아예 없어졌을 때 — 소프트참조라 참조 무결성이 없어 실제로 일어난다. */
    public static ReferencedDefinitionResponse gone(Long definitionId) {
        return new ReferencedDefinitionResponse(definitionId, null,
                "가리키던 세팅 정의서가 더 이상 없습니다.");
    }

    /**
     * 가리키던 정의서를 찾았는가 — 이름과 상태를 읽을 수 있는가와 같은 물음이다.
     *
     * <p>{@code present()} 로 부르지 않는 것은 의도한 것이다. 이 참조를 감싸는 화면 응답
     * ({@code GroupStandardResponse})에도 {@code present()} 가 있는데 그쪽이 묻는 것은 <b>"그룹이
     * 표준을 정해 두었는가"</b> 로 전혀 다른 질문이다. 표준을 정해 두었는데 그 정의서가 사라진 상태
     * — 즉 앞은 참이고 뒤는 거짓인 상태 — 가 실제로 존재하므로 이름이 갈려 있어야 한다.</p>
     */
    public boolean resolved() {
        return definition != null;
    }

    /** 지금 이 정의서를 서버에 붙일 수 있는가. */
    public boolean usable() {
        return blockReason == null;
    }

    /**
     * 화면에 적을 이름. 사라진 정의서는 id 를 대신 적는다 — 빈칸으로 두면 무엇을 해제하려는지 알 수 없다.
     */
    public String name() {
        return resolved() ? definition.name() : "(사라진 정의서 #" + definitionId + ")";
    }

    /** 사용 중단 권고 — 차단이 아니라 경고다. 사라진 정의서는 권고를 물을 대상이 아니다. */
    public boolean deprecated() {
        return resolved() && definition.deprecated();
    }
}
