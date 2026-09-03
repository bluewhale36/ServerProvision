# Windows Server 2025 무인 설치의 앱 통합 설계 — 토론 1호 (전체 그림 · 로드맵 · 열린 질문)

> **문서 종류**: discussion(코드 착수 전 토론 자산 · plan/report 대체 아님). 시리즈 첫 문서라 전체 그림과 로드맵을 담는다. 후속 문서는 쟁점 · 응답만 담는다.
> **작성**: 2026-09-02 KST, 앵커 세션. **입력**: 실측 1호 보고서(`report/26-09-02_14-14-54_WIN-fieldwork-1_report.html`) · 조사 브리핑(`discussion/26-08-20_09-10-41_…briefing.md`) · OS 섹션 3축 정리(`discussion/26-08-20_08-26-28_…discussion.md` D4).
> **전제(사용자 확정 2026-09-02)**: MVP 완료 조건 = RAID 자동화(E3.5) 완료 → Windows Server 2025 자동 설치. 설치 목적은 하드웨어 stress test(PassMark BurnInTest)이며 활성화 상태는 무관(GVLK 유지).
> **원장**: Notion 단계 페이지는 단계 코드 확정 후 신설(임의 신설 금지 — Q1). 그 전까지 이 문서의 열린 질문 절에서 토론한다.

---

## 1. 실측이 확정한 계약 (여기서부터는 바꾸지 않는다)

실측 1호(2026-09-02 · MS04-CE0 · 9361-8i RAID1)에서 아래가 실물로 성립했다. 앱 통합은 이 사슬을 **코드로 옮기는 일**이고, 사슬 자체를 다시 설계하지 않는다.

| 구간 | 확정 내용 | 근거 |
|---|---|---|
| 부팅 사슬 | iPXE → `wimboot`(v2.9.0 · 2011 CA 서명) → WinPE(boot.wim index 2) → 주입된 `winpeshl.ini` 가 setup 자동 실행을 선점 → `install.bat` | 1차 U3 · U4 · U6 |
| 설치 소스 | Samba 읽기 전용 공유(SMB3 · 서명 필수)의 `sources\setup.exe /unattend:X:\…\autounattend.xml` | 1차 N1 · N3 · 5차 U1 |
| 응답 파일 | windowsPE(디스크 · 이미지) · specialize(이름 · 시간대) · oobeSystem(비밀번호 · 자동 로그온 · OOBE 숨김 · FirstLogonCommands) 전 pass 처리 | 5차 · 3호 U1 |
| 정식 미디어 | `<ProductKey>` 필수 — 없으면 setup 중단. 미활성 무인 설치 = GVLK | 1차 U7 |
| WinPE 배치 규칙 | Setup PE 에 `where` · `findstr` 없음(내장 명령만) · `winpeshl.ini` 는 `cmd /k`(배치 종료 = WinPE 재시작 방지) · `net use` 는 `wpeutil WaitForNetwork` + `LanmanWorkstation` 재시작 + 재시도(네트워크 후 약 60초에 성공) · ASCII 메시지 | F-1 · F-2 · F-3 · F-5 |
| 드라이버 | `sources\$OEM$\$1\SPV\Drivers\<묶음>` 의 INF 세트 + `$$\Setup\Scripts\SetupComplete.cmd` 의 `pnputil /add-driver … /subdirs /install` → 문제 장치 92 → 0 | 2호 · 3호 |
| 증거 회수 | 첫 로그온 `FirstLogonCommands` 가 스크립트를 돌려 결과를 남김(실측은 쓰기 공유 · 설치된 OS 에는 `curl.exe` 내장) | 2호 · 3호 |
| RAID 볼륨 설치 | 9361-8i RAID1 논리 볼륨(디스크 0)에 setup 경로로 설치 성공 — DISM 분기 불요 | D3 |
| 소요 | PXE → 바탕화면 약 11분 30초(boot.wim 542 MB 전송 40초 · install.wim 4.8 GB 적용 4.5분) | 3호 N4 |

## 2. 현행 앱과의 접점 (코드 실태)

- 큰 단계 `ProvisioningPhase` 에 **`OS_INSTALLING("OS 설치")` 은 이미 있다**(`OS_SETTING` · `TESTING` 도). 실행기 SPI `ProvisioningPhaseExecutor { phase(); bootScript(GuestServer, ProvisioningProgress, rebootQuery) }` 의 구현체는 `DiagnoseLinuxExecutor` · `FirmwareUpdatingExecutor` · `FirmwareSettingExecutor` · `RaidConfigurationExecutor` 넷 — **OS_INSTALLING 실행기는 없다.** `BootScriptDispatcher` 가 `/api/pxe/v1/boot` 에서 현재 phase 의 실행기에게 부트 스크립트를 묻는다.
- 게스트 보고 채널 `GuestAgentRestController`: `/api/pxe/v1/agent/checkin` · `/steps` · `/steps/{stepId}/close` — 진단 리눅스 에이전트가 쓴다. Windows 첫 로그온이 같은 계약으로 보고할 수 있는지가 설계 포인트(§4 D4).
- 자산 서빙 선례 `FirmwareImageRestController /api/pxe/v1/firmware/{token}/{fileName}` — 토큰 레지스트리로 파일을 흘린다. wimboot · boot.wim · ini · bat · autounattend 를 같은 방식으로 HTTP 서빙할 수 있다. **설치 소스(sources 4.8 GB + `$OEM$`)는 setup.exe 가 SMB 로 읽어야 하므로 앱 HTTP 로는 대체할 수 없다** — Samba 는 앱 밖 서비스로 남는다(§4 D5).
- 정의서: R11 로 `OS_INSTALLATION` 은 식별 전용(`OSInstallationRequest` = `osMetadataId` · `isoId`, 계열 판별자 `osFamily`). `provisioning.setting.enums.OSFamily` 는 `RHEL_BASED` · `DEBIAN_BASED` 둘 — **`WINDOWS` 가 없다.**
- 자원: OS ISO 는 Management 에 등록됨(실측의 ISO #1 = `served/resources/OS/Windows/Server/2025/…iso`). `Subprogram(kind DRIVER · UTILITY · boardModel FK · treeRootPath · entrypointRelativePath · 무결성 마커)` 이 드라이버 자원의 자리다.
- RAID 인계: `RaidVolume(volumeRole = OS · name spvR{r}V{k} · usableBytes · wwn)` 이 게스트별로 저장돼 있다 — Windows 의 `DiskID` 로 옮기는 매핑이 미결(§4 D3 · Q3).
- 재부팅 · 재PXE: E2.5 가 단계 진입 시 Redfish 로 **네트워크 부팅을 1회 강제**한다. Windows setup 이 스스로 하는 재부팅에는 그 강제가 없으므로 펌웨어 부팅 순서(UEFI 는 새 Windows Boot Manager 를 앞에 둔다)를 따른다 — 실측에서는 사용자가 확인하며 진행했다.

## 3. 유스케이스 (사용자 관점)

1. 관리자가 Windows Server 2025 ISO 를 OS 자원으로 등록한다(이미 가능). 앱(또는 운영 절차)이 그 ISO 에서 설치 소스를 꺼내 Samba 공유 아래에 둔다.
2. 관리자가 보드별 드라이버 INF 묶음(칩셋 · QAT …)을 Subprogram(DRIVER) 자원으로 등록한다.
3. 사용자가 세팅 정의서에 "OS 설치 = Windows Server 2025 Standard(Desktop Experience)" 단계를 넣고, RAID 구성 단계와 함께 저장한다. 비밀번호 · 시간대 · 제품 키는 전역 운영 설정에서 온다(3축 정리).
4. 게스트가 PXE 로 올라와 진단 → 펌웨어 → RAID 를 마치고 `OS_INSTALLING` 에 들어오면, 앱이 wimboot 부트 스크립트를 내주고 게스트별 `autounattend.xml`(이름 · 디스크 · 이미지 · 보고 토큰)을 함께 주입한다.
5. WinPE 배치가 공유의 setup 을 돌리고, setup 이 `$OEM$` 을 복사하고 재부팅한다. 이때 게스트는 디스크로 부팅한다(재PXE 되면 앱이 로컬 부팅으로 돌려보낸다).
6. SetupComplete 가 드라이버를 넣고, 첫 로그온이 앱에 "설치 완료 · 문제 장치 n건" 을 보고한다. 앱은 단계 원장을 닫고 다음 단계(TESTING = BurnInTest)로 커서를 옮긴다. 사용자는 게스트 상세에서 시각 · 결과 · 문제 장치 목록을 본다.

## 4. 설계 결정 후보 (채택안 · 대안 · 탈락 사유)

### D1. 실행기 — `WindowsInstallingExecutor implements ProvisioningPhaseExecutor`(phase = OS_INSTALLING)
- `bootScript()` 가 wimboot 체인 스크립트를 낸다: `kernel <wimboot>` · `initrd winpeshl.ini · install.bat · autounattend.xml(게스트별) · boot.wim` · `boot`. 정적 자산(wimboot · boot.wim · ini · bat)은 토큰 서빙(선례 재사용), `autounattend.xml` 은 게스트별 렌더링.
- **재PXE 처리**: 같은 게스트가 OS_INSTALLING 중 다시 `/boot` 를 물으면 — 직전에 WinPE 스크립트를 내줬고 실패 보고가 없으면 — `exit`(로컬 부팅)로 돌려보낸다. 세 번째 재PXE 는 HOLD 로 세우고 게스트 상세에 사유를 띄운다(무한 재설치 루프 차단).
- 대안(탈락): OS 계열별 실행기를 하나로 합쳐 분기 — 리눅스(kickstart) 경로와 Windows 경로는 자산 · 스크립트 · 보고가 전부 달라 분기문만 자란다. 계열별 실행기가 저장소 규칙(다형성)에 맞다.

### D2. 자산 — 셋으로 나눈다
| 자산 | 어디서 | 누가 |
|---|---|---|
| `wimboot` | 진단 이미지처럼 **앱 자산**(버전 · 서명 CA 기록 · 무결성 마커) | 앱 |
| `boot.wim` · `sources\`(install.wim 포함) | 등록된 OS ISO 에서 추출해 **Samba 공유 루트**에 둔다. boot.wim 은 HTTP 로도 서빙 | 추출 = 앱 or 운영 절차(Q9) · 서빙 = Samba(OPS) |
| `$OEM$` | Subprogram(DRIVER) 자원의 INF 트리를 `Drivers\<자원명>` 으로 모으고, `SetupComplete.cmd` · 첫 로그온 스크립트는 앱이 생성해 공유 루트의 `sources\$OEM$` 에 배치 | 앱이 조립 → 공유에 기록 |
- `$OEM$` 의 범위(Q2): **전 보드 공용 한 벌**(등록된 Windows 드라이버 전부 · pnputil 은 맞는 장치만 붙이고 나머지는 드라이버 저장소에만 남음)이 단순하다. 보드별 소스 디렉토리를 만들면 공유 · 심링크 · 정합이 늘어난다. 게스트별 차이는 전부 `autounattend.xml` 과 첫 로그온 스크립트(앱에서 `curl` 로 받음)로 흡수한다.

### D3. 디스크 선택 — 미결(실측 D1 · D2 필요)
- 실측은 디스크 1개(RAID1 볼륨 = 디스크 0)라 `DiskID=0 · WillWipeDisk=true` 로 통과했다. 데이터 디스크가 함께 꽂힌 서버에서는 **DiskID 가 순서 번호에 불과해 데이터 디스크를 지울 수 있다**(조사 §4-1). RAID 계획의 OS 영역 볼륨(`RaidVolume.volumeRole = OS` · `usableBytes` · `wwn`)을 Windows 디스크 번호로 옮기는 근거가 필요하다.
- 세 안: ① **WinPE 배치가 `diskpart list disk` 를 파싱해 크기(usableBytes)로 대상 디스크를 찾고 그 번호를 응답 파일에 치환**한 뒤 setup 을 부른다(응답 파일은 X: 에 있어 배치가 고칠 수 있다 · 내장 명령 `for /f` 로 가능) ② 진단 리눅스가 수집한 디스크 열거와 WinPE 열거의 순서 일치(D1)를 실측으로 확인해 서버가 DiskID 를 미리 계산 ③ `WillWipeDisk` 대신 배치가 대상 디스크만 `diskpart clean` 하고 응답 파일은 파티션 생성만(판정이 틀리면 setup 이 멈추게 `WillShowUI=OnError` 유지).
- 권고: ①+③ 조합(크기 매칭 · 대상만 clean · 나머지 보존). 동일 크기 디스크가 여럿이면 실패로 멈추고 사람에게 넘긴다(최소 파괴 원칙). D1 · D2 · D4 실측(다중 디스크 회차)이 선행 — **E4-W-4 를 실측 뒤에 둔다.**

### D4. 완료 보고 — 첫 로그온 `curl` POST
- 설치된 Windows 에는 `curl.exe` 가 내장돼 있다(2호에서 사용). 첫 로그온 스크립트가 `POST /api/pxe/v1/agent/…`(게스트 토큰 동봉)로 "설치 완료 · 문제 장치 목록 · setupcomplete 로그" 를 보고한다. 기존 `steps/{stepId}/close` 계약을 그대로 쓸 수 있으면 재사용, Windows 쪽 보고 항목(문제 장치 수 등)이 다르면 payload 만 다형으로 확장.
- 대안(탈락): 쓰기 공유(`spvout`)에 파일로 남기고 앱이 폴링 — 실측용으로는 충분했으나 앱 상태 전이의 트리거로는 HTTP 보고가 명확하다.
- WinPE 단계의 보고(SETUP_STARTED)는 `curl` 이 없어 불가(N5 미확인) → iPXE 가 부트 스크립트 안에서 `imgfetch http://app/…/mark` 로 한 번 찍는 방식(dispatcher 의 GET 힌트)으로 대체 가능. 필요 여부는 D1 의 재PXE 판정 정밀도에 달린다.

### D5. Samba — 앱 밖 운영 서비스(OPS)
- 앱은 SMB 를 서빙하지 않는다. 공유 UNC · 계정 · 비밀번호는 앱 설정(환경변수)으로 받아 `install.bat` 렌더에만 쓴다. 공유 자체(패키지 · 계정 · 서명 필수 · 방화벽 · SELinux)는 스테이징 VM 런북에 절로 추가(OPS 계열). 비밀번호가 서빙되는 배치 파일에 평문으로 실리는 것은 현행 PXE 자산과 같은 노출 수준 — 격리망 전제를 명시.

### D6. 라이선스 · 비밀번호 · 시간대 — 전역 운영 설정
- 3축 정리(정의서 = 무엇을 / 게스트 = 어디에 / 전역 = 운영 설정)대로 `ProductKey`(기본 GVLK) · 표준 Administrator 비밀번호(Base64 인코딩은 서버가) · 시간대 · 로케일은 정의서가 아니라 **전역 설정**에 둔다. 저장 위치(설정 화면 vs 환경변수)는 Q7.

### D7. 정의서 — `OSFamily.WINDOWS` + `WindowsInstallationRequest`
- `OSInstallationRequest` 의 계열에 `WINDOWS` 를 더하고 `WindowsInstallationRequest(osMetadataId · isoId · edition = /IMAGE/NAME)` 를 둔다. R11 이 숨긴 상세(타임존 · 사용자 · 파티션)는 되살리지 않는다 — Windows 는 전역 설정과 RAID 계획이 그 자리를 채운다. 에디션은 ISO 의 이미지 목록에서 고르게 하면(WIM XML 파싱은 실측에서 wimlib 없이 해냄) 오타가 없다.

### D9. 유지보수 층 분류 — 누가 무엇을 관리하는가 (2026-09-02 사용자 우려에 대한 답)
RHEL 은 "정의서 → ks 파일 + ISO" 둘로 끝났다. Windows 는 파일이 늘어 보이지만, **사용자가 손대는 것은 셋(ISO · 드라이버 · 키)** 이고 나머지는 앱 안에 고정된다. 층별로 나누면:

| 층 | 무엇 | 누가 · 언제 | Windows 새 버전이 나오면 |
|---|---|---|---|
| A 앱 내장 템플릿(코드와 함께 배포) | `win.ipxe` · `winpeshl.ini` · `install.bat`(WinPE 5계명 코드화) · `SetupComplete.cmd` · 첫 로그온 보고 스크립트 · `autounattend.xml` 골격(windowsPE/specialize/oobeSystem) | 개발 · 앱 릴리스 | 그대로. 응답 파일 컴포넌트 이름은 Vista 이후 동일, Setup PE 내장 명령 집합도 안정 |
| B 버전 자원(Management · 버전당 1회) | ISO(이미 OS 자원) → 앱이 `boot.wim` · `sources\` 추출 · WIM XML 에서 에디션 목록 자동 채집 / 에디션 → 키 표(GVLK 공개값 기본 · 사용자 키 override) | 관리자 · ISO 등록 시 | ISO 1개 등록 + 키 표 1행. 이름 타이핑 없음(자동 채집) |
| C 보드 자원(Management · 보드 도입 시) | 드라이버 INF 묶음 = Subprogram(DRIVER) → 앱이 `$OEM$\Drivers\<자원명>` 로 조립 | 관리자 · 보드 · 칩셋 도입 시 | 벤더가 새 OS 용 패키지를 내면 재등록. RHEL 에는 없던 층 — Windows 인박스 커버리지가 낮아 생긴 것이며 자원 등록으로 정형화 |
| D 정의서(사용자) | OS · ISO 선택(현행) + **에디션**(B 의 목록에서 선택) · 나머지는 전역 기본 | 사용자 · 정의서 작성 시 | 변화 없음 |
| E 게스트별 자동 값(서버 계산) | ComputerName · DiskID(RAID 계획 OS 영역 ↔ D3) · 보고 토큰 · 공유 접속 정보 치환 | 앱 · 부팅 시 렌더 | 변화 없음 |
| F 전역 운영 설정 | 표준 Administrator 비밀번호 · 시간대 · 로케일 · 에디션→키 표 · Samba 공유 UNC/계정 | 운영자 · 1회 | 키 표 행 추가만 |
| G 운영 인프라(OPS · 1회) | Samba 공유(setup.exe 가 install.wim 을 파일 경로로 읽어야 해 HTTP 로 대체 불가) · `wimboot` 자산(진단 이미지처럼 버전 · 서명 관리) | 운영자 · 배포 시 | Samba 그대로. wimboot 는 boot.wim 형식이 바뀔 때만 갱신(24H2 `boot.stl` 사례) |

**새 버전 도입 절차(목표 = 코드 변경 0)**: ① ISO 등록 → 추출 · 에디션 채집 자동 ② 키 표 1행 ③ 드라이버 자원 재확인 ④ 실측 1회(U1 · WinPE 규칙 회귀 — 반나절, 새 setup 엔진 변화의 게이트) ⑤ 끝.
**정의서 OS 설치 섹션이 "조립" 할 수 있는가** — 예. 정의서가 주는 것은 ISO · 에디션뿐이고, 응답 파일 · 배치 · 부트 스크립트는 A 층 템플릿에 D · E · F 의 값을 치환해 서버가 만든다. RHEL 의 ks 생성과 같은 자리(`OS_INSTALLING` 실행기)에서 한다.

### D8. TESTING 단계 — BurnInTest(별도 슬라이스)
- 첫 로그온 훅 다음 자리. 무인 설치(Inno Setup) · `.bitcfg` · `-r` · 결과 로그 회수. 이 문서의 범위 밖, 로드맵에만 둔다.

## 5. 로드맵 · 슬라이스 분할 (제안)

| 코드 | 계열 | 내용 | 선행 |
|---|---|---|---|
| E4-1-a-1 | OPS | Samba 공유 · 설치 소스 추출 · wimboot 자산 배치 · 방화벽 — 런북 절 + 결정 | — |
| E4-1-a-2 | U | `OSFamily.WINDOWS` · `WindowsInstallationRequest`(에디션) · 전역 운영 설정(키 · 비밀번호 · 시간대) | E4-1-a-1 결정 |
| E4-1-a-3 | E | `WindowsInstallingExecutor` · wimboot 부트 스크립트 · 게스트별 autounattend/install.bat 렌더 · 토큰 자산 서빙 · 재PXE 로컬 부팅 | E4-1-a-2 |
| E4-1-a-4 | E | `$OEM$` 조립(Subprogram DRIVER → Drivers · SetupComplete · 첫 로그온 스크립트) · 완료 보고 엔드포인트 · 게스트 상세 표시 | E4-1-a-3 |
| E4-1-a-5 | 실기 | 다중 디스크(D1 · D2 · D4) · Secure Boot(S1~S4) · 두 번째 보드(MS74) | E4-1-a-4 전 또는 병행 |
| E4-1-a-6 | E | 디스크 선택 계약(D3 채택안 구현) | E4-1-a-5 |
| E5-W(가칭) | E | TESTING = BurnInTest | E4-1-a-4 |

**코드 확정(2026-09-02 사용자)**: Notion 의 `E4-1-a : Windows Server 2025` 하위로 `E4-1-a-1`~`E4-1-a-6`(기존 `E4-1-a-0 : 선행 조사` 다음). 가칭 E4-W-* 는 폐기. BurnInTest(E5-W 가칭)는 TESTING 단계라 E4-1-a 밖에서 별도 확정.

## 6. 열린 질문 (토론 포인트)

- **Q1 단계 코드 · 이름** — **확정(09-02)**: `E4-1-a` 하위 `E4-1-a-1`~`E4-1-a-6`. Notion 페이지 신설 완료.
- **Q2 `$OEM$` 범위** — 전 보드 공용 한 벌(권고) vs 보드별 소스 디렉토리. 공용이면 드라이버 저장소에 미사용 패키지가 쌓이는 것을 감수. → **확정(09-02 · 권고안)**: 전 보드 공용 한 벌.
- **Q3 디스크 선택** — D3 의 ①+③(권고) vs ② 서버 사전 계산. 어느 쪽이든 다중 디스크 실측이 먼저. → **보류(09-02)**: 실측 2호(E4-1-a-5) 결과로 확정 — E4-1-a-6 의 입력.
- **Q4 재PXE 처리** — E2.5 의 1회 강제에 기대고 dispatcher 는 `exit` 폴백(권고) vs 설치 중에는 BMC 부팅 순서를 디스크로 바꿔 두기(Redfish 쓰기 · 복원 책임). → **확정(09-02 · 권고안)**: E2.5 의 1회 강제에 기대고 dispatcher 는 `exit` 폴백.
- **Q5 완료 보고 채널** — 첫 로그온 `curl` POST(권고) vs `spvout` 파일 폴링. → **확정(09-02 · 권고안)**: 첫 로그온 `curl` POST.
- **Q6 Samba 소유** — 앱 밖 OPS(권고). 앱이 smb.conf 를 쓰는 안은 dhcpd 조각 선례가 있으나 SMB 는 계정 · 비밀번호까지 관리하게 되어 범위가 커진다. → **확정(09-02 · 권고안)**: 앱 밖 OPS.
- **Q7 전역 운영 설정의 저장 위치** — 관리 화면(DB) vs 환경변수. 비밀번호 · 키는 화면에 평문 노출이 문제 — 환경변수 + 화면은 "설정됨" 표시만(권고). → **확정(09-02 · 권고안)**: 환경변수 + 화면은 "설정됨" 표시만.
- **Q8 정의서 OS 섹션 재확장 범위** — 에디션만(권고) vs 로케일 · 시간대까지. → **확정(09-02 · 권고안)**: 에디션만.
- **Q9 설치 소스 추출** — ISO 업로드 시 앱이 `sources\` 를 추출(6 GB IO · 저장 공간 2배) vs 운영 절차로 1회 배치(권고 — 실측과 동일 · 앱은 경로만 안다). ISO 가 바뀌면 재배치. → **확정(09-02 · 권고안)**: 운영 절차로 1회 배치 — 앱은 경로만 안다.
- **Q10 드라이버 자원** — Subprogram(DRIVER) 의 트리를 그대로 `Drivers\<자원명>` 으로(권고). INF 존재 검사(`*.inf`)를 등록 시 경고로. → **확정(09-02 · 권고안)**: Subprogram(DRIVER) 트리 그대로 · 등록 시 INF 존재 검사는 경고.
- **Q11 GVLK 미활성 상태** — MVP 는 ②(유지). 운영 전 OEM/OPK 키 경로 확인은 사용자 몫. → **확정(09-02)**: MVP 는 ②(GVLK 유지). 운영 전 OEM/OPK 키 경로는 사용자 몫.

## 7. 비목표 (이 통합에서 하지 않는 것)
리눅스 OS 설치(E4-L · 장기) · Samba 를 앱이 서빙 · Secure Boot on 지원(S 트랙 별도) · 활성화 · BurnInTest(E5-W) · 정의서 상세(파티션 · 사용자) 복원.

## 8. 결정 정리 (2026-09-02 · 토론 1호 종결)
사용자 결정: "토론 문서의 디스크 질문인 Q3 제외 전부 권장안으로 진행. Q3 는 실측 후 확정."

| # | 결정 |
|---|---|
| Q1 | 코드 = `E4-1-a` 하위 `E4-1-a-1`~`E4-1-a-6` |
| Q2 | `$OEM$` 은 전 보드 공용 한 벌 |
| Q3 | **보류** — E4-1-a-5(실측 2호) 뒤 E4-1-a-6 에서 확정 |
| Q4 | 재PXE = E2.5 1회 강제 + dispatcher `exit` 폴백(3회째 HOLD) |
| Q5 | 완료 보고 = 첫 로그온 `curl` POST |
| Q6 | Samba = 앱 밖 OPS |
| Q7 | 전역 운영 설정 = 환경변수 · 화면은 "설정됨" 표시만 |
| Q8 | 정의서 OS 섹션 재확장 = 에디션만 |
| Q9 | 설치 소스 추출 = 운영 절차 1회 배치 · 앱은 경로만 |
| Q10 | 드라이버 = Subprogram(DRIVER) 트리 그대로 · INF 검사는 경고 |
| Q11 | 활성화 = GVLK 유지(MVP) |
| **CP1 정정(09-02)** | **Administrator 비밀번호 = 정의서 필수 입력**(E4-1-a-2 CP1 승인 시 사용자 결정). D6 · Q7 · Q8 의 "전역 설정" 목록에서 비밀번호를 뺀다 — 층 분류 F → D. 시간대 · 제품 키 · 공유 접속 정보는 전역 설정 유지 |

이 결정으로 D1 · D2 · D4~D9 는 채택안 그대로 CP1 의 입력이 된다(D6 의 Administrator 비밀번호만 위 CP1 정정으로 정의서 입력이 됐다 — plan §8 D-11). D3 만 미결로 남아 E4-1-a-3 은 DiskID 0 · 디스크 1개 전제로 구현하고, E4-1-a-6 이 실측 결과로 채운다. 다음 = E4-1-a-1(OPS · 런북 절) → E4-1-a-2 CP1.
