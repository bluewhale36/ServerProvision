# BMC Redfish 실측 체크리스트 4호 — 가상 USB 집행 경로의 잔여 delta 채증

> **문서 종류**: 실측 세션 계획 · 기록 양식(참고 브리핑). 원장 = Notion `E0-4-4`.
> **작성**: 2026-08-19 13:59 KST. **개정**: 2026-08-19 — 초안의 J2~J4 가 실측 전에 해소되어(사용자 실무 지식 + 3호 기실측) **J1 단독 체크리스트로 축소**. 해소 내용은 §4 에 설계 입력으로 보존. **2차 개정**: 2026-08-19 — 실기에서 가상 USB 가 fs0: 로 매핑되지 않는 경우가 있다는 실무 보고를 반영해 J1 을 **fs 번호 비의존 탐지(마커 순회)** 검증으로 확장, probe 이미지 제작 · T0 검증 완료. **3차 개정**: 2026-08-19 — PXE 서버 잠정 사용 중단(사용 불가) · BMC 가상 미디어 실행 불가에 따라 J1 전달 통로를 **물리 USB(dd)** 로 변경, sanboot 조합은 T3 적립. **4차 개정**: 2026-08-19 — J1 수행 중 OS 설치 서버의 boot priority 가 OS 1순위임이 관찰돼 §5 의 BootSourceOverride 불요 판정을 철회하고 **K1(§7) 신설**. **5차 개정**: 2026-08-19 — **E2-2 가상 USB 확정을 당일 철회**(예상 비용 과대 — 사용자 판단). Redfish RBU 경로의 로고 미반영을 재검증(§8 L 계열)한 뒤 방향을 재결정한다. J1 · K1 의 검증 가치는 방향과 무관하게 유지된다(J1 = fat32-lib 검증 자산, K1 = PXE 기반 프로비저닝 전체의 전제).
> **주 목적**: **E2-2(BIOS 집행 = 가상 USB + UEFI Shell 확정, 2026-08-19)의 plan 이 요구하는 실측 입력 중, 기존 검증이 덮지 못한 delta 만 채증**한다.
> **연결**: 3호(`discussion/26-08-19_11-15-57_bmc-redfish-fieldwork-3_briefing.md`) · Notion `PXE Server 구축` + 하위 `6월 2주차 — 1차 MVP : BIOS/BMC 업데이트 분기`(부팅 사슬 전체 실측 완료) · E2-R report(`report/26-08-01_20-46-20_E2-R_report.html`) · `docs/T3-checklist.md`.
> **장비 현행 상태**: MS04-CE0 · BIOS **F29** · BMC 13.06.27 · 보드 시리얼 QG260700082.

---

## 0. 출발점 — 부팅 사슬은 전부 실증됐고, 무인 자동화의 판정 재료도 대부분 이미 있다

2026-08-19 에 E2-2 의 집행 경로가 **가상 USB + UEFI Shell(AfuEfix64)** 로 확정됐다가 **같은 날 철회됐다**(확정 근거였던 "커스텀 미반영" 은 boot 로고 미반영으로 정정됐고, 이날의 발견들 — K1 부트 순서 강제 · fs 순회 · sanboot 잔여 — 이 가상 USB 의 예상 비용을 크게 불렸다. 재검증과 재결정 기준은 §8). 아래 J 계열은 확정 시점의 계획이며, J1 의 검증 가치는 방향과 무관하게 유지된다. Notion `PXE Server 구축` 문서가 이 경로의 부팅 사슬 전체를 이미 실기로 검증해 두었다:

> dhcpd + tftp + httpd 인프라 → `ipxe.efi` 체인로딩 → `boot.ipxe` → `sanboot http://.../bios_update.img`(FAT32 가상 USB) → UEFI Shell(`BOOTX64.EFI`) 진입 → `startup.nsh` 자동 실행 → `f.nsh`(BIOS) · `ami_bmc_fw_update_uefi.nsh`(BMC) 실집행 → `reset -c` 재부팅

문서의 검증(정적 이미지 · 수동 메뉴 · 사람 관찰)과 E2-2 의 무인 흐름(동적 이미지 · 자동 분기 · Redfish 폴링 판정) 사이의 delta 로 초안은 J1~J4 네 건을 도출했으나, **J2 · J3 · J4 는 실측 없이 해소됐다** — 소요 시간과 설정 리셋은 실무에서 굳어진 경험 지식이고, FirmwareInventory 반영 시점은 3호 실측 과정에서 이미 관측됐기 때문이다(§4). 남는 실측은 **J1 하나**다.

## 1. 안전 수칙

1. 이번 체크리스트는 **flash 실집행을 포함하지 않는다** — J1 이미지는 echo 와 재부팅만 담은 무해 probe 다.
2. 다만 J1 도 대상 서버의 **재부팅을 동반**하므로 유휴 장비에서 수행한다.
3. 상위 불변 유지: PFR1.RBU · PFR2.RBU 및 AFU `/PFRA` · `/PFRR` 사용 금지, `f2BIOS.nsh`(2영역) 실집행은 HPM_BIOS2 벤더 답변 후 별도 판단.
4. 자격증명 미기재. 채증 원장은 Notion `E0-4-4`.

## 2. 위험 등급

1~3호와 동일 — R(읽기 전용) / S(가역 상태 변경 · 재부팅 포함) / X(집행). **J1 = S. 이번 호에 X 항목 없음.**

## 3. J1 [S] — fat32-lib 조립 이미지의 실기 부팅 왕복

- **왜 남는가**: 문서 검증 이미지는 리눅스에서 `dd + mkfs.fat + loop mount` 로 수동 제작한 것이다. E2-R 은 앱 내 이미지 동적 생성 1순위를 순수 Java `de.waldheinz:fat32-lib`(mount · sudo · SELinux 불요, 인프로세스)로 확정했는데, **fat32-lib 이 쓰는 부트 섹터 · BPB 세부가 `mkfs.fat` 산출물과 동일하다는 보장이 없다**. 실기 UEFI(AMI Aptio V)와 iPXE sanboot 가 이 이미지를 인식하는지가 E2-2 의 유일한 라이브러리 리스크다. 실패 시 파급이 설계 변경(mtools 외부 도구 fallback)이므로 E2-2 CP1 전 확정 가치가 크다.
- **동시 검증 — fs 매핑 번호 비의존 탐지**: 실기에서 가상 USB 가 `fs0:` 로 매핑되지 않는 경우가 있다(호스트의 실 디스크 ESP 등이 앞 번호를 차지하면 밀린다 — 사용자 실무 보고, 2026-08-19). 그래서 probe 의 `startup.nsh` 는 번호를 가정하지 않고 **이미지 루트의 고유 마커 파일 `spv-fw.tag` 를 fs0:~fs9: 순회로 탐지**해 해당 볼륨으로 전환한다. 탐지 대상을 `BOOTX64.EFI` 류가 아닌 고유 마커로 한 이유는 호스트 실 ESP 가 같은 경로를 가져 오탐할 수 있기 때문이다. 미발견 시 echo 후 `reset -c` 로 PXE 복귀 — 반복 실패의 종단은 서버 측 타임아웃 실패 전이가 맡는다(E2-R). **이 패턴이 E2-2 본 이미지 `startup.nsh` 템플릿의 확정 골격**이며, J1 통과 = 이미지 인식 + 탐지 로직의 실기 검증을 겸한다.
- **준비물 (제작 완료, T0 검증 통과)**: `~/Downloads/j1_fat32lib_probe.img` — FAT32 super-floppy 100 MB(문서 검증 이미지와 동일 크기), fat32-lib 0.6.5 인프로세스 생성, 볼륨 라벨 `SPVJ1`. 내용물: `EFI/BOOT/BOOTX64.EFI`(edk2-stable202002 Shell — 문서 검증분과 동일 릴리스, 해시 원본 일치 확인) + `startup.nsh`(아래 전문) + `spv-fw.tag`(빌드 정보 1줄). flash 유틸 없음. T0 = BPB `FAT32` 서명(0x52) · 부트 서명 `55AA` · macOS 마운트로 파일 배치 대조 완료.

```
@echo -off
echo "=== SPV J1 probe : fat32-lib image boot test ==="
connect -r
map -r
set -v found no
for %i run (0 9)
  if exist fs%i:\spv-fw.tag then
    set -v found fs%i
  endif
endfor
if %found% == no then
  echo "J1 FAIL : probe volume not found in fs0..fs9"
  echo "Rebooting in 60 seconds..."
  stall 60000000
  reset -c
endif
echo "J1 OK : probe volume mapped as %found%"
%found%:
type \spv-fw.tag
echo "J1 probe complete. Rebooting in 60 seconds..."
stall 60000000
reset -c
```

- **전달 통로 변경(3차 개정)**: 원안(PXE sanboot)은 PXE 서버 잠정 사용 중단으로, 1차 대안(BMC 가상 미디어)은 실행 불가로 각각 탈락 — **물리 USB 로 전달**한다. 통로가 바뀌어도 검증 대상은 보존된다: 어느 통로든 펌웨어에 블록 디바이스가 하나 나타나고 **같은 FAT 드라이버**가 그 파일시스템을 마운트하므로, fat32-lib 산출물 인식과 `startup.nsh` 순회 탐지는 동일하게 시험된다. sanboot 전달 계통 자체는 문서에서 동형(mkfs.fat super-floppy 100 MB) 이미지로 기실증이며, "fat32-lib 이미지 × sanboot" 조합만 잔여로 남아 §5 와 `docs/T3-checklist.md` 에 적립한다.
- **절차**: ① 맥에서 USB 스틱(100 MB 이상)에 원시 기록 — `diskutil list` 로 외장 USB 의 디스크 번호 확인(**오기록 시 해당 디스크가 파괴되므로 번호 재확인이 전부다**) → `diskutil unmountDisk /dev/diskN` → `sudo dd if=~/Downloads/j1_fat32lib_probe.img of=/dev/rdiskN bs=4m` → `diskutil eject /dev/diskN` ② 스틱을 대상 서버에 장착 ③ 재부팅 → 부트 메뉴에서 해당 USB 의 UEFI 항목 선택.
- **판정**: `J1 OK : probe volume mapped as fsN` 출력 + `type` 으로 마커 내용 1줄 출력 + 60초 후 자동 재부팅이면 통과(마커 출력은 디렉토리 인식뿐 아니라 데이터 읽기 경로까지 입증한다). `J1 FAIL` 출력 또는 Shell 진입 실패면 **fallback(mtools) 전환 신호** — 실패 화면을 그대로 채증한다.
- **채증**: `%found%` 값과 마커 내용이 함께 보이는 화면 사진 1장 이상. 실측된 매핑 번호 자체가 "fs0: 하드코딩이 왜 위험한가"의 근거 기록이 된다.

## 4. 사전 해소 — J2 · J3 · J4 는 실측 불요, 답은 E2-2 설계 입력으로 보존

### J2 (해소) — AFU 소요 시간: 메인보드에 따라 10분 내외 ~ 최대 30분 내외

- **원래 목적**: 무인 흐름은 실패를 타임아웃으로만 감지(E2-R — 무롤백 + WAIT 무한대기 함정)하므로 타임아웃 상한 설계에 정상 소요의 실측값이 필요했다.
- **해소 근거**: 실무 경험치 — **보드별 편차가 커서 짧은 것은 10분 내외, 긴 것은 최대 30분 내외**까지 잡아야 한다(사용자, 2026-08-19). 한 대의 측정값은 이 편차를 대표하지 못하므로 실측 1회의 가치가 낮다.
- **E2-2 설계 입력**: 타임아웃 상한은 단일 고정값이 아니라 **최대 편차(30분)에 마진을 더한 값을 기본**으로 하고, 보드별 조정 여지를 설계에서 검토한다.

### J3 (해소) — FirmwareInventory 버전 반영 시점: POST 직후

- **원래 목적**: 완료 판정 = Redfish `FirmwareInventory` BIOS 버전 폴링이므로 갱신 시점(flash 직후인가 · POST 후인가)이 폴링 설계에 필요했다.
- **해소 근거**: **3호 실측 과정에서 이미 관측** — 반영은 POST 직후다(사용자, 2026-08-19).
- **E2-2 설계 입력**: 폴링은 재부팅 지시 후 POST 소요를 포함한 간격 반복으로 충분하며, POST 완료 시점부터 버전 대조가 유효하다.

### J4 (해소) — AFU(`f.nsh`) 경로에서도 설정은 리셋된다

- **원래 목적**: 3호의 "펌웨어 업데이트가 설정을 초기화한다" 실증이 Redfish RBU 경로 관측이어서, 확정 집행 경로(AFU)의 거동을 별도 확인하려 했다.
- **해소 근거**: `f.nsh` 사용 업데이트에서도 설정이 리셋된다는 것은 **실무에서 확립된 지식**이다. 애초에 provisioning 프로세스를 **펌웨어 업데이트 → 펌웨어 설정** 순서로 잡은 것 자체가, 업데이트가 설정을 초기화하기 때문에 굳어진 실제 업무 프로세스를 가져온 것이다(사용자, 2026-08-19).
- **E2-2 설계 입력**: plan 전제 "flash 후 설정 리셋"은 집행 경로 기준으로도 참. `FIRMWARE_UPDATING → FIRMWARE_SETTING` phase 순서는 3호 실측과 실무 프로세스 기원의 **이중 근거**를 가지며, E3 재적용은 조건 없이 후행한다.

## 5. 검증 불요 판정 — 이번 대상에서 제외한 것과 사유

- **부팅 사슬 전체**(PXE → iPXE → sanboot → UEFI Shell → startup.nsh 자동 실행 → flash → 재부팅): `PXE Server 구축` 문서에서 전부 실측 완료.
- **BMC 의 UEFI 경로 flash**: 문서에서 실측 완료였으나 E2-3 은 Redfish SimpleUpdate 로 확정(1호 실집행 완주 + PreserveConfiguration)이라 이 경로 자체가 불요.
- **BMC VirtualMedia 계열**: 부팅 통로가 sanboot 로 실증돼 애초에 불요 — 직전 세션의 VirtualMedia 실측 제안은 전제 오류로 철회.
- **BootSourceOverride Pxe 실집행 — 판정 철회(4차 개정)**: 당초 "프로비저닝 게스트는 상시 PXE 우선 부팅" 전제로 불요 판정했으나, J1 수행 중 OS 설치 서버의 boot priority 가 OS 1순위 · 가상 USB 후순위임이 관찰돼 전제가 깨졌다 — §7 K1 로 신설.
- **2영역 갱신**(`f2BIOS.nsh` `/OEMCMD:2B`): MVP 는 1영역(`f.nsh`)으로 충분. HPM_BIOS2 벤더 답변 후 필요 시 별도 항목화.
- **타 모델 3종**(MS03-CE0 · MS73-HB1 · MS74-HB0) 이미지: 장비 확보 시.
- **fat32-lib 이미지 × sanboot 전달 조합**: PXE 서버 잠정 사용 중단으로 이번에 실측 불가. J1(물리 USB)이 파일시스템 인식 · 탐지 로직을 검증하고, 이 조합은 `docs/T3-checklist.md` 에 적립 — E2-2 CP7 의 qemu 하네스(`scripts/pxe-lab/`) 선행 + 실기 T3 에서 닫는다.

## 6. 병행 권장 — 3호 이월 잔여

같은 실측일에 부담이 없으면 함께 처리한다(별도 계획 불요, 3호 문서 기준): **H4 재캡처**(Syslog 설정 화면 — Settings > Log Settings 류, SEL 조회 화면 아님) · **I1**(PFR 사본 버전 `ipmitool raw` 0x71/0x72) · **I2**(`ipmitool mc info` 가용성) · **I3**(전원 왕복 3회 — On 실패 모드 빈도).

## 7. K1 [S] — BootSourceOverride 실집행: OS 우선 부트 순서 관통 (J1 파생, 4차 개정 신설)

- **발견 경위**: J1 수행 중, OS 가 설치된 서버는 boot priority 가 OS 1순위 · 가상 USB 후순위로 잡혀 있음이 관찰됐다(2026-08-19). §5 구판의 "프로비저닝 게스트는 상시 PXE 우선 부팅" 전제가 실기에서 성립하지 않는다.
- **왜 프로비저닝 흐름의 필수 배선인가**: ① 재프로비저닝 대상(기존 OS 보유 서버)은 전원을 넣어도 OS 로 부팅돼 PXE 에 도달하지 못한다 ② 프로비저닝 중간의 모든 재부팅(phase 전환 · flash 후 `reset -c`)마다 디스크의 OS 로 이탈할 수 있다 ③ 결정적으로 펌웨어 업데이트가 BIOS 설정을 리셋하므로(3호 실측 + 실무 확립), 랙킹 시 수동으로 PXE 우선을 잡아 두는 운영 절차는 E2-2 flash 한 번에 무효가 된다 — 재부팅 직전 Redfish 로 강제하는 길만 남는다. 소속은 E1.5(전원 제어와 같은 클라이언트 · 같은 호출 흐름) 합류가 자연스럽다.
- **실측 항목** (1호에서 `BootSourceOverrideTarget` 허용값에 `Pxe` 존재는 확인됨 — 실집행이 미실측):
  - **K1-a [R]**: `GET /redfish/v1/Systems/Self` 의 `Boot` 객체 현행 채집 — Target · Enabled · Mode 현재값과 AllowableValues 재확인.
  - **K1-b [S]**: `PATCH` 로 `BootSourceOverrideTarget: "Pxe"` + `BootSourceOverrideEnabled: "Once"` → 재부팅 → OS 대신 네트워크 부팅 시도로 진입하는지 → 그 다음 재부팅은 원래 순서(OS)로 복귀하는지(Once 소진 확인).
  - **K1-c [S]**: `"Continuous"` 거동 — 재부팅을 거듭해도 유지되는지, `"Disabled"` PATCH 로 해제되는지.
  - **K1-d [R/S]**: PATCH 가 즉시 반영인지 `Systems/Self/SD`(ComputerSystem pending — 3호 실측) 경유인지, `If-Match` 요건이 있는지.
- **판정 · 채증**: 단계별 요청 · 응답 원문 + 부팅 진입 화면. PXE 서버가 없으므로 "PXE 진입" 판정은 네트워크 부트 시도 화면(iPXE/PXE 계열 메시지 또는 시도 실패 후 다음 항목 폴백)까지로 충분하다 — **부트 순서가 바뀌었는가**만 보면 된다.
- **유의**: OS 설치 서버의 유휴 확인 후 수행. Once 는 1회성이라 안전하고, Continuous 실험은 마지막에 Disabled 원복으로 끝낸다.

## 8. L 계열 — Redfish RBU 로고 재검증 (5차 개정 신설, E2-2 방향 재결정의 게이트)

- **배경**: 가상 USB 확정을 당일 철회했다. 이날 드러난 비용 — BootSourceOverride 필수 배선(K1) · fs 순회 탐지 · sanboot 잔여 검증 · 이미지 조립과 서빙 · flash 무관찰(보드별 10~30분 타임아웃으로만 실패 감지) — 에 비해, Redfish SimpleUpdate 는 실측 2분대 · `FlashPercentage` 진행 관찰 · 부팅 오케스트레이션 불요 · E2-3 과 동일 클라이언트라 구현량이 provider 1개 수준이다. Redfish 의 유일한 결격 = **로고 변경본의 boot 로고 미반영**(2026-08-19 정정: 그 외 갱신은 정상) — 이것이 재검증 대상이다.
- **L1 [R] — 보유 파일 실사**: GIGABYTE 로부터 제공받은 로고 변경본이 어떤 형식인지 확인한다(RBU 실재 여부 · AFU bin 만인지). RBU 가 있으면 정본 `image.RBU` 와 **해시 비교** — 동일 해시라면 지난 실측은 "로고가 들어 있지 않은 파일을 flash 한 것"으로 원인이 즉시 확정된다(벤더 문의 ⑵ 불요화).
- **L2 [X] — 재flash 확인**: 로고 변경본 RBU 가 실재하고 정본과 해시가 다르면, SimpleUpdate 로 재flash → POST 화면에서 로고 반영을 확인한다. **반영되면 Redfish 채택의 마지막 결격이 해소**된다.
- **L3 — RBU 형식이 없을 때**: 실측 불가 — 벤더 문의 ⑴(로고 변경본의 RBU 형식 제공 여부)이 곧 게이트가 된다. 메일 발송으로 대체하고 회신까지 방향 결정을 보류하되, E2-3(BMC = Redfish)과 E1.5 는 방향과 무관하므로 선행 진행 가능.
- **판정 기준**: L2 로고 반영 → **Redfish 채택 유력** / L1 동일 해시 → 원인 확정 후 벤더에 로고 변경본 RBU 요청(⑴) / L2 미반영 재현 → 벤더 ⑵ 회신 대기. 어느 경우든 결과를 원장(Notion E0-4-4)과 T3 에 기록한다.
