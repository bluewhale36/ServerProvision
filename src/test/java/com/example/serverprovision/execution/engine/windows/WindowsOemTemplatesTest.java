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
    @DisplayName("R15-2 — SetupComplete 는 spv-drivers.lst 를 5 필드로 읽어 TREE · INF · MSI · EXE 로 분기하고 항목마다 [SPV-INSTALL] 줄을 남긴다 · 목록이 없으면 옛 전체 INF 루프")
    void setupComplete_listDriven() {
        String cmd = WindowsOemTemplates.SETUPCOMPLETE_CMD;
        assertThat(cmd).contains("set LIST=%SPV%\\spv-drivers.lst").contains("if not exist \"%LIST%\" goto :legacy")
                .contains("tokens=1-5 delims=|").contains("if \"!ENTRY!\"==\"-\" set ENTRY=").contains("if \"!ARGS!\"==\"-\" set ARGS=")
                .contains("if /i \"!MODE!\"==\"TREE\"").contains("if /i \"!MODE!\"==\"INF\"")
                .contains("if /i \"!MODE!\"==\"MSI\"").contains("if /i \"!MODE!\"==\"EXE\"")
                .contains("msiexec /i \"!BASE!\\!ENTRY!\" /qn /norestart !ARGS!")
                .contains("pnputil /add-driver \"!BASE!\\*.inf\" /subdirs /install")
                .contains("echo [SPV-INSTALL] !FOLDER!^|!MODE!^|exit=!RC!")
                .contains("if \"!REBOOT!\"==\"1\" set NEEDREBOOT=1").contains("echo 1 > \"%SPV%\\reboot-required\"")
                .contains(":legacy").contains("[SPV-INSTALL] -^|LEGACY^|exit=0");
    }

    @Test
    @DisplayName("HF18 — 재부팅은 SetupComplete 가 아니라 완료 보고 뒤 spv-report.ps1 이 한다(첫 로그온 1회를 재부팅이 끊어 보고가 유실되던 실기 결함)")
    void rebootDeferredUntilReported() {
        String cmd = WindowsOemTemplates.SETUPCOMPLETE_CMD;
        assertThat(cmd).doesNotContain("shutdown /r").contains("reboot-required");

        String ps1 = WindowsOemTemplates.SPV_REPORT_PS1;
        int report = ps1.indexOf("Invoke-WebRequest -Uri $uri -Method Post");
        int reboot = ps1.indexOf("shutdown.exe /r /t 5");
        assertThat(report).isPositive();
        assertThat(reboot).as("재부팅은 보고 전송 뒤에 온다").isGreaterThan(report);
        assertThat(ps1).contains("Join-Path $spv 'reboot-required'").contains("Remove-Item $rebootFlag");
    }

    @Test
    @DisplayName("R15-2 — spv-report.ps1 은 [SPV-INSTALL] 줄을 installs(folder · mode · exitCode · 최대 50) 로 본문에 싣는다")
    void reportScript_forwardsInstalls() {
        String ps1 = WindowsOemTemplates.SPV_REPORT_PS1;
        assertThat(ps1).contains("[SPV-INSTALL]").contains("exit=(-?\\d+)").contains("$installs.Count -ge 50")
                .contains("folder = $g[1].Value; mode = $g[2].Value; exitCode = [int]$g[3].Value")
                .contains("installs = @($installs)")
                // HF18-2 — 제네릭 List[object] 는 5.1 에서 List[string] 파이프 열거를 깨뜨렸다(09-17 실기) · 본문 catch 가 예외를 transcript 에 남긴다
                .doesNotContain("Generic.List[object]").contains("$installs = @()").contains("FATAL {0}: {1}");
    }

    @Test
    @DisplayName("두 원문은 ASCII 만 쓴다(WinPE · cmd 코드페이지) · 해시는 원문에서 결정된다")
    void asciiOnly_andHash() {
        for (String text : new String[]{WindowsOemTemplates.SETUPCOMPLETE_CMD, WindowsOemTemplates.SPV_REPORT_PS1}) {
            assertThat(text.chars().allMatch(c -> c < 128)).as("ASCII only").isTrue();
        }
        assertThat(WindowsOemTemplates.scriptsHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

	@Test
	@DisplayName("SetupComplete — 괄호 블록 안의 줄에는 괄호 문자가 없다(cmd 는 블록 안 첫 닫는 괄호에서 블록을 닫는다 · echo 본문도 예외 아님)")
	void setupComplete_noParenthesesInsideBlocks() {
		int depth = 0;
		for (String raw : WindowsOemTemplates.SETUPCOMPLETE_CMD.split("\n")) {
			String line = raw.strip();
			if (depth > 0 && !line.equals(")") && !line.endsWith("(")) {   // 중첩 블록의 여는 줄(if … ( )은 정당
				org.assertj.core.api.Assertions.assertThat(line)
						.as("블록 안 줄: " + line)
						.doesNotContain("(").doesNotContain(")");
			}
			if (line.endsWith("(")) depth++;
			if (line.equals(")")) depth--;
		}
		org.assertj.core.api.Assertions.assertThat(depth).isZero();
	}
}
