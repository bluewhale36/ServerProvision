package com.example.serverprovision.execution.engine.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HF11-1 — 첫 로그온 보고 스크립트의 산출이 Windows 표시 언어와 무관해야 한다. 실기 2호(ko-KR)에서 pnputil 문장을 파싱하던
 * 옛 스크립트가 드라이버 0 · 문제 장치 0 으로 오보했다. 두 원문은 ASCII 여야 한다(코드페이지 949).
 */
class WindowsOemTemplatesTest {

    @Test
    @DisplayName("spv-report.ps1 — pnputil 문장 파싱(영문 정규식 3 · /enum-devices) 부재 · 게시 이름 oemNN.inf 고유 개수 · Get-PnpDevice Status 로 산출")
    void reportScript_languageNeutral() {
        String ps1 = WindowsOemTemplates.SPV_REPORT_PS1;
        assertThat(ps1).doesNotContain("Added driver packages").doesNotContain("Instance ID:").doesNotContain("Device Description:")
                .doesNotContain("/enum-devices");
        assertThat(ps1).contains("oem\\d+\\.inf").contains("HashSet").contains("$driversAdded = $published.Count")
                .contains("Get-PnpDevice -PresentOnly").contains("$_.Status -ne 'OK'").contains("FriendlyName").contains("InstanceId")
                .contains("/api/pxe/v1/agent/windows/complete").contains("X-Guest-Token");
    }

    @Test
    @DisplayName("두 원문은 ASCII 만 쓴다(WinPE · cmd 코드페이지) · 해시는 원문에서 결정된다")
    void asciiOnly_andHash() {
        for (String text : new String[]{WindowsOemTemplates.SETUPCOMPLETE_CMD, WindowsOemTemplates.SPV_REPORT_PS1}) {
            assertThat(text.chars().allMatch(c -> c < 128)).as("ASCII only").isTrue();
        }
        assertThat(WindowsOemTemplates.scriptsHash()).hasSize(64).matches("[0-9a-f]{64}");
    }
}
