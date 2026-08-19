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
- [ ] Redfish SimpleUpdate 로 커스텀 BIOS 파일이 적용되는지 (E3-R 조사의 실기 확인 항목).

#### E2-3 착수 게이트 (2026-08-08 신설 — BMC 펌웨어 13.06.27 이 UEFI Shell 경로를 폐쇄)
근거: `discussion/26-08-08_14-48-20_E2-bmc-redfish-pivot_discussion.md`. BMC 집행이 가상 USB 에서 Redfish SimpleUpdate 로 전환되면서 착수 게이트가 "이미지 포맷·하부 도구 확정" 에서 아래 셋으로 교체됐다.

- [ ] **13.06.27 에서 UpdateService OEM 계약이 유지되는가** — `GET /redfish/v1/UpdateService` 로 `Oem.AMIUpdateService`(FlashPercentage · UpdateStatus · UpdateTarget)와 `Actions/SimpleUpdate` URI 존재 확인. E3-R 조사가 "최신 펌웨어에서 유지되는지 확인 불가" 로 남긴 항목이며, 13.06.27 이 정확히 그 대상이다.
- [ ] **게스트 전원 OFF 상태에서 SimpleUpdate 가 수락되는가** — BMC 업데이트는 게스트 전원이 꺼진 상태에서 진행해야 한다(전원 선은 연결 유지, BMC 는 대기 전력으로 생존). 공식 가이드는 "업데이트 중 BMC WebGUI 접속 금지" 만 명시하고 전원 상태 요건은 미기재라 실측이 필요하다.
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
- [ ] **실 BMC Redfish**: `/redfish/v1` 버전 · `Systems/Self/Bios/SD` 실재 · 계정 PATCH · 기본 비밀번호(시리얼 끝 11자) 정책 — E3-R 체크리스트 8항목.

### 강화 확장 (DEC-35 — E3 이후, 전원 제어 3종)
- [ ] **Redfish 전원 제어**: ComputerSystem.Reset(On/ForceOff/GracefulRestart) 실측 — UC-2 즉시 강제 정지 · phase 전환 재부팅 신뢰성의 전제. **(2026-08-08 이관 — 이 항목은 신설된 `E1.5 : Redfish 제어 기반 · 전원 제어` 소관이며, `E2-3` 의 착수 게이트다.** BMC 업데이트가 `BIOS flash → ForceOff → BMC flash → On → 검증` 흐름을 요구하므로 전원 제어 없이는 시작도 종료도 못 한다. 아래 나머지 2 종은 E1.5 범위 밖 — 필요해지는 시점에 그 클라이언트 위에 얹는다.)
- [ ] **BootSourceOverride**: 다음 1회 부팅을 PXE 로 강제 — UC-4(network boot 이탈) 원격 복구의 전제.
- [ ] **IndicatorLED (UID 램프)**: 상세 페이지 버튼 → 실물 램프 점멸 — UC-5 식별 후보 4.

## 완료 기록

(아직 없음)
