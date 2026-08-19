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
- [ ] **PCIe 카드 lspci 실측** (E1-2): 사내 사용 카드(RAID·10G UTP/SFP+·FC 16/32Gb·NVIDIA GPU)의 lspci 출력 수집 — 종류 분류 규칙(kind) 보강 입력. 4종 보드 + 대표 카드 조합.
- [ ] **dmidecode 메모리 슬롯 표기 실측** (E1-2): 실보드의 DIMM Locator 문자열 형식 확인 — 슬롯 표시 UI 정합 입력.

### E2 — 펌웨어 (슬라이스 진행 시 구체화)
- [ ] **flash 집행** (DEC-20): 가상 USB 이미지 부팅 → BIOS flash 실행 — 어떤 시뮬레이터로도 재현 불가, 실보드 전용.
- [ ] Redfish SimpleUpdate 로 커스텀 BIOS 파일이 적용되는지 (E3-R 조사의 실기 확인 항목).

#### E2-3 착수 게이트 (2026-08-08 신설 — BMC 펌웨어 13.06.27 이 UEFI Shell 경로를 폐쇄)
근거: `discussion/26-08-08_14-48-20_E2-bmc-redfish-pivot_discussion.md`. BMC 집행이 가상 USB 에서 Redfish SimpleUpdate 로 전환되면서 착수 게이트가 "이미지 포맷·하부 도구 확정" 에서 아래 셋으로 교체됐다.

- [x] **13.06.27 에서 UpdateService OEM 계약이 유지되는가** — `GET /redfish/v1/UpdateService` 로 `Oem.AMIUpdateService`(FlashPercentage · UpdateStatus · UpdateTarget)와 `Actions/SimpleUpdate` URI 존재 확인. E3-R 조사가 "최신 펌웨어에서 유지되는지 확인 불가" 로 남긴 항목이며, 13.06.27 이 정확히 그 대상이다. **→ 2026-08-18 실측 완료(원장 = Notion E0-4)**: 유지 확인 — 단 계약 모양이 갱신됐다. 액션 명명은 표준 `#UpdateService.SimpleUpdate`(target 경로는 조사값과 동일), 진행 필드는 `Oem.AMIUpdateService.UpdateInformation.{FlashPercentage · UpdateStatus · UpdateTarget}` 한 겹 안으로, `PreserveConfiguration` 은 `{"BMC": true}` 단순형으로 바뀌었다. `MultipartHttpPushUri` = `/redfish/v1/UpdateService/upload` 유지, RedfishVersion 1.15.1. **E2-3 구현의 계약은 조사값이 아니라 이 실측 스키마를 따른다.**
- [x] **게스트 전원 OFF 상태에서 SimpleUpdate 가 수락되는가** — BMC 업데이트는 게스트 전원이 꺼진 상태에서 진행해야 한다(전원 선은 연결 유지, BMC 는 대기 전력으로 생존). 공식 가이드는 "업데이트 중 BMC WebGUI 접속 금지" 만 명시하고 전원 상태 요건은 미기재라 실측이 필요하다. **→ 2026-08-18 실측(원장 = Notion E0-4)**: SimpleUpdate 실집행(13.06.27 `.ima_enc`, HTTP pull)이 다운로드 → 검증 → flash → 완료(Task OK, 17:20:41~17:28:18 약 7분 37초)로 완주했고, 같은 세션에서 `PowerState: "Off"` 가 실측됐다. 완료 후 BMC 재시작으로 잠시 접속이 거부되다 동일 IP · 자격증명으로 복귀한다.
- [ ] **BMC 업데이트 실패 후 재시도 경로** — 듀얼 이미지(`DualImageConfigurations`)로 벽돌 위험은 낮으나 복구 절차 확인 필요. HPE Cray CSM 선례: `ipmitool mc reset cold` 후 5 분 뒤 재시도.

### E3 — BIOS/BMC 설정 (슬라이스 진행 시 구체화)
- [ ] **실 BMC Redfish**: `/redfish/v1` 버전 · `Systems/Self/Bios/SD` 실재 · 계정 PATCH · 기본 비밀번호(시리얼 끝 11자) 정책 — E3-R 체크리스트 8항목.

### 강화 확장 (DEC-35 — E3 이후, 전원 제어 3종)
- [ ] **Redfish 전원 제어**: ComputerSystem.Reset(On/ForceOff/GracefulRestart) 실측 — UC-2 즉시 강제 정지 · phase 전환 재부팅 신뢰성의 전제. **→ 2026-08-18 부분 실측(원장 = Notion E0-4)**: ForceOff 실동작 · `PowerState: "Off"` 확인. **On 재투입 실측이 잔여**라 미체크 유지. **(2026-08-08 이관 — 이 항목은 신설된 `E1.5 : Redfish 제어 기반 · 전원 제어` 소관이며, `E2-3` 의 착수 게이트다.** BMC 업데이트가 `BIOS flash → ForceOff → BMC flash → On → 검증` 흐름을 요구하므로 전원 제어 없이는 시작도 종료도 못 한다. 아래 나머지 2 종은 E1.5 범위 밖 — 필요해지는 시점에 그 클라이언트 위에 얹는다.)
- [ ] **BootSourceOverride**: 다음 1회 부팅을 PXE 로 강제 — UC-4(network boot 이탈) 원격 복구의 전제.
- [ ] **IndicatorLED (UID 램프)**: 상세 페이지 버튼 → 실물 램프 점멸 — UC-5 식별 후보 4.

## 완료 기록

- **2026-08-18 — BMC Redfish 실측 세션** (계획 = `discussion/26-08-15_15-48-41_bmc-redfish-fieldwork-checklist_briefing.md`, 원장 = Notion `E0-4 : BMC Redfish API 경로 확인 작업`, 장비 = MS04-CE0 · BIOS F27 · BMC 13.06.26 → 13.06.27). E2-3 착수 게이트 ①(OEM 계약 — 모양 갱신 확인) · ②(전원 OFF 수락 — 실집행 완주) 통과, ForceOff 실증. 부수 수확: 자격증명 PATCH(fresh ETag + If-Match) 204 실증(E3-0 부트스트랩 경로 확정) · BIOS `Attributes` 키 체계 전량 채집(`BirchStream` · `GBT` · `NWSK` · `SETUP` · `TCG` 접두 — E3-1 · BIOS 세팅 템플릿의 실측 입력) · `BootSourceOverrideTarget` 허용값에 `Pxe` 실측(UC-4 원격 복구 재료) · 듀얼 이미지 실물 확인(Image1 13.06.26 / Image2 13.06.24). **잔여 5건**: Reset `On` 재투입 · Redfish 세션 발급 경로(B1 — POST Sessions 불명, Basic auth 는 가용) · `SimpleUpdateActionInfo` 내용(A4) · Syslog 설정 위치(C4) · 실패 복구 절차(A7).
