package com.example.serverprovision.global.exception;

import java.util.regex.Pattern;

/**
 * 화면으로 나가는 오류 문장 다듬기(S22-3). 예외 문장 끝에 붙는 내부 식별자({@code id=…}, {@code guestId=…},
 * {@code definitionId=…})는 로그에서 원인을 찾는 데 쓰이지만 운영자에게는 뜻이 없다. 예외 원문은 로그가 그대로
 * 남기고, 응답 경계에서만 걷어낸다 — 예외 72 곳을 낱낱이 고치면 로그의 식별자까지 잃는다.
 */
public final class UserFacingMessage {

    private static final Pattern INTERNAL_ID =
            Pattern.compile("[,(]?\\s*\\b(?:id|guestId|guestServerId|definitionId|groupId)=[\\w\\-]+\\)?");

    private UserFacingMessage() {
    }

    public static String withoutInternalIds(String message) {
        if (message == null) {
            return null;
        }
        return punctuate(INTERNAL_ID.matcher(message).replaceAll("").strip());
    }

    /** "X하십시오", "X하세요", "X해 주세요": 하다 동사의 요청형. 평서형 "X해야 합니다" 로 바꾼다. */
    private static final Pattern HADA_REQUEST =
            Pattern.compile("([가-힣]+)(?:하십시오|하세요|해 ?주세요)");

    /**
     * 화면 문구 표기 규칙(DESIGN.md 7절). 엔진, 예외 문장은 출처가 넓고 원장에 저장된 옛 문장도 있어, 화면으로 나가는
     * 경계에서 대시는 콜론으로, 가운뎃점은 쉼표로, 하다 동사의 요청형은 평서형으로 바꾼다. 출처는 따로 고친다.
     */
    public static String punctuate(String text) {
        if (text == null) {
            return null;
        }
        String marks = text.replace(" — ", ": ").replace("—", ": ").replace(" · ", ", ").replace("·", ", ");
        return HADA_REQUEST.matcher(marks).replaceAll("$1해야 합니다");
    }
}
