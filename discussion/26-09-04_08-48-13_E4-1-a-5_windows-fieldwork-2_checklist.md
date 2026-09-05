# Windows Server 2025 앱 통합 실기 체크리스트 2호 — E4-1-a-2 · -3 · -4 의 CP7 (E4-1-a-5 실측 2호)

> **문서 종류**: 실기 세션 계획 · 기록 양식. 1호(`discussion/26-08-26_10-52-59_windows-server-2025-pxe-fieldwork-1_briefing.md`)가 정적 파일로 "경로 성립" 을 판가름했다면, 2호는 **앱이 그 경로를 통째로 대신하는지**를 본다 — 게스트는 앱의 `/api/pxe/v1/boot` 사슬로만 올라오고, 설치 파일 · 응답 파일 · 드라이버 페이로드 · 완료 판정을 전부 앱이 낸다.
> **작성**: 2026-09-04 08:48 KST, 앵커 세션. **대상 코드**: dev `4490695`(PR #69 E4-1-a-3 · PR #70 E4-1-a-4 병합본). 스테이징 VM 에는 아직 `0b082c0`(09-03 2차 배포)이 있으므로 §1 재배포가 선행이다.
> **원장**: Notion `E4-1-a-5 : 실측 2호`(https://app.notion.com/p/3cfef0e868f981be8680d97572767b2c). 판정은 §9 표에 적고 끝나면 세션에 전달한다.
> **판정 기준의 출처**: `docs/T3-checklist.md` "E4 — OS 설치" 7항목. 이 문서의 W · K 식별자가 그 7항목을 실행 순서로 편 것이다.
> **비밀값 규율**: Samba `deploy` · Administrator 비밀번호 · 제품 키 · 게스트 토큰은 이 문서 · 채증 파일 · 원장에 적지 않는다. 실값은 VM `/root/win2025-secrets.txt` 뿐이다.

---

## 0. 이 실기가 판정하는 것

| 식별자 | 무엇을 | 성립 조건 | T3 항목 |
|---|---|---|---|
| **W1** | 정의서의 Windows 축(E4-1-a-2) | 관리 화면에서 Windows 정의서(이미지 · 비밀번호)를 만들고 할당 · 개시가 된다 | (E4-1-a-2 CP7) |
| **W2** | 토큰 번들 wimboot 체인(E4-1-a-3) | 실 iPXE 가 `kernel …/windows/{token}/wimboot` + initrd 4 를 받아 WinPE 로 부팅 | 토큰 번들 wimboot 체인 실기 |
| **W3** | 렌더본 무인 완주(E4-1-a-3) | 앱이 렌더한 install.bat · autounattend.xml 로 개입 0 완주 · `hostname` = `SPV-<UUID 8>` | 렌더본으로 무인 설치 완주 |
| **W4** | $OEM$ · SetupComplete(E4-1-a-4) | 앱이 조립한 페이로드로 pnputil 이 돌아 문제 장치 92 → 0 | $OEM$ 페이로드 · SetupComplete 실기 |
| **W5** | 첫 로그온 완료 보고(E4-1-a-4) | `spv-report.ps1` 이 200 을 받고 카드가 완료로 바뀌며 토큰 URL 이 404 | 첫 로그온 완료 보고 도달 |
| **W6** | 종단 뒤 재부팅(E4-1-a-4) | 다음 PXE 가 `exit` 로 로컬 디스크 부팅으로 이어진다 | (같은 항목) |
| **K1** | 재PXE 재진입(E4-1-a-3) | 설치 중 재부팅이 네트워크 우선 순서에서 돌아오면 `exit` 폴스루 + 재진입 n/5 | 재PXE 실측 K1 |
| **K2** | 재PXE 없는 게스트의 스윕(E4-1-a-4) | 보고가 막힌 게스트를 스윕이 INSTALL_TIMEOUT 으로 닫는다 | 재PXE 없는 게스트의 스윕 |
| **D1** | 디스크 열거 순서 | 진단 리눅스 `lsblk` 순서(상세 인벤토리) ↔ WinPE `diskpart list disk` 번호 일치 여부 | 1호 Part D · E4-1-a-6 입력 |
| **D2** | 동일 모델 디스크의 식별 키 | 같은 모델 2개 장착 시 Disk ID · 시리얼 · UniqueId 로 구분 가능한가 | 1호 Part D · E4-1-a-6 입력 |
| **D4** | `WillWipeDisk=true · DiskID=0` 의 파괴 범위 | 데이터 디스크의 표식 파일이 설치 뒤 살아 있는가 · DiskID 0 이 RAID 볼륨이었는가 | 1호 Part D · E4-1-a-6 입력 |
| **S** | Secure Boot | 별도 트랙 — 이번엔 **off** | wimboot 서명 · Secure Boot |

W1~W6 는 한 번의 완주(Run 1 · 단일 디스크)로 함께 판정된다. K1 · K2 는 Run 2(선택), **D1 · D2 · D4 는 Run 3(다중 디스크 · 반드시 Run 1 뒤)** 이다. D3(RAID 논리 볼륨에 setup 경로 설치 가능)은 1호에서 O 로 끝났다. Run 3 의 결과가 토론 Q3(디스크 선택 — 보류)을 확정해 E4-1-a-6 의 입력이 된다.

## 1. 배포 준비 (스테이징 VM · NAT 상태에서 먼저)

VM 은 09-03 원복 뒤 NAT(`spvadmin@192.168.1.10`)에 있다. 아래는 그 상태에서 한다. 절차의 SSOT 는 런북 `docs/staging-vm-bootstrap.md`(§8 배포 · §14 Windows) — 여기서는 이번에 달라지는 것만 적는다.

- [ ] **jar 재배포 — dev `4490695`**: 임시 워크트리(`git worktree add --detach ../ServerProvision-deploy-dev origin/dev`)에서 `./gradlew build`(3,038 green 확인) → `scp build/libs/ServerProvision-0.0.1-SNAPSHOT.jar spvadmin@192.168.24.128:/tmp/serverprovision.jar.new` → VM 에서 `sudo install -o provisioning -g provisioning -m 0640 /tmp/serverprovision.jar.new /opt/serverprovision/serverprovision.jar.new && sudo mv /opt/serverprovision/serverprovision.jar.new /opt/serverprovision/serverprovision.jar`(새 inode). 자동 모드가 scp · mv · DB · rm 을 한 줄에 묶으면 막는다 — 단계별로.
- [ ] **DDL**: E4-1-a-3 · -4 는 DDL 0. E4-1-a-2 의 `ddl/E4-1-a-2_windows_os_family.sql` 은 09-03 에 적용됐다(0행). 재기동이 `validate` 를 통과하면 끝 — `journalctl -u serverprovision -n 50` 에 `Started ServerProvisionApplication`.
- [ ] **env 9키** — `/etc/serverprovision/env`(0600)에 런북 §14-7 의 7키 + E4-1-a-4 의 2키. 값은 `/root/win2025-secrets.txt` 에서:
  - `WINDOWS_INSTALL_SOURCE_ROOT=/srv/pxe/win2025` · `WINDOWS_INSTALL_SHARE_UNC=\\192.168.1.10\win2025`(실기망 주소 — WinPE 가 붙는 곳) · `WINDOWS_INSTALL_SHARE_USER=deploy` · `WINDOWS_INSTALL_SHARE_PASSWORD=…` · `WINDOWS_PRODUCT_KEY_SERVERSTANDARD=<GVLK>` · `WINDOWS_TIME_ZONE`(기본값이면 생략) · `WINDOWS_INSTALL_TIMEOUT` · `WINDOWS_INSTALL_MAX_REENTRIES`(기본 60m · 5 — 생략).
  - `WINDOWS_INSTALL_SWEEP_INTERVAL` · `WINDOWS_INSTALL_SWEEP_GRACE`(기본 5m · 30m — Run 1 은 기본값. K2 에서만 바꾼다).
  - 공유 비밀번호에 `"%^&|<>()!` 가 있으면 준비도가 "배치 금지 문자" 로 막는다 — 있으면 `deploy` 비밀번호를 바꾼다(`smbpasswd deploy`).
- [ ] **`PXE_SERVER_BASE_URL` 확인** — 기존 실기(진단 · E3.5)가 통과한 값 그대로여야 한다. 이번엔 이 base 로 `wimboot` · `boot.wim`(542 MB) · 렌더본 셋이 내려가고 첫 로그온 보고도 이 주소로 온다. `https` 자체서명이면 iPXE 가 거부하므로 **http 주소**여야 한다(§3 에서 게스트가 닿는지 `curl -sI <base>/api/pxe/v1/boot` 로 본다).
- [ ] **`sources/$OEM$` 권한**(런북 §14-1): 09-02 실측이 손으로 둔 `$OEM$` 가 아직 있다 — **앱의 첫 조립이 그 안의 `$1` · `$$` 을 통째로 바꿔 끼우므로 먼저 백업한다**: `sudo cp -a '/srv/pxe/win2025/sources/$OEM$' /srv/pxe/oem-handmade-backup`. 그 뒤 `sudo chown provisioning:spvadmin '/srv/pxe/win2025/sources/$OEM$' && sudo chmod 2775 '/srv/pxe/win2025/sources/$OEM$'`.
- [ ] **드라이버 트리를 자원 저장소로**: 허용 루트(`PROVISION_ALLOWED_ROOTS=/var/lib/serverprovision/served/resources`) 아래 **보드별 경로** `Driver/GIGABYTE/{메인보드}/Windows/{드라이버종류}` 로 둔다(2026-09-04 사용자 지시). 실기 2호 = `Driver/GIGABYTE/MS04-CE0/Windows/Chipset`(GIGABYTE 전체 패키지 — 등록 트리 루트를 `Chipset` 로 잡으면 pnputil 이 하위 전부를 훑고 W2K16-x64 만 맞는 것이 잡힌다 · 정확히 실측 세트만 원하면 루트를 `Chipset/GIGABYTE/MS74-HB0/DriverFiles/production/W2K16-x64` 로) · `…/Windows/QAT`(icp_qat4). `sudo chown -R provisioning:provisioning`. `.provision.json` 은 등록 시 앱이 만든다.
- [ ] **`win2025-static` 내림**: `sudo systemctl disable --now win2025-static && sudo firewall-cmd --permanent --remove-port=8088/tcp && sudo firewall-cmd --reload`. wimboot · boot.wim 은 앱이 토큰 URL 로 낸다(E4-1-a-3). `smb` 는 유지.
- [ ] **wimboot 위치**: `ls -l /srv/pxe/win2025/wimboot`(v2.9.0 · 74.3 KB · 2011 CA). 대시보드 chip "wimboot SHA-256" 앞 12자 = `5f067ccdc4d0`.
- [ ] **함정 둘(2026-09-04 준비에서 확인 · 런북 §14-1 · §14-7 반영)**: ① `EnvironmentFile` 은 백슬래시를 이스케이프로 먹는다 — UNC 값은 `'\\192.168.1.10\win2025'` 처럼 **작은따옴표** ② 유닛 `ProtectSystem=strict` 라 POSIX 권한만으로는 `$OEM$` 에 못 쓴다 — drop-in `serverprovision.service.d/win2025-oem.conf` 의 `ReadWritePaths=/srv/pxe/win2025/sources` + daemon-reload.
- [ ] **재기동 · 대시보드**: `sudo systemctl restart serverprovision` → 맥에서 `https://192.168.24.128/system/asset` Windows 설치 소스 영역 — 서빙 활성 · 이미지 4종 · 슬롯 6 중 스크립트 둘은 아직 "파일 없음" · chip "드라이버 페이로드 미조립" · 공유 계정 설정됨 · 제품 키 ServerStandard 설정됨. 채증 `P1-dashboard-before.png`.

> **2026-09-04 15:40 KST Run 1 게스트 쪽 증거(맥 `~/Desktop/fieldwork-2/`)** — `spv-report.log`(첫 로그온 15:13:21 · `SPV-0F961A9D\Administrator` · `report accepted: HTTP 200 {"closed":true,"provisioningCompleted":true}` · 토큰은 사본에서 마스킹) · `setupcomplete.log`(고유 `oemNN.inf` **47** = 3호 실측과 동일 → F-W1 정정안(게시 이름 개수) 검증) · `devices.txt`(`Get-PnpDevice` Status≠OK **0건** = 문제 장치 0 을 언어 무관하게 재확인 · `Get-Disk`: Disk 0 = AVAGO MR9361-8i 479.6 GB GPT(RAID1), Disk 1~4 = AMI Virtual HDisk(BMC 가상 미디어 · 크기 0 · RAW) → **D 계열 메모: BMC 가상 디스크가 Windows 에 디스크로 열거되므로 E4-1-a-6 의 디스크 선택은 크기 0 · USBSTOR 을 걸러야 한다**). W6 O(수동 네트워크 부팅 → `exit` → 로컬 Windows · 로그온 확인). 비밀번호 혼선 1건: 정의서 10 이 화면 수정(PUT · 09:25 KST)으로 바뀌어 1호 값과 달랐음 — 실제 값은 VM 비밀 파일 3번째 줄.
> **2026-09-04 15:15 KST Run 1 결과** — W1 O(정의서 10 할당 · 개시 14:53) · W2 O(iPXE 번들 5 파일 · boot.wim 542 MB 6.5초 · WinPE 15:01:37) · W3 O(호스트명 SPV-0F961A9D · 개입 0) · W4 O(SetupComplete: chipset 175/184 · QAT 1 → DEV_4944 ×2 · 문제 장치 "시스템에서 장치를 찾을 수 없습니다" = 0) · W5 O(15:13:28 200 closed · 종단 · 토큰 404) · W6 = 수동 재부팅으로 확인 예정 · K1 미관측(부트 순서 디스크 우선 · 재진입 0). 서빙→완료 12분 37초. **F-W1**: 보고의 driversAdded=0 · problemDevices=0 은 `spv-report.ps1` 이 pnputil 영문 출력만 파싱해서(ko-KR 은 "추가된 드라이버 패키지:  175") — HF: `oem\d+\.inf` 개수 · `Get-PnpDevice` Status 로 언어 무관 파싱. 부팅 실패 1회 = 진단 REBOOT 뒤 펌웨어가 네트워크 부팅을 안 함(부트 순서) → 수동 네트워크 부팅으로 진행.
> **2026-09-04 14:50 KST 발견(F-R13)** — 등록 · 수집을 끝낸 미개시 게스트를 (RAID 볼륨 삭제 뒤) 다시 진단 리눅스로 부팅하면 재수집이 되지 않는다: 커서는 DIAGNOSTIC_BOOTING 으로 되돌아가고 지시는 WAIT(이미 enriched). 이 상태로 개시하면 소급 전진 조건(커서 = INFORMATION_PERSISTING)이 거짓이라 진단 phase 에 갇힌다. **우회 = [회수] → 재부팅 → 새 등록**. HF 적립(메모리 r13).
> **2026-09-04 09:10 KST 추가** — 드라이버 2 등록(사용자 · MS04-CE0 · Chipset id 2 · QAT id 3) 뒤 첫 [조립]이 **500**: 손조립 `$OEM$/$$` · `$1` 이 spvadmin 소유라 앱이 옮기지 못함(AccessDenied). `chown -R provisioning:spvadmin '$OEM$'` 뒤 재조립 성공 — 드라이버 2종 · 제외 0 · 18.6 MB · chip "최신 · 2종" · 슬롯 6/6. 런북 §14-1 에 반영. 코드 적립(HF 후보): `blockReason` 이 기존 자식 디렉토리의 쓰기 가능까지 보고 409 로 안내 · 스왑 실패 시 만든 `.old-<ts>` 정리.
> **2026-09-04 09:00 KST 진행 상황(세션 수행)**: §1 전부 완료 — jar dev 4490695 · env 5키 · `$OEM$` 백업(`/srv/pxe/oem-handmade-backup`) + 권한 + drop-in · 드라이버 트리는 보드별 경로 `/var/lib/serverprovision/served/resources/Driver/GIGABYTE/MS04-CE0/Windows/{Chipset, QAT}`(사용자 지시 형식 `Driver/GIGABYTE/{메인보드}/Windows/{드라이버종류}`) — Chipset 은 사용자가 넣은 GIGABYTE 전체 패키지(`GIGABYTE/MS74-HB0/DriverFiles/production/{W2K12R2-x64, W2K16-x64, Windows10-x64, Windows10-x86}` + Symbols), QAT 는 icp_qat4 · `win2025-static` 내림 · 대시보드 chip 확인(UNC `\\192.168.1.10\win2025` · 조립 버튼 활성). §2 의 보드(MS04-CE0 등 5) · OS/ISO(os 1 · iso 1) 확인 · 정의서 10 "실기 2호 Windows Standard" 생성. **남은 것 = 드라이버 자원 등록(사용자) → [드라이버 페이로드 조립] → 실기 서버의 기존 등록분 회수 여부 확인(§4).** 이미 실기망(192.168.1.10 · dhcpd 가동)이라 §3 은 완료 상태.

## 2. 자원 · 정의서 준비 (앱 화면 · NAT 상태)

- [ ] **보드 카탈로그**: `/management/board` 에 실기 서버 보드(09-02 실측 = **MS04-CE0**)가 있는가. 없으면 등록(게스트 부팅이 404 로 막힌다).
- [ ] **OS · ISO 자원**: `/management/os` 에 `WINDOWS_SERVER 2025` + ISO 가 있는가. 없으면 OS 등록 → ISO 는 VM 에 있는 실 ISO 경로로 등록(`curl -F isoPath=<VM 의 ISO 절대경로> -F description=… -F _allowCreateDirectory=on POST /management/os/{id}/iso` 또는 폼). 정의서의 Windows 축은 `isoId` 가 필수다.
- [ ] **드라이버 자원 2 등록**(kind DRIVER · 공용): 관리 화면 Subprogram → 드라이버 → "기존 디렉토리 등록" 으로 §1 의 두 트리를 이름 `Intel Chipset W2K16` · `Intel QAT icp_qat4` 로. 목록에서 활성 · 무결성 확인. 채증 `P2-drivers.png`.
- [ ] **[드라이버 페이로드 조립]**: 대시보드 Windows 영역 버튼 → flash "드라이버 2종 · <크기> · 제외 0" · chip "최신 · 2종" · 슬롯 6/6. VM 에서 `sudo ls -R '/srv/pxe/win2025/sources/$OEM$' | head -30` — `$1/SPV/Drivers/<id>_intel-chipset-w2k16` · `<id>_intel-qat-icp-qat4` · `$$/Setup/Scripts/SetupComplete.cmd` · `$1/SPV/spv-report.ps1` · `spv-oem-manifest.json`. `file …/SetupComplete.cmd` 가 CRLF. 채증 `P3-oem-assembled.png` · `P3-oem-tree.txt`.
- [ ] **정의서**: `/provisioning/setting` 에서 "실기 2호 Windows Standard" — OS 설치 = Windows Server 2025 · 이미지 `Windows Server 2025 SERVERSTANDARD`(데스크톱) · Administrator 비밀번호 입력. 다른 단계(펌웨어 · RAID)는 넣지 않는다 — 기존 RAID1(9361-8i)을 그대로 쓴다. 채증 `P4-definition.png`.
- [ ] (선택) 두 번째 정의서 "실기 2호 Windows Datacenter" — 제품 키 미설정이라 **준비도 BLOCKED 가 실기에서도 정직하게 뜨는지** 보는 용도(§5 W1-b).

## 3. 망 전환 (실기망 192.168.1.0/24)

런북 §14-5 의 절차 중 **`win2025-fieldwork.sh on` 은 쓰지 않는다** — 그 모드는 앱을 정지시키고 `boot.ipxe` 를 정적 `win.ipxe` 로 바꾸는 앱 통합 전 방식이다. 이번엔 앱이 켜진 채 기본 사슬(`boot.ipxe` → 앱 `/api/pxe/v1/boot`)로 간다.

- [ ] VM 정상 종료 → Fusion 어댑터를 USB LAN(`en7`) 브리지(vmnet3)로 → 기동 → `nmcli con mod enp2s0 ipv4.method manual ipv4.addresses 192.168.1.10/24 && nmcli con up enp2s0` → `sudo systemctl start dhcpd`(조각 next-server 1.10).
- [ ] `win2025-fieldwork.sh status` 가 **off**(앱 기동 · boot.ipxe = 앱 체인)인지. `sudo systemctl is-active serverprovision dhcpd tftp smb` 전부 active.
- [ ] 맥(같은 망)에서 `curl -sI <PXE_SERVER_BASE_URL>/api/pxe/v1/boot` 응답(400 이라도 도달) · `smbutil view //deploy@192.168.1.10` 에 `win2025`.
- [ ] `sudo journalctl -u serverprovision -f | grep -E 'wininstall|oem|boot'` 를 한 창에 띄워 둔다(채증 `L-app.log` 로 tee).

## 4. 실기 조건 · 안전 수칙

| 조건 | 이유 |
|---|---|
| Secure Boot **off** | S 트랙 별도. 2011 CA wimboot 거부 가능성은 E4-1-a-5 후속 |
| 부트 순서 **네트워크 우선**(Run 1) | K1(재진입 exit)을 정상 완주 안에서 함께 본다. 디스크 우선은 Run 2 |
| 디스크 = Run 1 · Run 2 는 기존 RAID1 볼륨 1개(디스크 0)만 | autounattend 는 `DiskID 0 · WillWipeDisk true` — 다른 디스크가 보이면 지울 수 있다(E4-1-a-6 전까지 불변). **데이터 디스크는 Run 3 에서만, 폐기 가능한 것만** 장착한다 |
| BMC KVM 열어 두기 | WinPE · Setup 화면 · 첫 로그온의 유일한 관찰 수단 |
| 앱 · dhcpd · tftp · smb 가동 상태 유지 | 실기 중 앱을 멈추면 게스트가 HOLD/실패로 빠진다 |
| 실기 게스트가 앱에 **이미 등록**돼 있으면 상태 확인 | 09-04 조회: 활성(미회수) 등록이 종단 상태로 남은 게스트가 셋 있다 — "UW260814001 (3)"(uuid 끝 `0f961a9d`) · "안양시 IPCC (하) (2)"(`0fc1733e`) · "아이티인포 (상) (1)"/"(하)"(`0b5ff2f`/`0b5ffe7`). 실기 서버의 systemUUID 가 이 중 하나면 종단 게스트라 /boot 가 `exit` 만 낸다 — 상세에서 **[회수]** 한 뒤 부팅해야 새로 등록(재투입 U6)된다 |

안전 수칙은 1호 §2 그대로: 데이터 디스크 미장착 · 자격증명 미기재 · 실패 시 무리한 반복 금지(Panther 로그 채증 후 다음).

## 5. Run 1 — 정상 완주 (W1~W6 · K1 동반)

시간 예산: 진단 자동 진행 약 3~5분 + PXE → 첫 로그온 11.5분(3호 실측) + 보고 · 종단 1분. **약 20분.**

| # | 조작 · 관찰 | 성립 | 채증 |
|---|---|---|---|
| R1-1 | 서버 전원 ON → PXE → 앱 등록 → 진단 리눅스 자동 진행(R13) → 상세 `/provisioning/server/<id>` 에 수집 완료 · 커서 INFORMATION_PERSISTING 대기 | 진단 완주 · RAID 인벤토리 카드에 9361-8i · 볼륨 1 | `R1-1-diag-done.png` |
| R1-2 **W1** | 상세에서 정의서 할당 → [프로비저닝 개시] → 커서 OS_INSTALLING · Windows 설치 카드 준비도 "문제 없음 — 다음 부팅에서 설치를 시작할 수 있습니다" | 카드 READY · 진행 "서빙 전" | `R1-2-card-ready.png` |
| R1-2b W1-b(선택) | Datacenter 정의서를 다른 게스트/재할당으로 → 카드 준비도 "제품 키 ServerDatacenter 미설정 — 환경변수"(BLOCKED · 결손 대기) | 화면 · iPXE 콘솔 `waiting for resources: product key missing for ServerDatacenter` | `R1-2b-blocked.png` |
| R1-3 **W2** | 게스트 재부팅(진단 완주 REBOOT) → iPXE 콘솔에 `kernel …/api/pxe/v1/windows/<token>/wimboot` · initrd 4 다운로드 진행 · boot.wim 542 MB 전송 → WinPE 부팅 | 콘솔 다운로드 4 + boot · WinPE 콘솔 `[ServerProvision] wpeinit done` | KVM 사진 `R1-3-ipxe-chain.png` · 앱 로그 "wimboot 체인 서빙 = 착수" · 카드 "설치 중 · 서빙 시각 · 재진입 0/5 · 잔여 60분" `R1-3-card-serving.png` |
| R1-4 | WinPE 배치: WaitForNetwork → net use N: (오류 53 재시도 ≤ 5분) → `setup.exe /unattend` 시작 | 콘솔 `share mounted at … (attempt n)` · `Starting setup /unattend` | KVM 사진 `R1-4-winpe-setup.png` · 시각 메모 |
| R1-5 **K1** | Setup 의 재부팅(2~3회) — 네트워크 우선이라 매번 PXE → 앱 → `windows setup in progress (reentry n/5)` → `exit` → 로컬 디스크 부팅 이어짐 | 콘솔 문구 · 카드 재진입 n/5 증가 · 원장 RUNNING 행 1개(meta reentries) | `R1-5-reentry.png`(콘솔) · 카드 `R1-5-card-reentry.png` · `SELECT status, JSON_EXTRACT(status_meta,'$.reentries') …` |
| R1-6 **W4** | 첫 로그온 전 SetupComplete.cmd(SYSTEM) — 드라이버 설치 · 로그 | 로그온 뒤 `C:\SPV\setupcomplete.log` 에 pnputil 루프 2회(chipset · qat) · `Added driver packages` · `problem devices:` 목록 비어 있음(92 → 0) | 로그 파일 사본 → spvout 또는 KVM 사진 `R1-6-setupcomplete-log.png` · 장치 관리자 `R1-6-devmgr.png` |
| R1-7 **W3** | 첫 로그온(Administrator 자동) · `hostname` = `SPV-<systemUUID 뒤 8>` · `C:\spv-firstlogon.txt` 존재 | 개입 0 · 이름 일치(상세 페이지의 systemUUID 와 대조) | `R1-7-hostname.png` |
| R1-8 **W5** | FirstLogonCommands 2 = `spv-report.ps1` → `C:\SPV\spv-report.log` 에 `reporting to <base>/api/pxe/v1/agent/windows/complete … drivers=n problems=0` · `report accepted: HTTP 200 {"closed":true,"provisioningCompleted":true,…}` | 앱 로그 "설치 완료 보고 : computerName=SPV-… drivers=… problemDevices=0 · 종단" · 카드 "완료 · 완료 시각 · ComputerName · 드라이버 n · 문제 장치 0" · 상태 배지 완료 · 이력 SUCCEEDED "설치 완료 · 드라이버 n · 문제 장치 0" | `R1-8-spv-report-log.png` · `R1-8-card-complete.png` · 옛 토큰 URL `curl -sI <base>/api/pxe/v1/windows/<token>/wimboot` → 404 `R1-8-token-404.txt` |
| R1-9 **W6** | 게스트 재부팅(수동) → PXE → 앱 → `#!ipxe` + `exit` → 로컬 Windows 부팅 | 콘솔 exit · 카드 재진입 증가 없음 · 원장 RUNNING 행 없음 | `R1-9-exit.png` |
| R1-10 | 대시보드 chip 여전히 "최신 · 2종"(설치가 페이로드를 바꾸지 않음) · 비밀값 부재: 상세 HTML · 앱 로그에 Administrator/공유 비밀번호 · 게스트 토큰 평문 0(`grep -c`) | 0건 | `R1-10-c4.txt` |

**막히면**: R1-3 에서 wimboot 다운로드 실패 → base URL(https · 방화벽) · Range 지원 확인. R1-4 에서 오류 53 5분 초과 → `WINDOWS_INSTALL_SHARE_UNC` 가 1.10 인지 · smb 서명 설정. R1-6 에서 SetupComplete 미실행 → 제품 키가 OEM 채널인지(GVLK 는 실행) · `$OEM$` 가 `sources` 바로 아래인지. R1-8 에서 보고 미도달 → `C:\SPV\spv-report.log` 의 `network wait` · HTTP 코드(404 = 토큰 · 409 = 상태 · 400 = JSON) · 앱 방화벽.

## 6. Run 2 — 재PXE 없는 게스트 · 실패 경로 (선택 · K2)

같은 게스트를 다시 쓴다(설치된 Windows 를 덮어쓴다).

- [ ] **준비**: 상세 [회수] 없이 재투입이 필요하면 U6 회수 · 재투입 → 할당 · 개시. env 를 임시로 `WINDOWS_INSTALL_TIMEOUT=20m` · `WINDOWS_INSTALL_SWEEP_GRACE=5m` · `WINDOWS_INSTALL_SWEEP_INTERVAL=1m` 으로 두고 재기동(카드 잔여 20분으로 확인).
- [ ] **부트 순서 디스크 우선**으로 바꾼다(BIOS/Redfish BootSourceOverride 는 이번 범위 밖 — BIOS 화면).
- [ ] **K2-1** 서빙(카드 설치 중) 직후 VM 에서 게스트의 완료 보고만 막는다: `sudo firewall-cmd --add-rich-rule='rule family=ipv4 source address=<게스트 IP> port port=<앱 포트> protocol=tcp drop'`(WinPE 의 SMB · 첫 다운로드가 끝난 뒤 — Setup 이 install.wim 을 다 읽은 뒤인 재부팅 시점이 안전). Setup 재부팅은 디스크 우선이라 재PXE 없음 → 앱 로그에 재진입 0.
- [ ] **K2-2** 첫 로그온의 `spv-report.ps1` 은 20 × 15 초 뒤 포기(`spv-report.log`). 서빙 + 20분 + 5분이 지나면 스윕이 `[wininstall] … 스윕 실패 전환 : served=… elapsed=…분` → 카드 "실패 · INSTALL_TIMEOUT" + "설치 시한을 넘겼습니다" · 원장 FAILED detail "재진입 · 완료 보고 없음, 스윕(시한 20분 + 유예 5분)" · 옛 토큰 URL 404. 채증 `R2-2-sweep.png` · `R2-2-app-log.txt`.
- [ ] **K2-3** 방화벽 규칙 제거 → 게스트에서 `spv-report.ps1` 을 손으로 다시 실행(`powershell -File C:\SPV\spv-report.ps1 -BaseUrl <base> -Token <토큰>`) → **409**(실패 상태의 지연 보고 — 정직한 거절) 확인 `R2-3-late-409.txt`.
- [ ] **K2-4** 카드 [재시도] → 커서 AWAITING_BOOT → 부트 순서를 네트워크 우선으로 되돌려 재PXE → 새 토큰 서빙(옛 토큰 404 유지) → 완주까지 두거나 여기서 중단.
- [ ] env 를 기본값으로 되돌리고 재기동.

## 6-2. Run 3 — 다중 디스크 판정 (D1 · D2 · D4 · E4-1-a-6 입력)

**전제**: Run 1 이 O 로 끝난 서버. 장착하는 데이터 디스크는 **전부 폐기 가능**해야 한다 — D4 는 "지워지는가" 를 보는 실험이라 지워질 수 있다. 가능하면 **같은 모델 2개**(D2)와 RAID 볼륨과 크기가 다른 것 1개를 섞는다. 시간 예산 = 디스크 준비 10분 + 완주 20분.

| # | 조작 · 관찰 | 성립 · 판정 재료 | 채증 |
|---|---|---|---|
| R3-0 표식 | Run 1 으로 설치된 Windows 에서(또는 다른 장비에서) 데이터 디스크를 초기화해 파티션 1개씩 만들고 루트에 `SPV-MARKER-<n>.txt`(내용 = 디스크 시리얼) 를 둔다. PowerShell `Get-Disk \| Format-List Number,FriendlyName,SerialNumber,UniqueId,Size,PartitionStyle` 저장 | 각 디스크의 시리얼 · UniqueId · 크기가 기록됨 | `R3-0-disks-before.txt` · 사진 |
| R3-1 재투입 | 서버 종료 → 데이터 디스크 장착 → 앱에서 그 게스트 [회수] → 부팅 → 재등록 → 진단 자동 진행 → 상세 인벤토리의 **디스크 목록(장치 · 크기 · 시리얼 · 순서)** 과 RAID 인벤토리 카드(볼륨 · 멤버 슬롯) 채증 | 진단 리눅스가 본 열거 = `lsblk` 순서 | `R3-1-inventory.png` |
| R3-2 **D1** | 할당 · 개시 → wimboot 체인 → WinPE 배치가 콘솔에 찍는 `diskpart list disk` 표(번호 · 크기 · 상태)를 사진으로 | 번호 순서가 R3-1 의 순서와 같은가 · **Disk 0 이 RAID 볼륨인가** | KVM 사진 `R3-2-diskpart.png` |
| R3-3 **D4** | 그대로 설치를 진행시킨다(Setup 이 DiskID 0 을 지우고 설치) → 완주 → 설치된 Windows 에서 `Get-Disk` · `Get-Volume` · 각 데이터 디스크의 `SPV-MARKER-<n>.txt` 생존 확인 | ① OS 가 RAID 볼륨에 들어갔는가 ② 데이터 디스크 표식이 전부 살아 있는가(`WillWipeDisk` 가 DiskID 0 만 지우는가) ③ Disk 0 이 데이터 디스크였다면 그 디스크가 지워지고 OS 가 거기 들어갔음을 그대로 기록(이것이 D3 채택안 ①+③ 의 근거) | `R3-3-disks-after.txt` · `R3-3-markers.png` |
| R3-4 **D2** | 같은 모델 2개의 `Get-Disk` 출력에서 SerialNumber · UniqueId · Disk ID(`diskpart` `detail disk`)가 서로 다른지 · 진단 리눅스 인벤토리의 시리얼과 같은 값인지 | 앱이 저장한 시리얼로 Windows 쪽 디스크를 지목할 수 있는가 | `R3-4-identity.txt` |
| R3-5 | (선택) 부트 순서를 바꾸지 않고 재부팅 → DiskID 가 재부팅 뒤에도 같은 디스크를 가리키는가(열거 안정성) | `diskpart list disk` 재확인 | `R3-5-stability.png` |

**판정이 만드는 것** — Q3(디스크 선택)의 채택안: D1 이 일치하고 Disk 0 이 늘 RAID 볼륨이면 서버 사전 계산(②)이 가능하고, 어긋나면 WinPE 배치의 크기 매칭 + 대상만 clean(①+③)이 필요하다. D2 의 식별 키는 `RaidVolume.wwn` · 진단 시리얼과 Windows `UniqueId` 를 잇는 근거다. 결과를 §9 에 적으면 세션이 E4-1-a-6 CP1 의 입력으로 옮긴다.

## 7. 함께 볼 수 있는 것 (선택)

- **E4-1-a-2 CP7**(정의서 화면): §2 의 정의서 생성 · 수정(비밀번호 유지 체크 · 소스에 없는 이미지 placeholder)이 이미 W1 로 덮인다. 목록 · 상세의 Windows 축 표시 스크린샷 1장이면 충분.
- **T3 잔여(E1 · U4 · E3)**: 같은 서버에서 `lspci -nn`(9361-8i Subsystem) · `dmidecode` placeholder · DIMM Locator 표기는 진단 리눅스가 이미 수집한다 — 상세 페이지 인벤토리에서 값을 읽어 T3 의 해당 줄에 `[x]` 로 옮길 수 있다.
- **S 트랙**: 완주 뒤 Secure Boot 를 켜고 R1-3 만 반복 — wimboot(2011 CA) 거부 여부 1건. 거부되면 콘솔 오류 문구만 채증(E4-1-a-5 후속 결정 재료).

## 8. 채증 규율

- 파일명 = 위 표의 식별자(`R1-3-ipxe-chain.png` …). KVM 은 사진 · 앱 화면은 스크린샷 · 로그 · DB 는 `.txt`.
- 게스트 안 파일(`C:\SPV\setupcomplete.log` · `spv-report.log` · `C:\Windows\Panther\setupact.log` · `setuperr.log`)은 spvout 공유(`\\192.168.1.10\spvout`)로 복사하거나 KVM 사진.
- 앱 쪽: `sudo journalctl -u serverprovision --since "<시작 시각>" | grep -E 'wininstall|oem|boot' > L-app.txt` · 원장 `sudo mariadb server_provision -e "SELECT step_code,status,started_at,finished_at,JSON_EXTRACT(status_meta,'$.reason'),JSON_EXTRACT(status_meta,'$.reentries'),JSON_EXTRACT(status_meta,'$.driversAdded'),JSON_EXTRACT(status_meta,'$.problemDeviceCount') FROM provisioning_history WHERE step_code='OS_INSTALLING' ORDER BY created_at"` → `L-ledger.txt`.
- 비밀값 · 토큰 값이 섞인 줄은 `****` 로 지우고 저장한다(`spv-report.log` 의 명령줄 · autounattend 사본).

## 9. 판정표

| 식별자 | 판정(O · X · 부분) | 관찰 한 줄 | 채증 |
|---|---|---|---|
| W1 정의서 · 할당 · 개시 | O | 정의서 10 할당 · 14:53 개시 → 커서 OS_INSTALLING(AWAITING_BOOT) | DB 폴링 · 카드 |
| W2 wimboot 체인 | O | 15:00:35 boot.ipxe → 토큰 URL 5 파일 200(boot.wim 542 MB 6.5초) → WinPE DHCP 15:01:37 | 앱 로그 · dhcpd |
| W3 무인 완주 · hostname | O | 개입 0 · DHCP 호스트명 SPV-0F961A9D 15:12 · 보고 computerName 일치 | dhcpd · spv-report.log |
| W4 $OEM$ · SetupComplete · 문제 장치 | O | 앱 조립본으로 SetupComplete 실행 · 게시 `oemNN.inf` 47 · QAT → DEV_4944 ×2 · 문제 장치 0(pnputil ko · Get-PnpDevice 0건) | setupcomplete.log · devices.txt |
| W5 완료 보고 · 카드 · 토큰 404 | O | 15:13:28 HTTP 200 closed · 종단 · 토큰 HEAD 404 · 카드 "완료 · SPV-0F961A9D" | 앱 로그 · curl · 카드 |
| W6 종단 exit | O | 수동 네트워크 부팅 → `exit` → 로컬 Windows 로그온 | 사용자 확인 |
| K1 재진입 exit · n/5 | 미수행 | 부트 순서 디스크 우선 — Setup 재부팅이 재PXE 로 안 옴(재진입 0) | Run 2 |
| K2 스윕 · 지연 보고 409 · 재시도 | 미수행 | | Run 2 |
| D1 lsblk ↔ diskpart 순서 · Disk 0 = RAID 볼륨? | 부분 | 데이터 디스크 없이 Disk 0 = RAID1 확인 · BMC 가상 미디어 4개가 Disk 1~4(크기 0) | devices.txt |
| D2 동일 모델 식별 키 | 미수행 | | Run 3 |
| D4 파괴 범위(표식 생존 · OS 위치) | 미수행 | | Run 3 |
| S Secure Boot(선택) | 미수행 | | |

## 9-1. 2026-09-04 마감 상태 (Run 1 완료 · Run 2 · 3 미수행)
- VM 은 실기 모드 그대로(192.168.1.10 · dhcpd 가동 · env Windows 5키 · drop-in · 정의서 10 · `$OEM$` 조립본 2종). 다음 회차는 §1 없이 §3 확인부터 시작하면 된다. 스테이징(NAT)으로 돌릴 때는 §10 첫 항목.
- 게스트 "win test (1)"(MS04-CE0)은 종단 상태로 남아 있다 — 다음 회차 전에 [회수]. Windows 가 RAID1 에 설치돼 있다(Run 3 의 표식 작업에 쓸 수 있다).
- 결함 · 단계(2026-09-04 사용자 승인으로 Notion 신설): **HF11** Windows 실기 2호 파생 결함 묶음 — HF11-1 보고 파서 언어 무관화(F-W1) · HF11-2 진단 재진입 재수집 · 개시 소급 판정(F-R13) · HF11-3 `$OEM$` 조립 사전 판정 · 잔여 정리(F-OEM). 별도: **HF12** 정의서 저장 결함 2(updated_at · 리눅스 keep 미병합) · **HF13** 시각 의존 테스트 Clock 주입. F-3(flash) 는 HF9 에 4번째 지점으로 합류 · UI 문구 적립분은 S16-2 에 합류. 관찰(BMC 가상 디스크 열거)은 E4-1-a-6 입력.
- 세션 교훈(메모리): 실기 인스턴스에서 `/boot` 확인 호출 금지 · journalctl `-u`/`-t` 혼용 금지 · VM 시계 NTP 없음(격리망) → 서빙 전 맥 시각으로 맞춤.

## 10. 끝난 뒤

- [ ] 망 원복(런북 §14-5 역순): dhcpd stop → enp2s0 DHCP → 정상 종료 → 어댑터 NAT → 기동 → `192.168.24.128`. env 임시값을 썼으면 기본값으로.
- [ ] `/srv/pxe/oem-handmade-backup` 은 판정 O 뒤 삭제(앱 조립본이 정본).
- [ ] 채증 묶음과 §9 표를 세션에 전달 → 세션이 T3 E4 절 7항목을 `[x]` 로 옮기고, E4-1-a-2 · -3 · -4 의 Notion 종료 경계(상태 완료 · 종료 일자)와 E4-1-a-5 원장 기입을 한다. 결함이 나오면 HF 로 갈아탄다(발견한 스트림에서).
- [ ] 후속 판단 재료: K2 결과에 따라 스윕 유예 기본값(30분) 유지 여부 · S 트랙 결과에 따라 wimboot 2023 CA 판 필요 여부 · **Run 3(D1 · D2 · D4) 결과로 토론 Q3 확정 → E4-1-a-6 디스크 선택 계약 CP1**.
