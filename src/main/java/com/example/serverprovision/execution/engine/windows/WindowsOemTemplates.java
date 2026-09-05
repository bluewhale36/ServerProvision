package com.example.serverprovision.execution.engine.windows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@code $OEM$} 에 들어가는 설치 후 스크립트 둘의 원문(E4-1-a-4 D-3) — 실측 2 · 3호가 손으로 두고 성공시킨 SetupComplete 의
 * pnputil 루프를 앱이 쓰고, 첫 로그온 보고 스크립트를 더한다. 게스트별 값(base URL · 게스트 토큰)은 파일에 없고
 * autounattend 의 FirstLogonCommands 인자로 들어온다 — 그래서 두 파일은 모든 게스트에 같고, 해시 하나로 갱신 여부를 잰다.
 * 전부 ASCII(코드페이지 949).
 */
public final class WindowsOemTemplates {

    private WindowsOemTemplates() {
    }

    /**
     * {@code $$\Setup\Scripts\SetupComplete.cmd} — Setup 이 끝난 뒤 첫 로그온 전에 SYSTEM 으로 한 번 실행된다.
     * {@code $1\SPV\Drivers\<자원>} 폴더마다 pnputil 로 INF 를 설치하고 문제 장치 목록을 로그에 남긴다.
     * OEM 제품 키로 설치하면 Setup 이 이 파일을 건너뛴다(GVLK · 소매 키는 실행).
     */
    public static final String SETUPCOMPLETE_CMD = """
            @echo off
            rem ServerProvision E4-1-a-4 - SetupComplete.cmd (runs once as SYSTEM after Windows Setup, before first logon).
            rem Installs every driver bundle under %SystemDrive%\\SPV\\Drivers (copied by Setup from $OEM$\\$1\\SPV) with pnputil,
            rem then records the problem-device list. Fieldwork #2/#3 (2026-09-02): 92 problem devices -> 0 with this loop.
            rem ASCII only. Setup skips this file when an OEM product key is used (GVLK / retail keys run it).
            setlocal EnableDelayedExpansion
            set SPV=%SystemDrive%\\SPV
            set LOG=%SPV%\\setupcomplete.log
            if not exist "%SPV%" mkdir "%SPV%"
            echo [%DATE% %TIME%] SetupComplete start > "%LOG%"
            if not exist "%SPV%\\Drivers" (
              echo [%DATE% %TIME%] no driver payload at %SPV%\\Drivers >> "%LOG%"
              goto :problems
            )
            for /d %%D in ("%SPV%\\Drivers\\*") do (
              echo [%DATE% %TIME%] pnputil /add-driver "%%~fD\\*.inf" /subdirs /install >> "%LOG%"
              pnputil /add-driver "%%~fD\\*.inf" /subdirs /install >> "%LOG%" 2>&1
              echo [%DATE% %TIME%] exit code !ERRORLEVEL! for %%~nxD >> "%LOG%"
            )
            :problems
            echo [%DATE% %TIME%] problem devices: >> "%LOG%"
            pnputil /enum-devices /problem >> "%LOG%" 2>&1
            echo [%DATE% %TIME%] SetupComplete end >> "%LOG%"
            endlocal
            exit /b 0
            """;

    /**
     * {@code $1\SPV\spv-report.ps1} — 첫 로그온(Administrator 자동 로그온)에서 FirstLogonCommands 가 base URL · 토큰을 인자로
     * 실행한다. 완료 보고 JSON 을 한 번 보낸다(네트워크 대기 · 전송 각 20 × 15 초). 404 · 409 · 400 은 재시도해도 답이 바뀌지
     * 않는 응답이라 즉시 멈춘다. <b>산출은 표시 언어와 무관해야 한다(HF11-1)</b> — pnputil 의 문장은 로캘별이라 파싱하지 않고,
     * 드라이버 수는 SetupComplete 로그의 게시 이름 {@code oemNN.inf} 고유 개수, 문제 장치는 {@code Get-PnpDevice} 의 Status(enum)로 센다.
     */
    public static final String SPV_REPORT_PS1 = """
            param(
              [Parameter(Mandatory=$true)][string]$BaseUrl,
              [Parameter(Mandatory=$true)][string]$Token
            )
            # ServerProvision E4-1-a-4 - first-logon completion report (FirstLogonCommands, runs as Administrator).
            # Counts published drivers from C:\\SPV\\setupcomplete.log and lists non-OK PnP devices, then POSTs one JSON to the provisioning server.
            # ASCII only. Retries: network wait 20 x 15 s, POST 20 x 15 s. Terminal answers (400/404/409) stop the loop.
            $ErrorActionPreference = 'Continue'
            $spv = Join-Path $env:SystemDrive 'SPV'
            New-Item -ItemType Directory -Force -Path $spv | Out-Null
            Start-Transcript -Path (Join-Path $spv 'spv-report.log') -Append | Out-Null
            try {
              $base = $BaseUrl.TrimEnd('/')
              $uri = $base + '/api/pxe/v1/agent/windows/complete'
              $target = [System.Uri]$base
              for ($i = 1; $i -le 20; $i++) {
                $up = Test-NetConnection -ComputerName $target.Host -Port $target.Port -InformationLevel Quiet -WarningAction SilentlyContinue
                if ($up) { break }
                Write-Output ("network wait {0}/20 - {1}:{2} not reachable" -f $i, $target.Host, $target.Port)
                Start-Sleep -Seconds 15
              }

              # HF11-1: pnputil prints localized text (e.g. Korean on ko-KR), so never parse its words.
              # driversAdded = distinct published names "oemNN.inf" in the SetupComplete log (language-neutral, fieldwork #2: 47).
              $driversAdded = 0
              $logTail = ''
              $logPath = Join-Path $spv 'setupcomplete.log'
              if (Test-Path $logPath) {
                $lines = @(Get-Content $logPath)
                $published = New-Object System.Collections.Generic.HashSet[string]
                foreach ($m in ($lines | Select-String -Pattern '(?i)\\boem\\d+\\.inf\\b' -AllMatches)) {
                  foreach ($g in $m.Matches) { [void]$published.Add($g.Value.ToLowerInvariant()) }
                }
                $driversAdded = $published.Count
                $logTail = (($lines | Select-Object -Last 60) -join "`n")
                if ($logTail.Length -gt 4000) { $logTail = $logTail.Substring($logTail.Length - 4000) }
              }

              # Problem devices = present PnP devices whose Status is not OK (enum, language-neutral) - the pnputil problem listing is not parsed any more.
              $problems = New-Object System.Collections.Generic.List[string]
              try {
                foreach ($d in @(Get-PnpDevice -PresentOnly -ErrorAction Stop | Where-Object { $_.Status -ne 'OK' })) {
                  $item = '{0} ({1})' -f $d.FriendlyName, $d.InstanceId
                  if ($item.Length -gt 200) { $item = $item.Substring(0, 200) }
                  $problems.Add($item)
                }
              } catch {
                Write-Output ("Get-PnpDevice unavailable: {0}" -f $_.Exception.Message)
              }

              $os = Get-CimInstance Win32_OperatingSystem
              $osVersion = ('{0} {1}' -f $os.Caption, $os.Version).Trim()
              if ($osVersion.Length -gt 64) { $osVersion = $osVersion.Substring(0, 64) }

              $body = @{
                computerName = $env:COMPUTERNAME
                osVersion = $osVersion
                driversAdded = [int]$driversAdded
                problemDeviceCount = $problems.Count
                problemDevices = @($problems | Select-Object -First 50)
                setupCompleteLogTail = $logTail
              } | ConvertTo-Json -Depth 3 -Compress
              $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
              $headers = @{ 'X-Guest-Token' = $Token }
              Write-Output ("reporting to {0}: computerName={1} drivers={2} problems={3}" -f $uri, $env:COMPUTERNAME, $driversAdded, $problems.Count)

              for ($i = 1; $i -le 20; $i++) {
                try {
                  $resp = Invoke-WebRequest -Uri $uri -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes -UseBasicParsing -TimeoutSec 30
                  Write-Output ("report accepted: HTTP {0} {1}" -f $resp.StatusCode, $resp.Content)
                  break
                } catch {
                  $code = $null
                  if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
                  Write-Output ("attempt {0}/20 failed: {1} (HTTP {2})" -f $i, $_.Exception.Message, $code)
                  if ($code -eq 400 -or $code -eq 404 -or $code -eq 409) { break }
                  Start-Sleep -Seconds 15
                }
              }
            } finally {
              Stop-Transcript | Out-Null
            }
            """;

    private static final String SCRIPTS_HASH = sha256(SETUPCOMPLETE_CMD + "\n" + SPV_REPORT_PS1);

    /** 두 원문의 SHA-256 — 매니페스트가 기록하고, 앱을 올렸을 때 원문이 바뀌었으면 chip 이 갱신 필요를 보인다. */
    public static String scriptsHash() {
        return SCRIPTS_HASH;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
        }
    }
}
