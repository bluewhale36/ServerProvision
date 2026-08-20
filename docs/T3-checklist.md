# T3 체크리스트 — 물리 서버(실기) 확보 시 몰아서 확인할 항목

> **운영 규약** (유스케이스 토론 Q-C · DEC-35, 2026-07-19 확정): 물리 서버 없이는 검증할 수 없는(T3)
> 항목을 슬라이스가 만들 때마다 여기 한 줄씩 적립한다. 물리 서버가 확보되면 이 목록을 위에서부터
> 소화하고, 완료 항목은 체크 + 결과 한 줄을 남긴다. Notion 후속 마일스톤 기재 규약(DEC-9)과 병행.

## 적립 목록

### E1 — 진단 리눅스
- [ ] **V3** (E1-R): `modprobe ipmi_devintf ipmi_si` 후 `/dev/ipmi0` 생성 여부 + `ipmitool lan print` 유효 채널 번호(1 이 아닐 수 있음) — 4종 보드 각각. E1-2 수집 스크립트의 전제.
- [ ] **V8** (E1-R): `dmidecode -s system-uuid` / `-s baseboard-serial-number` 가 placeholder("To Be Filled By O.E.M." 류)가 아닌지 — 4종 보드 각각. E1-2 수집 파서의 placeholder 필터 설계 입력.
- [ ] **실 iPXE 펌웨어 거동** (E1-1): 실보드 NIC 의 PXE ROM 이 `chain http://` · `||`/`goto` · 커널 인자 `initrd=` 를 처리하는지 (QEMU ROM 과 다를 수 있음 — V7 의 실기판). 참고 실측(2026-07-19 T2): QEMU legacy BIOS iPXE ROM 은 스크립트 부트파일 실행 OK / OVMF(UEFI) 네이티브 PXE 는 텍스트 스크립트를 실행 못 함 — 실기 UEFI 는 dhcpd→tftp `ipxe.efi` 2단 체인이라 구조가 다르며(E1-I 소관) 이 항목에서 실보드로 확인한다.
- [ ] **실보드 NIC 드라이버** (E1-1): modloop-lts 의 모듈셋으로 실 NIC 이 잡히는지 (진단 리눅스에서 `ip link` 확인).
- [ ] **PCIe 카드 lspci 실측** (E1-2): 사내 사용 카드(RAID·10G UTP/SFP+·FC 16/32Gb·NVIDIA GPU)의 lspci 출력 수집 — 종류 분류 규칙(kind) 보강 입력. 4종 보드 + 대표 카드 조합. **`lspci -nn` 으로 뜰 것**(U4-1 토론 8회차) — 기본 출력은 Vendor/Device(=칩)만 이름으로 바꾸므로 리브랜드 카드의 상품명이 보이지 않는다. 카드 식별에 필요한 것은 `Subsystem` 줄의 `[벤더:디바이스]` id 다.
- [ ] **dmidecode 메모리 슬롯 표기 실측** (E1-2): 실보드의 DIMM Locator 문자열 형식 확인 — 슬롯 표시 UI 정합 입력.

### E2 — 펌웨어 (슬라이스 진행 시 구체화)
- [ ] **flash 집행** (DEC-20): 가상 USB 이미지 부팅 → BIOS flash 실행 — 어떤 시뮬레이터로도 재현 불가, 실보드 전용.
- [ ] Redfish SimpleUpdate 로 커스텀 BIOS 파일이 적용되는지 (E3-R 조사의 실기 확인 항목). **→ 2026-08-19 부분 실측(원장 = Notion E0-4)**: 표준 `image.RBU` 는 `UpdateComponent: "BIOS"` 로 동일 버전(F27, 47초) · 상위 버전(F29, 2분 22초) 모두 flash 성공 — 단 **1영역(`BIOS`)만 갱신되고 `BIOS2` 는 버전 미노출로 거동 불명**. **로고 변경본(커스텀 = GIGABYTE 에 요청해 제공받는 boot 로고 변경 이미지)을 RBU 경로로 flash 하면 boot 로고만 반영되지 않음 — 그 외 갱신은 정상**(2026-08-19 정정: 종전 "기본 이미지로 flash 됨" 은 과대 서술이었다). 벤더 문의(로고 변경본의 RBU 형식 제공 여부 · 로고 미반영 원인) 후 방향 재조정, 이 항목은 그때까지 미결 유지. **→ 2026-08-19 L1 실측(4호 §8): 보유 "로고 변경본 RBU" 가 정본 image.RBU 와 SHA-256 동일 — 로고 미반영의 원인 확정(로고가 들어 있지 않은 파일을 flash 한 것, RBU 경로 결함 아님). 미반영 원인 문의는 불요화, 남은 게이트 = 로고 변경본의 RBU 형식 제공 요청(벤더 메일 발송 예정).** 2영역 갱신은 `UpdateComponent` 허용값의 `HPM_BIOS2` 가 후보(미실측).
- [ ] **fat32-lib 생성 이미지의 sanboot 전달 검증** (E2-2 · 체크리스트 4호 J1 잔여): J1(2026-08-19)의 전달 통로가 PXE 서버 잠정 사용 중단으로 물리 USB 로 변경되어, 같은 이미지를 iPXE `sanboot`(HTTP)로 전달하는 조합이 미실측으로 남음. 파일시스템 인식 · `startup.nsh` 순회 탐지는 J1 이 검증하고, sanboot 계통 자체는 동형(mkfs.fat super-floppy 100 MB) 이미지로 기실증(Notion `PXE Server 구축`)이라 잔여 리스크는 낮음. E2-2 CP7 qemu 하네스(`scripts/pxe-lab/`) 선행 확인 + 실기에서 종결.

#### E2-3 착수 게이트 (2026-08-08 신설 — BMC 펌웨어 13.06.27 이 UEFI Shell 경로를 폐쇄)
근거: `discussion/26-08-08_14-48-20_E2-bmc-redfish-pivot_discussion.md`. BMC 집행이 가상 USB 에서 Redfish SimpleUpdate 로 전환되면서 착수 게이트가 "이미지 포맷·하부 도구 확정" 에서 아래 셋으로 교체됐다.

- [x] **13.06.27 에서 UpdateService OEM 계약이 유지되는가** — `GET /redfish/v1/UpdateService` 로 `Oem.AMIUpdateService`(FlashPercentage · UpdateStatus · UpdateTarget)와 `Actions/SimpleUpdate` URI 존재 확인. E3-R 조사가 "최신 펌웨어에서 유지되는지 확인 불가" 로 남긴 항목이며, 13.06.27 이 정확히 그 대상이다. **→ 2026-08-18 실측 완료(원장 = Notion E0-4)**: 유지 확인 — 단 계약 모양이 갱신됐다. 액션 명명은 표준 `#UpdateService.SimpleUpdate`(target 경로는 조사값과 동일), 진행 필드는 `Oem.AMIUpdateService.UpdateInformation.{FlashPercentage · UpdateStatus · UpdateTarget}` 한 겹 안으로, `PreserveConfiguration` 은 `{"BMC": true}` 단순형으로 바뀌었다. `MultipartHttpPushUri` = `/redfish/v1/UpdateService/upload` 유지, RedfishVersion 1.15.1. **E2-3 구현의 계약은 조사값이 아니라 이 실측 스키마를 따른다.**
- [x] **게스트 전원 OFF 상태에서 SimpleUpdate 가 수락되는가** — BMC 업데이트는 게스트 전원이 꺼진 상태에서 진행해야 한다(전원 선은 연결 유지, BMC 는 대기 전력으로 생존). 공식 가이드는 "업데이트 중 BMC WebGUI 접속 금지" 만 명시하고 전원 상태 요건은 미기재라 실측이 필요하다. **→ 2026-08-18 실측(원장 = Notion E0-4)**: SimpleUpdate 실집행(13.06.27 `.ima_enc`, HTTP pull)이 다운로드 → 검증 → flash → 완료(Task OK, 17:20:41~17:28:18 약 7분 37초)로 완주했고, 같은 세션에서 `PowerState: "Off"` 가 실측됐다. 완료 후 BMC 재시작으로 잠시 접속이 거부되다 동일 IP · 자격증명으로 복귀한다.
- [ ] **BMC 업데이트 실패 후 재시도 경로** — 듀얼 이미지(`DualImageConfigurations`)로 벽돌 위험은 낮으나 복구 절차 확인 필요. HPE Cray CSM 선례: `ipmitool mc reset cold` 후 5 분 뒤 재시도.

### U4 — 디스크 · RAID (세팅 정의서 확장, 2026-08-17 신설)

근거: `discussion/26-08-15_22-12-47_U4-1-disk-features_discussion.md` ~ `26-08-17_19-40-39_…-8_discussion.md` (8회차).
U4-1 은 정의서가 **RAID 구성과 디스크 역할을 적는 것**까지만 하고 집행은 E 영역에 남긴다. 아래는 그 경계에서 실물 없이 확정할 수 없어 남긴 것들이다.

- [ ] **주력 RAID 카드 둘의 Subsystem ID 실측** (U4-1-1 / MA-raidcard): `lspci -nn` 으로 **GIGABYTE CRA3338**(캐시 없음)과 **AVAGO 9361-8i**(캐시 2GB)의 `Subsystem: … [벤더:디바이스]` 값을 각각 수집. 리브랜드 카드는 Vendor/Device 가 원 칩 제조사(Broadcom `0x1000`) 것이라 **기본 lspci 로는 두 카드가 같아 보일 수 있다** — 자원 등록의 '정밀 등록'(관측 채움)과 정의서의 자동 탐지(AUTO)가 이 값에 걸려 있다.
- [ ] **CRA3338 의 PCI 클래스 표기 확인** (U4-1-1): BMC inventory 에서 `Serial Attached SCSI controller` 로 잡힌다는 것이 확인됐다(E0-3 PCI 섹션). **lspci 에서도 같은 클래스로 뜨는지** 확인. 지금 파서 `DiagnosticReportParser.kindOf` 는 `raid` · `megaraid` 문자열이 있을 때만 `RAID` 로 분류하므로 **이 카드가 `ETC` 로 떨어진다** — 실측 문자열을 받아 분류 규칙을 보강해야 한다.
- [ ] **두 카드의 RAID 구성 명령이 실제로 다른가** (E 계열): 사용자는 다르다고 알고 있으나 정확한 명령은 미상. **E 슬라이스에서 WebSearch 로 선행 조사**한 뒤 실기로 확인한다. 참고 — 같은 MegaRAID 칩 계열이면 `storcli` 로 함께 다뤄질 가능성이 있고, 그렇다면 명령을 가르는 것은 캐시 유무가 아니라 **칩 계열과 동작 모드(RAID / IT-HBA)** 다.
- [ ] **CRA3338 의 RAID 레벨 한계 실기 확인** (U4-1-1): 사용자 확인으로 **RAID0 · RAID1 만 지원**(최소 2개 디스크), RAID5 이상 불가. 이것이 업무 관례("같은 스펙 3개 이상이면 캐시 있는 카드")의 **진짜 이유**다 — 캐시가 아니라 RAID5 를 만들 수 있는 컨트롤러인가가 가른다. 실기에서 그 한계를 확인하고, 자원의 '지원 RAID 레벨' 필드 설계 입력으로 쓴다.
- [ ] **디스크 장착 순서 실측** (U4-1-2): 업무 관례상 **SSD → HDD, 용량 작은 것 → 큰 것** 순으로 장착된다. 이 순서가 `lsblk`/`lspci` 수집 결과의 나열 순서와 일치하는지 — 볼륨 우선순위(OS 영역 자동 선택)가 이 순서를 전제한다.

### E3 — BIOS/BMC 설정 (슬라이스 진행 시 구체화)
- [x] **실 BMC Redfish**: `/redfish/v1` 버전 · `Systems/Self/Bios/SD` 실재 · 계정 PATCH · 기본 비밀번호(시리얼 끝 11자) 정책 — E3-R 체크리스트 8항목. **→ 2026-08-19 3호 실측으로 사실상 완결(원장 = Notion E0-4-3)**: `PATCH /redfish/v1/Systems/Self/Bios/SD` 204(`If-Match: *`) → pending 생성("Future BIOS Settings") → ForceRestart 적용 → 원복까지 **전 왕복 실증**. 2호의 GET 404 는 "빈 pending 은 GET 404, PATCH 는 수락" 거동으로 확정 — E3-1 전제 완전 복구. `BiosAttributeRegistry.json` 273속성(Enumeration 256 · Integer 15 · Password 2, DefaultValue · 허용값 · ResetRequired · Dependencies 47) 채집 = E3-1 · biossetting 검증 룰 원천. 세션 = `SessionTimeout` 30초 · 상한 10개 실측(발급 · 토큰 사용 정상, E1.5 는 Basic 우선 + Task 추적 등 단기 세션 병용). 잔여 1: 공장 기본 비밀번호 정책(신품 개봉 시 확인).

### 강화 확장 (DEC-35 — E3 이후, 전원 제어 3종)
- [x] **Redfish 전원 제어**: ComputerSystem.Reset(On/ForceOff/GracefulRestart) 실측 — UC-2 즉시 강제 정지 · phase 전환 재부팅 신뢰성의 전제. **→ 2026-08-19 완결(원장 = Notion E0-4)**: On 재투입 성공, GracefulShutdown 정상 동작, `ResetActionInfo` 허용값 5종 실측(`ForceOff` · `ForceRestart` · `GracefulShutdown` · `On` · `PowerCycle` — 조사값의 GracefulRestart 는 부재). **실패 모드 1종 실측**: On 이 "IPMI 명령은 성공했으나 전원 상태 불변" Exception(Critical)으로 떨어진 사례 1회 — 수동 cold boot 개입 후 재시도로 성공. E1.5 클라이언트는 On 발행 후 `PowerState` 폴링 검증 + 실패 시 `PowerCycle` 폴백 + 운영자 알림을 설계에 포함해야 한다. **(2026-08-08 이관 — 이 항목은 신설된 `E1.5 : Redfish 제어 기반 · 전원 제어` 소관이며, `E2-3` 의 착수 게이트다.** BMC 업데이트가 `BIOS flash → ForceOff → BMC flash → On → 검증` 흐름을 요구하므로 전원 제어 없이는 시작도 종료도 못 한다. 아래 나머지 2 종은 E1.5 범위 밖 — 필요해지는 시점에 그 클라이언트 위에 얹는다.)
- [ ] **BootSourceOverride**: 다음 1회 부팅을 PXE 로 강제 — UC-4(network boot 이탈) 원격 복구의 전제. **→ 2026-08-19 승격 검토(4호 세션 관찰)**: OS 설치 서버의 boot priority 가 OS 1순위 · USB/네트워크 후순위임이 관찰돼 "프로비저닝 게스트는 상시 PXE 우선" 전제가 실기에서 깨짐. 재프로비저닝 대상(기존 OS 보유)과 phase 전환 재부팅(특히 flash 의 설정 리셋과 결합 — 수동 PXE 우선 설정이 flash 한 번에 무효)에 대비해 UC-4 복구 전용이 아니라 **프로비저닝 흐름 필수 배선(E1.5 합류 후보)** 으로 승격 검토. Once/Continuous 선택 · `Systems/Self/SD`(ComputerSystem pending) 경유 여부는 4호 K1 실측 입력.
- [ ] **IndicatorLED (UID 램프)**: 상세 페이지 버튼 → 실물 램프 점멸 — UC-5 식별 후보 4.

## 완료 기록

- **2026-08-19 — BMC Redfish 실측 4호 세션 J1** (계획 = `discussion/26-08-19_13-59-00_bmc-redfish-fieldwork-4_briefing.md`, 원장 = Notion `E0-4-4`). **fat32-lib 조립 이미지의 실기 부팅 왕복 통과** — PXE 서버 잠정 사용 중단 · BMC 가상 미디어 실행 불가로 전달 통로를 물리 USB(dd)로 변경해 수행. 실기 UEFI(AMI Aptio V)가 fat32-lib FAT32 super-floppy(100 MB · edk2-stable202002 Shell)를 인식하고 `startup.nsh` 의 fs 번호 비의존(마커 `spv-fw.tag` 순회) 탐지가 동작함 — **E2-2 의 이미지 동적 생성 = fat32-lib 인프로세스로 확정, mtools fallback 불요**. 잔여 = "fat32-lib 이미지 × sanboot 전달" 조합(위 E2 절 적립 항목). **파생 관찰: OS 설치 서버는 boot priority 가 OS 1순위** — BootSourceOverride 승격 검토의 계기(위 강화 확장 절, 4호 K1 신설).
- **2026-08-19 — BMC Redfish 실측 3호 세션** (계획 = `discussion/26-08-19_11-15-57_bmc-redfish-fieldwork-3_briefing.md`, 원장 = Notion `E0-4-3`). **BIOS 설정 Redfish 쓰기 전 왕복 실증**(위 E3 항목 — E3-1 전제 복구). **핵심 파생 발견: F27 → F29 flash 가 전원 계열 설정을 기본값으로 초기화함** — F27 시점 값(SpeedStep Disable · PackageCState C0/C1 · BIOS Controls EPB 등)이 E0-1 의 사내 표준 세팅과 일치했는데 F29 후 전부 기본값(Enable · Auto · OS Controls EPB)으로 바뀜. 즉 "펌웨어 업데이트 → 설정 소실 → 재적용 필요" 가 실측됐고, `ProvisioningPhase` 의 FIRMWARE_UPDATING → FIRMWARE_SETTING 순서 설계가 실증적 근거를 얻음. 또한 "표준 이미지 flash + Redfish 설정 재적용" 파이프라인이 사내 커스텀 이미지 요구의 대체 경로로 성립(벤더 문의 ① · ② 의 중요도 하락). `Systems/Self/SD` = ComputerSystem(Boot 등) pending 으로 별개 확인. Syslog 는 `NetworkProtocol` 응답에 부재 확정(Redfish 트리 밖 — E3-R 추정 확정). H4 는 캡처 화면이 어긋나(SEL 이벤트 조회 `/api/logs/event`) Syslog 설정 화면 재캡처 이월. 잔여: I1(PFR 버전) · I2(ipmitool) · I3(전원 왕복 관측) · H4 재수행.
- **2026-08-19 — BMC Redfish 실측 2호 세션** (계획 = `discussion/26-08-19_09-29-05_bmc-redfish-fieldwork-2_briefing.md`, 원장 = Notion `E0-4`). **BIOS 의 Redfish flash 실증**(표준 이미지 한정 — 커스텀 이미지 미결로 E2-2 설계 분기는 벤더 문의 후 확정), 전원 제어 완결(위 항목), 보드 시리얼 `QG260700082` 채집(Chassis Oem — E3-0 입력). **부정 실측 2건**: `@Redfish.Settings` 링크 부재 + `GET /redfish/v1/Systems/Self/Bios/SD` 404 — **공식 가이드의 BIOS 설정 PATCH 경로(`Bios/SD`)가 이 펌웨어에 없다**(E3-1 전제 재검 필요 — 1호에서 실측된 `Systems/Self/SD` 가 대체 후보, 미확인) / 세션 발급 415 는 Content-Type 헤더 누락이 원인(경로 자체는 실재 — 재시도 항목). 잔여: 커스텀 이미지 벤더 문의 · `Systems/Self/SD` 확인 · `HPM_BIOS2` 용도 · Syslog 위치(F3 이월) · 복구 절차(F4 이월).
- **2026-08-18 — BMC Redfish 실측 세션** (계획 = `discussion/26-08-15_15-48-41_bmc-redfish-fieldwork-checklist_briefing.md`, 원장 = Notion `E0-4 : BMC Redfish API 경로 확인 작업`, 장비 = MS04-CE0 · BIOS F27 · BMC 13.06.26 → 13.06.27). E2-3 착수 게이트 ①(OEM 계약 — 모양 갱신 확인) · ②(전원 OFF 수락 — 실집행 완주) 통과, ForceOff 실증. 부수 수확: 자격증명 PATCH(fresh ETag + If-Match) 204 실증(E3-0 부트스트랩 경로 확정) · BIOS `Attributes` 키 체계 전량 채집(`BirchStream` · `GBT` · `NWSK` · `SETUP` · `TCG` 접두 — E3-1 · BIOS 세팅 템플릿의 실측 입력) · `BootSourceOverrideTarget` 허용값에 `Pxe` 실측(UC-4 원격 복구 재료) · 듀얼 이미지 실물 확인(Image1 13.06.26 / Image2 13.06.24). **잔여 5건**: Reset `On` 재투입 · Redfish 세션 발급 경로(B1 — POST Sessions 불명, Basic auth 는 가용) · `SimpleUpdateActionInfo` 내용(A4) · Syslog 설정 위치(C4) · 실패 복구 절차(A7).
