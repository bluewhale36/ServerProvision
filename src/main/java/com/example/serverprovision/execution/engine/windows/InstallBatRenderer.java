package com.example.serverprovision.execution.engine.windows;

/**
 * install.bat 렌더(E4-1-a-3) — 공유 접속 정보 셋을 치환한다. 배치 파일에서는 이스케이프가 성립하지 않으므로
 * 비밀번호는 {@link #isBatchSafe} 를 통과한 값만 받는다(준비도 10번이 사전 차단 — 여기서는 검증만 되풀이한다).
 */
public final class InstallBatRenderer {

    /** 배치 특수 문자 — cmd 가 인용부호 안에서도 해석하거나(%) 인용 자체를 깨는(") 것들. */
    private static final String BATCH_UNSAFE = "\"%^&|<>()!";

    private InstallBatRenderer() {
    }

    public static String render(String shareUnc, String user, String password) {
        if (!isBatchSafe(password)) {
            throw new IllegalArgumentException("공유 비밀번호에 배치 금지 문자가 있습니다 — 준비도가 먼저 막아야 한다.");
        }
        return WindowsInstallTemplates.INSTALL_BAT
                .replace("__SHARE_HOST__", hostOf(shareUnc))
                .replace("__SHARE_UNC__", shareUnc.trim())
                .replace("__DEPLOY_USER__", user.trim())
                .replace("__DEPLOY_PASSWORD__", password);
    }

    /** {@code \\host\share} → host. 실측 배치가 ping 대상(IP)과 UNC 를 따로 쓰므로 UNC 에서 호스트를 뽑는다. */
    public static String hostOf(String shareUnc) {
        String s = shareUnc == null ? "" : shareUnc.trim();
        int i = 0;
        while (i < s.length() && (s.charAt(i) == '\\' || s.charAt(i) == '/')) {
            i++;
        }
        int end = i;
        while (end < s.length() && s.charAt(end) != '\\' && s.charAt(end) != '/') {
            end++;
        }
        return s.substring(i, end);
    }

    /** 인쇄 가능 ASCII({@code 0x21~0x7E})에서 배치 특수 문자를 뺀 것만 허용 — 공백도 인용 밖에서 깨지므로 제외. */
    public static boolean isBatchSafe(String password) {
        if (password == null || password.isEmpty()) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c < 0x21 || c > 0x7E || BATCH_UNSAFE.indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }
}
