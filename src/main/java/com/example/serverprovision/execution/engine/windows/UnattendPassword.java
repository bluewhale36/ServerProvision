package com.example.serverprovision.execution.engine.windows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 응답 파일의 비밀번호 인코딩(E4-1-a-3 D-7) — Windows 는 {@code PlainText=false} 값을 "평문 + 부모 노드명" 을
 * UTF-16LE 로 인코딩한 Base64 로 읽는다. 실측 스크립트가 이 산식으로 만든 값으로 로그인에 성공했다.
 * Base64 는 난독화일 뿐이므로 결과도 평문과 같은 비밀로 취급한다(로그 · 원장 · 화면 금지).
 */
public final class UnattendPassword {

    public static final String ADMINISTRATOR_NODE = "AdministratorPassword";
    public static final String AUTOLOGON_NODE = "Password";

    private UnattendPassword() {
    }

    public static String encode(String plain, String nodeName) {
        if (plain == null) {
            throw new IllegalArgumentException("비밀번호 평문이 없습니다.");
        }
        return Base64.getEncoder().encodeToString((plain + nodeName).getBytes(StandardCharsets.UTF_16LE));
    }
}
