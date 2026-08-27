# Windows Server 2025 PXE 무인 설치 실측 체크리스트 1호 — 경로 성립 판가름

> **문서 종류**: 실측 세션 계획 · 기록 양식(참고 브리핑). 조사 브리핑(`discussion/26-08-20_09-10-41_windows-server-2025-unattended-pxe_briefing.md`)의 §6 · §7 을 실행 절차와 준비물로 옮긴 것이다.
> **작성**: 2026-08-26 10:52 KST, 앵커 세션.
> **주 목적**: **U1 판가름** — Windows Server 2025 의 setup 엔진이 `setup.exe /unattend:` 의 `specialize` · `oobeSystem` pass 를 처리하는가. 이 한 판이 구현 경로(setup 경로 vs DISM `/Apply-Image` 경로)를 정하므로, 판정 전에는 코드를 쓰지 않는다.
> **원장**: Notion 실측 페이지는 **신설 확정 후** 지정(E0 계열 우산 아래 — 임의 신설 금지). 확정 전에는 이 문서의 §10 표를 채운다.
> **전제**: 착수 시점(MVP 배포 후 vs 지금)과 라이선스 경로(§0)는 미확정이나, 실측 자체는 평가판 ISO 로 가능하다.

---

## 0. 선행 결정 2건 (사용자)

1. **착수 시점** — 2026-08-15 MVP 축소 결정은 OS 설치를 제외했고, Windows 는 "MVP 배포 후 업데이트 1순위 후보" 다. 실측을 지금 당기는지는 별도 결정.
2. **라이선스 경로** — 평가판 문언은 "test · demonstrate · evaluate" 한정. "대부분의 출고 서버가 거치는 stress test" 는 제조 공정 상시 도구로 보이기 쉬우므로 OEM/시스템 빌더(OPK) 계약 경로 확인이 필요하다. 실측은 평가판으로 진행하되 운영 투입 전 확정.

## 1. 준비물

### 1-1. 파일 자산 (실측 서버의 정적 디렉토리 `/srv/pxe/win2025/`)

| 파일 | 출처 · 만드는 법 | 확인 |
|---|---|---|
| `boot.wim` | Server 2025 평가판 ISO `\sources\boot.wim` — **index 2**(Windows Setup) 그대로 | 그대로 복사 |
| `install.wim` | 같은 ISO `\sources\install.wim` — Samba 공유 `sources/` 아래 | `wimlib-imagex info install.wim` 으로 `/IMAGE/NAME` 실값 채집(U5) |
| `wimboot` | github.com/ipxe/wimboot/releases **v2.9.0 서명 릴리스** — 직접 빌드본 금지(Secure Boot 거부) | SHA 기록 |
| `winpeshl.ini` | 조사 브리핑 §3-4 초안 — 인용 부호 두 표기 모두 준비(U6) | 2벌 |
| `install.bat` | §3-5 초안 — 서버 IP · 공유 · 계정을 실측 환경값으로 | 비밀번호는 문서 미기재 |
| `autounattend.xml` | §3-7 골격을 **고정값**으로 채운 시험본 — `DiskID=0` · `WillWipeDisk=true` · 평가판 `/IMAGE/NAME` · `ComputerName=*` · Korea Standard Time · Administrator 비밀번호(§4-2 인코딩) · `FirstLogonCommands` 는 1호에서는 로컬 표식 파일 생성 정도(완료 보고 훅은 후속) | 디스크 1개 장비 전용 |
| `win.ipxe` | §3-3 스크립트를 정적 파일로 — 실측 1호는 앱 dispatcher 를 거치지 않는다(§2-4) | `${net0/mac}` 대신 고정 파일명 |

ISO 마운트 · WIM 조회는 리눅스에서 완결한다(`mount -o loop`, `wimlib-imagex`). macOS 는 `brew install wimlib`.

### 1-2. 서버 구성 (스테이징 VM, Rocky)

| 구성 | 내용 |
|---|---|
| 정적 HTTP 서빙 | `/srv/pxe/win2025/` 를 **앱과 별도 포트**로 서빙(`python3 -m http.server` 또는 nginx). 앱의 자산 서빙(마커 · 토큰)은 실측 대상이 아니다 |
| Samba | `dnf install samba` · 조사 §3-6 설정 그대로(SMB3 · 서명 필수 · guest 금지 · `deploy` 계정 · 읽기 전용) · 445/tcp 개방. **guest 공유는 만들지 않는다**(N2 의 실패 케이스는 별도 임시 공유로) |
| dhcpd · TFTP | 기존 조각 그대로(`ipxe.efi` 서빙). 게스트는 iPXE 까지 기존 사슬로 올라온다 |
| 앱 | 실측 중 **정지**(§2-4) |

### 1-3. 실기 조건

| 조건 | 이유 |
|---|---|
| Secure Boot **off** | 1호는 경로 성립만 본다. S 계열은 별도 트랙(§8) |
| **디스크 1개만 장착** — 데이터 디스크 물리 분리 | `DiskID=0` + `WillWipeDisk=true` 가 다른 디스크를 지울 수 있다(D4 전까지 불변) |
| RAM ≥ 8 GB | boot.wim(약 500 MB) 전체 RAM 적재 + WinPE(H3) |
| BMC KVM 접근 | WinPE 화면 · setup 오류 화면의 유일한 관찰 수단(H4). 실기 BMC 는 격리망 1.x 에서 접근 |
| 보드 NIC · 스토리지 컨트롤러 모델 기록 | H1 · H2 판정 기준 |

## 2. 안전 수칙

1. **데이터 디스크는 D4 전까지 물리 분리** — 응답 파일의 와이프 범위 실증 전에는 장착하지 않는다.
2. 평가판 이미지는 실측 전용. 설치된 채 장비를 출하하지 않는다.
3. 자격증명(Samba `deploy` 비밀번호 · Administrator 비밀번호)은 문서 · 원장에 적지 않는다.
4. **앱 정지 후 실측** — 게스트가 PXE 로 올라오면 앱이 등록하고 R13 자동 진단이 돌아 버린다. 실측 게스트는 iPXE 셸(Ctrl-B)에서 `chain http://<VM>:<포트>/win2025/win.ipxe` 로 수동 진입하거나, dhcpd 조각의 iPXE 분기 filename 을 임시로 `win.ipxe` 로 바꾼다(끝나면 원복). 앱 정지가 단순하다.
5. 실패 시 무리한 반복 금지 — Panther 로그(§10) 채증 후 다음 항목으로.

## 3. 위험 등급

R(읽기 · 관찰) / S(가역 — 서버 구성 변경) / X(집행 — 디스크에 쓴다). 1호의 U1 은 X 다(디스크 1개 장비에 설치).

## 4. Part U [X] — setup 엔진 판가름 (1호의 관문)

| ID | 확인 | 판정 기준 |
|---|---|---|
| U5 | `wimlib-imagex info install.wim` 로 평가판 `/IMAGE/NAME` 실값 | 응답 파일 `ImageInstall/MetaData` 에 그대로 기입 |
| U3 | wimboot 부팅 후 WinPE 에서 `dir X:\Windows\System32\winpeshl.ini install.bat autounattend.xml` | 3개 파일 실재 |
| U4 | 설치 미디어의 setup 자동 실행 대신 `install.bat` 이 먼저 뜨는가 | 우리 배치의 `wpeinit` · `net use` 로그가 먼저 |
| U6 | `[LaunchApps]` 인용 부호 두 표기 중 동작하는 것 | U4 가 뜨지 않으면 다른 표기로 재부팅 |
| **U1** | `setup.exe /unattend:` 완주 — 재부팅 후 **OOBE 화면 없이** 자동 로그온 · `ComputerName` · 시간대 반영 · `FirstLogonCommands` 표식 파일 존재 | 셋 다 충족 = **setup 경로 성립**. OOBE 화면이 뜨거나 계정 · 시간대 미반영 = `oobeSystem` 미처리 |
| U2 (U1 실패 시) | legacy setup 강제 3법 — `/legacy` 플래그 · `boot.wim` 레지스트리 `CmdLine` 교체 · `%CONFIGSETROOT%` | 하나라도 U1 기준 충족 = setup 경로 유지(그 방법 기록). 전부 실패 = **DISM 경로 확정** |

U1 의 확인 순서: (1) WinPE 단계 — `X:\Windows\Panther\setupact.log` 에 `/unattend` 인식 행 (2) 재부팅 후 첫 화면 — OOBE 유무 (3) 로그온 후 `hostname` · `tzutil /g` · 표식 파일.

## 5. Part N [S] — 설치 소스 공유

| ID | 확인 |
|---|---|
| N1 | WinPE(26100)에서 서명 필수 Samba 공유에 `net use` 성공 |
| N2 | 임시 guest 공유로 `net use` — 거절 코드 채집 후 공유 삭제 |
| N3 | IP 주소(`\\192.168.1.10\win2025`)로 접속 시 서명 성립 — 이름 해석 불요 확인 |
| N4 | `boot.wim` 전송 시간(HTTP) · `install.wim` 적용 시간(SMB) — 1대 기준 |
| N5 | WinPE 에 `curl.exe` 존재 여부(`where curl`) — 있으면 런타임 HTTP 수령 가능 |

## 6. Part H [R] — 드라이버 · 하드웨어

| ID | 확인 |
|---|---|
| H1 | WinPE 에서 `wpeinit` 후 `ipconfig` 에 보드 NIC 가 잡히는가(I210 · X710) — 없으면 `wimlib` 드라이버 주입 파이프라인 필요 |
| H2 | `diskpart` › `list disk` 에 대상 디스크가 보이는가(SATA/NVMe 온보드 · SAS3008 뒤) |
| H3 | 부팅 성공 시 `wpeutil` · 작업 관리자 메모리 사용 — RAM 요건 |
| H4 | BMC KVM 으로 WinPE 콘솔이 보이고 키 입력이 되는가 |

## 7. Part D [X] — 디스크 (데이터 디스크 장착 후, 마지막)

| ID | 확인 |
|---|---|
| D1 | 진단 리눅스 `lsblk` 순서 ↔ WinPE `diskpart list disk` 번호 일치 |
| D2 | 동일 모델 2개 장착 시 식별 키(`detail disk` 의 시리얼 · Disk ID) |
| D3 | RAID 논리 볼륨에 setup 경로 설치 가능 여부 — 불가면 DISM 분기 확정 |
| D4 | `WillWipeDisk=true` + `DiskID=0` 의 파괴 범위 — **데이터 디스크에 표식 파일을 두고** 설치 후 생존 확인 |

## 8. Part S [R] — Secure Boot (별도 트랙)

S1 `ipxe-shim.efi` 로 Secure Boot on 부팅 · S2 서명 wimboot 로드 · S3 Server 2025 `bootmgfw.efi`(2023 CA)를 보드 db 가 신뢰하는가 — **GIGABYTE BIOS 의 2023 인증서 포함 여부, BIOS 업데이트 선행 가능성** · S4 off 폴백 유지.

## 9. 판정이 만드는 것

| 결과 | 설계 입력 |
|---|---|
| U1 성립 | **setup 경로 plan** — `OS_INSTALLING` 실행기(`ProvisioningPhaseExecutor` 구현체) + Windows 자산 영역(wimboot · boot.wim · install.wim) + 게스트별 `autounattend.xml` 생성(정의서 OS 섹션 재확장, R11 축소분 복원) |
| U1 실패 · U2 실패 | **DISM 경로 plan** — WinPE 배치가 diskpart → DISM → bcdboot, `specialize`/`oobeSystem` 은 `\Windows\Panther\unattend.xml` 배치 |
| N 계열 | Samba 운영 설계(OPS) — 서명 · 계정 · 공유 배치 |
| H1 · H2 | 드라이버 주입 파이프라인 필요 여부 |
| D1 · D2 | 디스크 선택 UX 의 식별 키 계약(정의서 OS 섹션) · D4 는 와이프 정책 |
| S 계열 | 운영 조건(Secure Boot on 지원 시점) |

## 10. 채증 규율

항목 ID 와 1:1. 채증물: KVM 화면 캡처 · WinPE 의 `X:\Windows\Panther\setupact.log` · `setuperr.log`(USB 또는 공유로 복사) · 설치 후 `C:\Windows\Panther\` 동일 로그 · `net use` 응답 · `diskpart` 출력. 아래 표를 채우고, Notion 원장 확정 시 그리로 옮긴다.

| ID | 결과(O/X/보류) | 관찰 · 값 | 채증물 |
|---|---|---|---|
| U5 | | | |
| U3 | | | |
| U4 | | | |
| U6 | | | |
| U1 | | | |
| U2 | | | |
| N1~N5 | | | |
| H1~H4 | | | |
| D1~D4 | | | |

## 11. 착수 순서 (반나절 시간표)

1. 준비(1시간) — 자산 배치 · Samba · 정적 서빙 · 시험용 XML · 앱 정지.
2. U5 → 부팅 → U3 · U4 · U6(30분) — 여기까지가 wimboot 체인 성립.
3. **U1**(설치 30~60분 + 재부팅 확인) — 실패 시 U2 세 방법(각 재부팅 1회).
4. N1~N5 · H1~H4 는 2~3 단계 안에서 함께 채집.
5. D 계열은 데이터 디스크를 장착해 별도 회차(D4 는 반드시 표식 파일 방식).
6. S 계열은 BIOS 업데이트 가능 시점에 별도 회차.
