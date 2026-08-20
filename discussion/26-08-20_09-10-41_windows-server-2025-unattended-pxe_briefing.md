# Windows Server 2025 무인 설치 · PXE 기동 — 조사 브리핑

> **문서 종류**: 웹 조사 브리핑(Opus 하위 에이전트 조사 · 세션 판정 완료). 실측 아님 — 실측 필요 항목은 §6.
> **작성**: 2026-08-20 KST.
> **배경**: 주문 실측(2026-08-20)에서 "Windows Server 2025 설치 + stress test 가 대부분 출고 서버의 실 공통 과정"임이 확인돼 MVP 배포 후 업데이트 1순위 후보로 기록됨(`discussion/26-08-20_08-26-28_setting-definition-os-scope_discussion.md` §8 D4). 본 조사는 그 후보의 기술 성립성을 사전 확인한 것.
> **세션 판정 요약**: ① 성립 경로는 **iPXE wimboot + WinPE(HTTP) + Samba 공유의 `setup.exe /unattend:`** 단 하나 — WDS 는 Server 2025 미지원 + CVE-2026-0386 으로 불성립, MDT 는 폐기. ② 이 경로는 기존 인프라(dhcpd · tftp · httpd · iPXE 체인 · 게스트별 dispatcher)와 완전 정합하고, wimboot 의 initrd 주입으로 **게스트별 autounattend.xml 을 기존 dispatcher 방식 그대로 서빙**할 수 있다. ③ 대형 미지수 둘 = **U1**(Server 2025 새 setup 엔진의 `oobeSystem` pass 처리 여부 — 실물 반나절이면 판가름) · **S3**(2026 Secure Boot 인증서 전환 — 2023 CA 서명 부트매니저를 보드 펌웨어가 신뢰하는가). 폴백 = `DISM /Apply-Image` 경로(확정 탈출구) + 첫 구현은 Secure Boot off. ④ 착수 시점은 별도 결정 — 이 문서는 그때의 입력이다.

---

## §1 · PXE 기동 경로 비교

|             | ⓐ iPXE `wimboot` + WinPE(HTTP)                                                                    | ⓑ WDS                                                                                                                                                                                                  | ⓒ MDT / Configuration Manager / Autopilot                 |
| ----------- | ------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------- |
| **성립 여부**   | **성립** (권장)                                                                                       | **불성립**                                                                                                                                                                                                | MDT **불가**(폐기) · ConfigMgr 이론상 가능하나 부적합 · Autopilot 무관    |
| **필요 구성요소** | `wimboot` 바이너리 · `boot.wim` · httpd(기존) · 설치 소스용 Samba · `winpeshl.ini` + 배치 · `autounattend.xml` | Windows Server 1대 + WDS 역할 + RemoteInstall 공유                                                                                                                                                          | MDT = Windows 서버 + ADK + 배포 공유 / ConfigMgr = SQL + 사이트 서버 |
| **리눅스 정합성** | **완전 정합** — httpd · tftp 재사용, iPXE 체인 그대로, Windows 머신 0대(WIM 조작은 `wimlib` 로 리눅스에서 가능)             | **비정합** — Windows 서버가 별도로 필요하고, PXE · DHCP 를 WDS 가 다시 장악                                                                                                                                               | **비정합**                                                   |
| **차단 사유**   | —                                                                                                 | ① Windows Server 2025 는 WDS 배포 **미지원**, Windows 11 및 이후 Windows Server 의 설치 미디어 `boot.wim` 워크플로는 **차단됨** ② **CVE-2026-0386** 으로 unattend.xml 기반 hands-free 배포가 2026-04-14 이후 업데이트에서 **기본 비활성 · 지원 중단** | MDT 는 2026-01-06 **즉시 폐기**(다운로드 제거 · Windows 11 미지원 명시)   |

### ⓐ 가 WDS 차단 규정에 걸리지 않는 근거 (확정)

Microsoft 의 폐기 문서가 차단 범위를 명시적으로 좁혀 놓았다. 원문 인용:

> "WDS PXE boot isn't affected. You can still use WDS to PXE-boot devices to custom boot images."
> "You can also still run setup from a network share."
> "Workflows that use custom `boot.wim` images, such as with Configuration Manager or MDT, aren't impacted."

즉 차단 대상은 **WDS 가 설치 미디어 `boot.wim` 을 직접 배포하는 워크플로**이며, ⓐ 는 WDS 를 전혀 경유하지 않고 "네트워크 공유에서 setup 실행" 에 해당한다. CVE-2026-0386 도 "native WDS scenarios where an Unattend.xml file is referenced and exposed through the RemoteInstall share" 에 한정된다 — RemoteInstall 공유와 WDS RPC 채널이 없으면 무관하다.

**출처**
- https://github.com/MicrosoftDocs/windowsserverdocs/blob/main/WindowsServerDocs/get-started/removed-deprecated-features-windows-server.md
- https://support.microsoft.com/en-us/topic/windows-deployment-services-wds-hands-free-deployment-hardening-guidance-related-to-cve-2026-0386-0daa3a3c-f3cd-4291-9147-a459c290c462
- https://learn.microsoft.com/en-us/troubleshoot/windows-server/setup-upgrade-and-drivers/deployment-toolkit-support
- https://oofhours.com/2026/01/06/the-mdt-download-is-gone-so-rip/

## §2 · 권장 경로와 근거

**권장: ⓐ iPXE `wimboot` → WinPE(HTTP 부팅) → Samba 공유의 `setup.exe /unattend:` 실행.**

근거 넷:

1. **기존 자산 재사용이 최대다.** `ipxe.efi` 체인로딩 · 동적 boot 스크립트 dispatcher · httpd 가 그대로 쓰인다. 새로 추가되는 서비스는 Samba 하나뿐이고, 그마저 읽기 전용 공유 1개다. 진단 리눅스(Alpine)와 동일한 dispatcher 분기에 Windows 경로를 한 갈래 더 붙이는 형태가 된다.
2. **Secure Boot 가 켜져 있어도 성립한다(확정).** iPXE 공식 문서: "you can use iPXE to boot into Microsoft Windows using `wimboot` in the usual way. Windows and `wimboot` are both already signed for UEFI Secure Boot". iPXE 자체는 `ipxe-shim.efi` / `snponly-shim.efi` 로 shim 체인을 쓴다. (단 2026년 인증서 만료 이슈는 §6 S 계열 — 이 경로의 유일한 실측 급소.)
3. **게스트별 `autounattend.xml` 동적 생성이 자연스럽다.** wimboot 은 iPXE 의 `initrd` 로 넘긴 파일을 WinPE 의 `X:\Windows\System32\` 에 주입한다. 즉 ServerProvision 이 MAC 별로 XML 을 HTTP 로 서빙하면 WinPE 안에 HTTP 클라이언트가 없어도 응답 파일이 배달된다. 디스크 선택(§4)의 근본 해법이 여기서 나온다.
4. **Microsoft 가 남겨둔 유일한 경량 경로다.** MDT 폐기 · WDS 차단 이후 Microsoft 권장은 "Configuration Manager, Autopilot, or WinPE-based methods" — 앞의 둘은 이 규모 · 용도에 과하고, 남는 것이 WinPE 기반이며 ⓐ 가 그 최소 구현이다.

**보조 대안(채택 안 함, 기록용)**
- **iSCSI `sanhook`**: ISO 를 담은 iSCSI LUN 을 게스트에 붙여 WinPE 가 로컬 디스크로 보게 하는 방식. Samba 없이 리눅스 iSCSI 타깃만으로 성립하나 디버깅이 어렵다.
- **`DISM /Apply-Image` 직접 적용**: `setup.exe` 를 건너뛰고 WinPE 배치가 diskpart → DISM → `bcdboot` 을 수행. 디스크 선택 자유도 최고 · 새 setup 엔진 변화(§6 U 계열)에서 자유로우나, `windowsPE` pass 를 손으로 재구현하는 셈이고 `specialize`/`oobeSystem` 은 `\Windows\Panther\unattend.xml` 배치가 별도로 필요. **ⓐ 가 setup 엔진 문제로 막히면 확정된 탈출구다.**

**출처**
- https://ipxe.org/secboot · https://ipxe.org/wimboot · https://ipxe.org/howto/winpe

## §3 · 권장 경로 구성 상세

### 3-1 · 파일과 출처

| 파일 | 출처 | 비고 |
|---|---|---|
| `wimboot` | github.com/ipxe/wimboot/releases (Microsoft 서명 릴리스) | 최신 v2.9.0 (2025-11-17). 직접 빌드본은 Secure Boot 에서 거부 |
| `boot.wim` | Windows Server 2025 ISO 의 `\sources\boot.wim` | index 2 = Windows Setup. `setup.exe` 내장이라 ADK 불요 |
| `install.wim` | 같은 ISO | Samba 공유로 제공 (HTTP 아님) |
| `BCD` · `boot.sdi` · `bootmgr` | **불필요** | wimboot 2.7.1 이후 `boot.wim` 에서 자동 추출(확정 — CHANGELOG) |
| `winpeshl.ini` · `install.bat` | 직접 작성 | wimboot 이 `X:\Windows\System32\` 에 주입 |
| `autounattend.xml` | **ServerProvision 이 게스트별 동적 생성** | 같은 방식으로 주입 |

ISO 마운트 · WIM 조작은 리눅스에서 완결된다(`mount -o loop`, 드라이버 주입은 `wimlib`).

### 3-2 · WinPE 부팅 제어 흐름 (확정)

```
winlogon → HKLM\SYSTEM\Setup\CmdLine → winpeshl.exe
   ├─ X:\Windows\System32\winpeshl.ini 있음 → 여기 나열된 앱 실행   ← 우리가 가로채는 지점
   ├─ 없음 → X:\setup.exe 있으면 실행(= 설치 미디어 boot.wim 의 기본 동작)
   └─ 둘 다 없음 → cmd /k X:\Windows\System32\startnet.cmd
```

핵심: **주입한 `winpeshl.ini` 가 설치 미디어의 setup 자동 실행보다 우선**한다 — 설치 미디어 boot.wim 을 그대로 쓰면서 흐름 전체를 우리가 잡는다.

### 3-3 · iPXE 스크립트 (dispatcher 가 생성)

```ipxe
#!ipxe
set base http://prov.example.local/win2025
kernel ${base}/wimboot
initrd ${base}/winpeshl.ini                                   winpeshl.ini
initrd ${base}/install.bat                                    install.bat
initrd http://prov.example.local/api/pxe/v1/guest/${net0/mac}/autounattend.xml  autounattend.xml
initrd ${base}/boot.wim                                       boot.wim
boot
```

세 번째 `initrd` 가 이 설계의 요점 — 게스트별 dispatcher 가 부트 스크립트를 내주듯 응답 파일도 게스트별로 내준다.

### 3-4 · `winpeshl.ini`

```ini
[LaunchApps]
cmd.exe, /c X:\Windows\System32\install.bat
```

> 인용 부호 규칙(`"cmd.exe", "/c ..."` 형태 필요 여부)은 문서마다 표기가 갈린다 — 실측 항목(U6).

### 3-5 · `install.bat`

```bat
@echo off
wpeinit

rem 네트워크가 올라올 때까지 대기 (NIC 드라이버 초기화 지연 대비)
set /a n=0
:wait
ping -n 2 10.0.0.1 >nul 2>&1 && goto ok
set /a n+=1
if %n% GEQ 30 goto fail
goto wait
:ok

rem 인증된 계정으로 연결 — guest 는 24H2/2025 SMB 클라이언트가 거부한다 (§6 N 계열)
net use N: \\10.0.0.1\win2025 /user:deploy "DeployP@ss1" || goto fail

N:\sources\setup.exe /unattend:X:\Windows\System32\autounattend.xml
goto :eof

:fail
echo [ServerProvision] 설치 소스 연결 실패 & cmd
```

`setup.exe` 를 공유에서 실행하면 `N:\sources\install.wim` 을 자동으로 찾으므로 응답 파일에 `InstallFrom` 경로 지정이 불요. `/unattend:` 는 공식 문서상 WinPE 발 setup.exe 에 적용 가능하며 UNC 경로도 받는다.

### 3-6 · Samba 구성 요점

읽기 전용 공유 하나. **guest 공유 금지** — Windows Server 2025 / 11 24H2 SMB 클라이언트는 서명을 요구하고 서명은 guest 자격증명과 병용 불가라, 옛 레시피(`guest only = yes`)는 그대로 실패한다.

```ini
[global]
   server min protocol = SMB3
   server signing = mandatory
   map to guest = never

[win2025]
   path = /srv/pxe/win2025
   read only = yes
   guest ok = no
   valid users = deploy
```

`smbpasswd -a deploy` 로 계정 생성, 445/tcp 개방.

### 3-7 · `autounattend.xml` 최소 골격 (요지)

- **windowsPE pass**: 언어(`Microsoft-Windows-International-Core-WinPE`) + `Microsoft-Windows-Setup` 의 `UserData`(EULA 자동 수락 · 평가판은 ProductKey 미기입) · `DiskConfiguration`(DiskID 는 서버가 게스트별 주입 — §4, EFI 260MB + MSR 128MB + Primary extend, `WillShowUI=OnError`) · `ImageInstall`(`InstallTo` DiskID/PartitionID + `InstallFrom/MetaData` 의 `/IMAGE/NAME`).
- **specialize pass**: `ComputerName`(`*` 또는 서버 주입 자산번호) · `TimeZone`(Korea Standard Time).
- **oobeSystem pass**: `UserAccounts/AdministratorPassword`(base64 난독 — §4-2) · `AutoLogon` · `OOBE`(전 화면 숨김 · `ProtectYourPC=3`) · `FirstLogonCommands`(ServerProvision 완료 보고 POST — stress test 자동 개시의 훅 자리).

주의 셋: ① **평가판 이미지의 `/IMAGE/NAME` 에는 "Evaluation" 이 들어간다** — `wimlib-imagex info install.wim` 으로 실값 확인(U5). ② EFI 파티션은 현행 권장 260MB. ③ **Windows Server 2025 는 설치에 TPM 2.0 · Secure Boot 를 요구하지 않는다(확정)** — Windows 11 클라이언트와 다른 점.

**출처**
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/windows-setup-command-line-options
- https://learn.microsoft.com/en-us/windows-hardware/customize/desktop/unattend/microsoft-windows-setup-diskconfiguration-disk-diskid
- https://learn.microsoft.com/en-us/windows-server/get-started/hardware-requirements
- https://github.com/ipxe/wimboot/blob/master/CHANGELOG.md
- https://easy2boot.xyz/troubleshooting-e2b/wimboot-and-the-winpe-boot-process/
- https://learn.microsoft.com/en-us/windows-server/storage/file-server/smb-signing
- https://github.com/zoicware/Server2025Unattend

## §4 · 디스크 선택 · 비밀번호 자동화

### 4-1 · 디스크 선택

**문제(확정).** `DiskID` 는 순서 번호일 뿐이며 Microsoft 스스로 불안정하다고 경고한다: "The system may assign different numbers to disks when you reboot, and different computers with the same disk configuration can have different disk numbers." OS 용 + 데이터용이 함께 꽂힌 서버에서 정적 `DiskID=0` + `WillWipeDisk=true` 는 **데이터 디스크를 지울 수 있다.**

**해법 셋 — 우선순위 순**
1. **서버가 게스트별 `DiskID` 주입(권장)**: 진단 리눅스가 수집한 디스크 목록에서 사용자가 선택 → 서버가 `autounattend.xml` 에 환산 기입 → dispatcher 가 서빙. 요구사항("사용자 입력 = 디스크 선택")과 정확히 맞물린다. 전제 = 진단 리눅스와 WinPE 의 디스크 열거 순서 일치(실측 D1).
2. **WinPE 배치의 `diskpart` 판정(안전망)**: 크기 · 모델 · 버스로 목표 디스크를 지목. 이 방식이면 응답 파일에서 `DiskConfiguration` 을 빼거나 DISM 경로로 넘어간다.
3. **최소 파괴 원칙**: `WillWipeDisk` 대신 대상 디스크만 명시적 `diskpart clean`, `WillShowUI=OnError` 유지 — 판정이 틀리면 조용히 밀지 않고 멈춘다.

**한계(확정)**: 동일 모델 다수 장착 시 크기 구분 불가 · **unattend 의 `DiskConfiguration` 은 디스크 장치만 다루므로 RAID 카드 뒤 논리 볼륨 설치는 setup 경로가 아니라 `DISM /Apply-Image` 분기가 필요**하다는 보고 존재 — RAID 구성(MA7 · U4-1)이 걸린 서버는 DISM 분기를 미리 열어 둔다.

### 4-2 · Administrator 비밀번호

**인코딩 규칙(확정)**: `PlainText=false` 값은 암호화가 아니라 난독화 — `평문 + 부모 노드명`(`AdministratorPassword` 또는 `Password`)을 UTF-16LE 로 변환해 Base64. Java 로 한 줄:

```java
Base64.getEncoder().encodeToString((plain + nodeName).getBytes(StandardCharsets.UTF_16LE))
```

**취급(확정)**: 평문과 동일하게 취급. 설치 중 응답 파일이 `%WINDIR%\Panther` 에 캐시로 남으므로 `FirstLogonCommands` 또는 `SetupComplete.cmd` 에 Panther 정리를 넣는 것이 정석. stress test 후 와이프 전제라 실질 위험은 낮으나 표준 비밀번호를 전 장비 고정하면 그 값이 어디에나 남는다.

**출처**
- https://learn.microsoft.com/en-us/previous-versions/windows/it-pro/windows-7/ee851580(v=ws.10)
- https://learn.microsoft.com/en-us/windows-hardware/manufacture/desktop/windows-setup-automation-overview

## §5 · 라이선스 요점

| 항목 | 내용 | 확실도 |
|---|---|---|
| 평가 기간 | 180일 · 설치 후 10일 내 인터넷 활성화 필요 | 확정 |
| 연장 | `slmgr /rearm` 최대 6회 | 확정 |
| 사용 범위 | "test, demonstrate, and internally evaluate" 만 — "live operating environment" 불가 | 확정 |
| 정품 전환 | 가능하나 에디션 교차 불가 | 확정 |

**본 용도 판단(추정 — 계약 확인 권장)**: "출고 서버 stress test 후 와이프" 는 문언상 test 에 해당하나, ① 설치된 채 출하하면 위반(반드시 와이프 또는 정품 재설치) ② "대부분의 출고 서버가 거치는" 규모면 평가가 아니라 **제조 공정 상시 도구**로 보이기 쉬움 — OEM/시스템 빌더 채널의 재설치 · 테스트 권한(OPK 계약)이 정확한 근거이며, 이런 조직이면 그 계약 경로가 이미 있을 가능성이 높다. 부수: 평가판이 180일 전 조기 만료되는 사례가 다수 보고됨(마스터 이미지 장기 재사용 시 주기 갱신 필요).

**출처**
- https://www.microsoft.com/en-us/evalcenter/evaluate-windows-server-2025
- https://learn.microsoft.com/en-us/windows-server/get-started/upgrade-conversion-options

## §6 · 실측 필요 항목 (체크리스트 후보)

### S — Secure Boot · 부팅 체인 (2026년 인증서 만료가 직격)
- **S1**: Secure Boot on 상태로 `ipxe.efi`(또는 `ipxe-shim.efi`) 부팅 — 신뢰 기반이던 Microsoft UEFI CA 2011 이 2026-06-27 만료(만료 즉시 거부는 아니라는 것이 Microsoft 입장이나 펌웨어별 편차).
- **S2**: 서명된 wimboot v2.9.0 로드 — 어느 CA(2011/2023)로 서명됐는지 공개 문서 없음.
- **S3**: Server 2025 `boot.wim` 의 `bootmgfw.efi`(Windows UEFI CA 2023 서명) 를 보드 db 가 신뢰하는가 — **GIGABYTE 펌웨어의 2023 인증서 포함 여부 · BIOS 업데이트 선행 확인**.
- **S4**: Secure Boot off 폴백 경로 확보 — 첫 구현은 off 로 성립시키고 S1~S3 은 별도 트랙.

### N — 네트워크 · 공유
- **N1**: WinPE(26100)의 SMB 서명 강제 여부(Samba `server signing = mandatory` 면 어느 쪽이든 통과). **N2**: guest 실패 · 실계정 성공 실측. **N3**: IP 주소 접속 시 서명 성립. **N4**: boot.wim 전송 시간 · 다중 동시 부팅 부하. **N5**: WinPE 에 `curl.exe` 존재 여부(있으면 런타임 HTTP 수령 가능 — 설계 자유도 상승).

### D — 디스크
- **D1**: 진단 리눅스 ↔ WinPE `diskpart` 열거 순서 일치. **D2**: 동일 모델 다수 시 식별 키(시리얼 · WWN). **D3**: RAID 논리 볼륨에 setup 경로 설치 가능 여부(불가면 DISM 분기 확정). **D4**: `WillWipeDisk=true` 의 파괴 범위 실증(데이터 디스크 장착 상태로).

### U — unattend · setup 엔진 (최대 미지수)
- **U1**: **Server 2025 `setup.exe /unattend:` 가 `specialize` · `oobeSystem` pass 를 처리하는가** — Windows 11 24H2/25H2 의 새 setup 엔진(ConX)이 `oobeSystem` 을 무시한 사례 다수, 단 Server 2025 성공 사례도 존재 — 실측 전 확정 불가.
- **U2**: 실패 시 legacy setup 강제 — `/legacy` 미문서 플래그 · boot.wim 레지스트리 `CmdLine` 교체 · `%CONFIGSETROOT%` 세 방법.
- **U3**: wimboot 주입 파일이 `X:\Windows\System32\` 에 실재 배치되는가. **U4**: `winpeshl.ini` 가 설치 미디어 setup 자동 실행을 실제 선점하는가. **U5**: 평가판 `install.wim` 의 정확한 `/IMAGE/NAME`. **U6**: `[LaunchApps]` 인용 부호 문법.

### H — 하드웨어 · 드라이버
- **H1/H2**: GIGABYTE 보드 NIC · 스토리지 드라이버의 stock boot.wim 포함 여부(없으면 `wimlib` 주입 파이프라인). **H3**: RAM 요건(boot.wim 전체 RAM 적재 오버헤드 포함). **H4**: AST2600 KVM 으로 WinPE 원격 관찰(무인 디버깅 생명줄). **H5**: AMI UEFI 네트워크 스택과 iPXE 체인 상호작용(진단 리눅스 기동 실적으로 대부분 해소 추정).

**출처**
- https://techcommunity.microsoft.com/blog/windows-itpro-blog/act-now-secure-boot-certificates-expire-in-june-2026/4426856
- https://lenovopress.lenovo.com/lp2353-updating-windows-boot-manager-and-winpe-windows-uefi-ca-2023-certificate
- https://learn.microsoft.com/en-us/windows-server/storage/file-server/smb-security-hardening
- https://www.elevenforum.com/t/w11-25h2-autounattend-xml-fails-how-to-integrate-the-previous-legacy-setup.43235/

## §7 · 착수 순서 제안 (리스크 큰 순)

1. **U5 · U3 · U4** — Secure Boot off · 디스크 1개 · 고정 DiskID=0 최소 환경에서 "wimboot → WinPE → winpeshl 선점 → net use → setup /unattend" 완주 확인. 여기서 **U1 이 판가름** — 이 한 판이 전체 경로의 성립을 결정한다.
2. U1 실패 시 U2 → 그래도 실패면 **DISM 경로 전환 확정**(이 시점에 결정해야 이후가 안 뒤집힌다).
3. **N1~N3** — Samba 서명 · 인증 구성 확정.
4. **H1 · H2** — 실보드 NIC · 스토리지 가시성(없으면 wimlib 주입 파이프라인).
5. **D1 · D4** — 다중 디스크 장비로 파괴 범위 실증(**건너뛰고 운영에 올리면 데이터 디스크를 날린다**).
6. **S1~S4** — Secure Boot on 트랙(GIGABYTE BIOS 업데이트가 선행 조건일 수 있음).

최대 미지수 둘 = **U1**(새 setup 엔진의 unattend 처리)과 **S3**(2023 CA 신뢰) — 둘 다 문서로 판정 불가, 실물 1대 반나절이면 갈린다.
