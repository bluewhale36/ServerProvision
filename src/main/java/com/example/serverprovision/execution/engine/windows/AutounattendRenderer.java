package com.example.serverprovision.execution.engine.windows;

import java.util.Locale;
import java.util.UUID;

/**
 * autounattend.xml 렌더(E4-1-a-3) — 실측 원문 템플릿의 자리표시자에 값을 치환한다. 모든 값은 XML 이스케이프
 * (이미지 이름 · 시간대에 {@code &} 가 들어올 수 있다). 언어는 이미지 태그 하나를 넷에 그대로 쓴다(D-6 — 매핑 표 없음).
 */
public final class AutounattendRenderer {

    private static final String COMPUTER_NAME_PREFIX = "SPV-";
    private static final int COMPUTER_NAME_HEX = 8;

    private AutounattendRenderer() {
    }

    /**
     * @param language              이미지의 언어 태그(예: ko-KR) — UILanguage · SystemLocale · UserLocale · InputLocale
     * @param productKey            에디션의 제품 키 — 준비도가 존재를 보장한 뒤에만 여기 도달한다
     * @param imageName             install.wim 의 /IMAGE/NAME
     * @param administratorPassword 평문 — 여기서 두 번 인코딩한다(AdministratorPassword · AutoLogon)
     * @param reportBaseUrl         완료 보고 스크립트가 부를 앱 base URL(E4-1-a-4) — FirstLogonCommands 인자
     * @param guestToken            게스트 토큰 값 — 보고의 X-Guest-Token. 렌더본에만 실리고 로그 · toString 에는 나오지 않는다
     */
    public record AutounattendValues(
            String language,
            String productKey,
            String imageName,
            String computerName,
            String timeZone,
            String administratorPassword,
            String reportBaseUrl,
            String guestToken
    ) {
        @Override
        public String toString() {
            return "AutounattendValues[language=" + language + ", imageName=" + imageName
                    + ", computerName=" + computerName + ", timeZone=" + timeZone + ", reportBaseUrl=" + reportBaseUrl
                    + ", productKey=****, password=****, guestToken=****]";
        }
    }

    public static String render(AutounattendValues v) {
        return WindowsInstallTemplates.AUTOUNATTEND_XML
                .replace("__UI_LANGUAGE__", escape(v.language()))
                .replace("__INPUT_LOCALE__", escape(v.language()))
                .replace("__PRODUCT_KEY__", escape(v.productKey()))
                .replace("__IMAGE_NAME__", escape(v.imageName()))
                .replace("__COMPUTER_NAME__", escape(v.computerName()))
                .replace("__TIME_ZONE__", escape(v.timeZone()))
                .replace("__REPORT_BASE_URL__", escape(v.reportBaseUrl()))
                .replace("__GUEST_TOKEN__", escape(v.guestToken()))
                .replace("__ADMIN_PASSWORD_B64__",
                        UnattendPassword.encode(v.administratorPassword(), UnattendPassword.ADMINISTRATOR_NODE))
                .replace("__AUTOLOGON_PASSWORD_B64__",
                        UnattendPassword.encode(v.administratorPassword(), UnattendPassword.AUTOLOGON_NODE));
    }

    /**
     * ComputerName = {@code SPV-} + systemUUID 뒤 8 hex 대문자(D-6) — 결정적이라 재서빙에도 같은 이름이고,
     * NetBIOS 15자 제한 안이며, E4-1-a-4 의 완료 보고와 게스트를 대조할 열쇠가 된다.
     */
    public static String computerNameFor(UUID systemUuid) {
        String hex = systemUuid.toString().replace("-", "").toUpperCase(Locale.ROOT);
        return COMPUTER_NAME_PREFIX + hex.substring(hex.length() - COMPUTER_NAME_HEX);
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
