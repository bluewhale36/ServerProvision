# 실기 4호 — HF15 일괄 CP7 · 동일 스펙 3대 관찰 기록

> **작성**: 2026-09-10 KST, 앵커 세션(실시간 기록). **목적**: 실기 3호(09-08 · 3종 5대)에서 나온 결함 F-1~F-10 을 정정한 HF15(하위 5 · PR #77 · dev 21abb5b · 7차 재배포)를 **같은 스펙의 서버 3대**로 실기 검증한다. 이번은 판정 항목표가 있는 회차다 — 항목마다 실기 3호의 재현 근거와 이번 기대값을 짝지어 두고, 관찰은 타임라인에 시간순으로 남긴다.

## 1. 환경

| 항목 | 값 |
|---|---|
| 서버 앱 | VM `spvadmin@192.168.1.10` · **7차 재배포(09-09)** = dev 21abb5b(PR #77) · jar b097f22d… · 실 DB DDL `last_boot_at` 적용 |
| 진단 자산 | apkovl = 종전(RAID CLI 2종 동봉) + acpid 조각(handler · events/power · 런레벨 · world) · 부분 apk 저장소 44(acpid · acpid-openrc 포함) · agent.sh(lsblk WWN · 검증 재채집 disks · settle) · 봉인 6/6 · 백업 `/var/lib/serverprovision/backup-hf15-20260909-155258/` |
| 시계 | 09-10 09:25 맥과 일치(실기 3호 뒤 13시간 뒤처졌던 것을 09-09 재동기화) — **개시 직전 재확인** |
| 정의서 | 7 표준 9361-8i · 8 표준 사이버다임 · 9 표준 CRA3338(활성) · 1 · 10 비활성 |
| 게스트 | 실기 3호 5대 중 4대 회수됨(사용자) · 사이버 웨이브(완료)만 활성 — 재투입하려면 회수 필요 |
| 사전 검증 | QEMU 랩(로컬 7777 · 같은 자산)으로 진단 리눅스 부팅(world 트랜잭션 · acpid 기동 · 에이전트 체크인) 스모크 — §2 |

## 2. 사전 검증(QEMU 스모크 · 09-10 · 중단)

로컬 랩(7777 · VM 과 같은 자산)으로 진단 리눅스 부팅을 검증하려 했다. brew 동봉 edk2(UEFI)는 네트워크 부팅 ROM 이 없어 EFI 셸로 떨어졌고(랩 스크립트 예고), legacy BIOS 모드에서는 iPXE 체인 → Alpine init 까지 진행(09:5x) 했으나 **사용자 지시로 중단** — acpid 포함 world 트랜잭션의 통과 여부는 실기 첫 부팅(K1)에서 확인한다.

## 3. 판정 항목(HF15 CP7)

| ID | 하위 | 실기 3호 재현 | 이번 기대 | 판정 |
|---|---|---|---|---|
| K1 | 공통 | — | 진단 리눅스 부팅이 world 트랜잭션을 통과해 배너 · 체크인까지(acpid 추가 뒤 첫 실기) | **O** 10:12:35(#1 · 등록 뒤 40초) |
| K2 | HF15-1 F-2 | BIOS 설정 재시작 뒤 UEFI 셸(성우 1/1) | 설정 재시작 뒤 PXE 복귀 · 판독 통과 | **O 3/3**(10:36~10:38 · 셸 0) |
| K3 | HF15-1 F-2b | RAID → OS 전이 에이전트 reboot 뒤 셸(5/5) | 전이 뒤 PXE 로 돌아와 wimboot 서빙(수동 PXE 0회) | **O 3/3**(상2 10:40:42 · 상3 10:43:41 · 웨이브 10:51:32 — 실기 3호 5/5 셸 구간에서 수동 PXE 0회) |
| K4 | HF15-1 F-4 | 리셋 지연 시 폴링을 복귀로 오인(상2 1/1) | 판독은 `/boot` 도착 뒤에만 — READBACK_MISMATCH 0 | **O 3/3**(READBACK_MISMATCH 0 · O-2 경합 잔존 주의) |
| K5 | HF15-1 | — | 종단 뒤 BMC BootSourceOverride 가 Disabled 로 풀림(Redfish 조회) · 조정자 로그 `[pxe-guarantee]` 세움/해제 | 세움 3/3(개시) · 해제 3/3(wimboot 서빙) · 재무장 2회(재시도) 전부 `반영 확인` · **종단 뒤 Redfish GET 3/3**(상2 · 상3 10:58 · 웨이브 11:07 `BootSourceOverrideEnabled=Disabled` · Target Pxe · Mode UEFI) → **O** |
| K6 | HF15-2 F-3 | `set bgi=` 거절(9361-8i 2/2) | `autobgi=off` Success(09-08 18:44 실증 재확인) | **O**(10:49:02 웨이브 · v0 · v1 둘 다 `AutoBGI Off Success 0` · 집행 원장 로그) |
| K7 | HF15-2 F-8 | 재시도가 옛 인벤토리로 계획(2/2) | RAID 실패 → [재시도] → 재채집 → 잔여 재구성(deleteExisting=true) — 실패가 안 나면 인위 재현 여부는 사용자 판단 | |
| K8 | HF15-3 F-5 | 다른 포트 부팅이 NIC 섹션에 안 보임 | 다른 포트로 부팅 시 NIC 행 추가 · 같은 포트 IP 재임대 갱신 | **O**(17:16:05 상2 — primary …1a:a2/.193 인 서버를 …1a:a1/.139 로 PXE → 저널 "호스트 NIC 바인딩 추가(다른 포트로 부팅)" · DB 바인딩 2행 · 상세 NIC 표 2행(Primary 표시는 첫 행만)) |
| K9 | HF15-4 F-10 | 정상 종료 무응답(3/3) | 진단 리눅스에서 정상 종료 → poweroff · 화면 감시 문구 · Windows 로그온 화면 재현 여부 | **X → O** — 16:43:57 1차 X(F-5 · button 미적재 · acpid crashed) → 8차 배포 뒤 **17:12:55 GracefulShutdown → 17:13:05 PowerState Off(10초 안) · 체크인 17:12:54 정지** · 콘솔 `button 28672 0` · acpid started(사용자 실측) |
| K10 | HF15-5 F-6 | SAS3008 3/3 HDD 오설치 | SSD OS 볼륨에 설치 · 서빙 meta `diskBasis=lsblk` · DiskID 가 lsblk 순서 | **O 3/3**(상2 10:57:28 Disk 0 = spvR1V1 SSD · 상3 10:58:23 **Disk 1** = spvR1V2 SSD · 웨이브 11:06:24 Disk 0 = spvR1V1 VD0 — 전부 완료 보고 UniqueId 일치. basis 는 lsblk 가 아니라 `inventory-order`(② · F-3 파생) — 실기 3호 3/3 오설치가 0/3 로) |
| K11 | HF15-5 F-7 | IR UniqueId 형식 불일치 | 완료 보고 `diskConfirmed=true`(NAA 변환 일치) · lsblk WWN 형식 실측 | **O 3/3**(상2 `…A3B1AA501DAF4D09` · 상3 `…342CAD252A20520E` · 웨이브 `600605B00D104D7D32353909095FA6E9` = 기대값 대소문자만 다름 → `diskConfirmed=true` 3/3 · IR wwid 뒤집기 변환 2 · MegaRAID NAA 그대로 1) · lsblk WWN 은 빈 값(F-3)이라 형식 실측은 미달 |
| K12 | HF15-5 F-1 | RAID 전 카드 BLOCKED | 개시 전 카드 "RAID 구성 뒤 확정 — 계획의 OS 영역 …" · 준비도 READY | **O** 10:19(상2 spvR1V1 · 상3 spvR1V2) |
| K13 | HF15-5 F-9 | VD 행 종류 HDD · 전송 — | RAID 뒤 디스크 표에 "RAID 볼륨 · spvR1V1" 행 · 가상 미디어 숨김 | **△ → O**(10:50 3/3 화면에서는 배지 미표시 — wwn 이 비어(F-4) 매칭 불가 · 표기 억제 부분은 O) → **17:11 8차 배포 뒤 상2 재진단: "RAID 볼륨 · spvR1V1 — 222.6G" · "RAID 볼륨 · spvR1V2 — 14.6T" 배지 표시** |
| K14 | HF15-1 O-10 | — | [재시도 후 네트워크 부팅] 이 재시도 + PXE 부팅을 한 번에(사용 기회가 있을 때) | **O 2회**(10:38:16 · 10:46:02 웨이브 — 재시도 → Continuous 재무장 → 전원 발행 · 2회째에 BMC 세팅 통과) |

## 4. 게스트 대장 (등록되는 대로 채움)

| 이름 | guest id(앞 8) | 종류 · RAID 카드 | 디스크 | 정의서 | 등록 | 개시 | 최종 |
|---|---|---|---|---|---|---|---|
| **사이버 웨이브**(#1 · …961a7d · IP .131) | 01a088df-1249 | 종류 C' · MegaRAID 9361-8i(1000:9361) · 볼륨 0 | 물리 8(전부 UGood · lsblk 빈 목록 예상) | 8(할당 28 · 10:19:47) | 10:11:55 | 10:19:49 | 진단 완주 10:12:35 |
| **일산 상2**(#2 · …961aa1 · IP .139) | 01a088e2-5008 | 종류 B · MPT_IR SAS3008(1458:3008) · **볼륨 2 잔존**(spvR1V1 OS SSD · spvR1V2 DATA HDD — 실기 3호 산물) | 물리 4 · lsblk sda SSD 222.6G · sdb HDD 14.6T | 9(할당 27 · 10:19:16) | 10:15:28 | 10:19:27 | 진단 완주 10:16:14 |
| **일산 상3**(#3 · …9ac4e8 · IP .114 · MAC c4:e9) | 01a088e4-72c3 | 종류 B · SAS3008 · 볼륨 2 잔존 | 물리 4 | 9(할당 26 · 10:19:16) | 10:17:48 | 10:19:35 | 진단 완주 10:18:49 |

## 5. 타임라인 (KST · 저널 · DB 실측)

| 시각 | 게스트 | 사건 | 비고 |
|---|---|---|---|
| 10:11:55 | #1(…961a7d · 사이버 웨이브 장비) | PXE 부팅 요청(IP .131) → **신규 서버 등록**(MS04-CE0 · MAC …1a:7d) | 실기 3호 사이버 웨이브가 회수된 뒤 재투입(U6 경로). 진단 이미지(acpid) 첫 실기 부팅 → K1 관찰 |
| 10:12:35 | #1 | **진단 완주(미개시 유보)** — RAID 9361-8i(1000:9361) · 물리 8 · 볼륨 0 적재 | **K1 O** — acpid 가 든 world 트랜잭션을 통과해 등록 40초 만에 수집 보고까지(응급 셸 없음) |
| 10:12:36 | #1 | **조정자 첫 실 BMC 동작** — 미개시(창 밖) → `PXE 보장 해제 : 반영 확인`(BootSourceOverride Disabled PATCH · 되읽기 일치) | K5 부분 — 실 BMC 가 PATCH 를 받아들임. 로그 문구에 라벨이 두 번 찍힘(O-1 · 표기만) |
| 10:15:28 | #2(…961aa1 · 실기 3호 일산 상2 장비) | PXE 부팅 요청(IP .139 · MAC …1a:a1) → **신규 서버 등록** | SAS3008 계열 — K10 · K11(디스크 번호 · IR 확증)의 표본 |
| 10:16:14 | #2 일산 상2 | 진단 완주(미개시 유보) — SAS3008(1458:3008) · 물리 4 · **볼륨 2**(실기 3호의 spvR1V1 · spvR1V2 잔존 · wwid 005829e2… · 00f16993…) | lsblk = sda SSD 222.6G · sdb HDD 14.6T(IR 볼륨 2개) — **WWN 둘 다 null(F-1)** |
| 10:16:15 | #2 | 조정자 해제 · 반영 확인 | K5 부분 2/2 |
| 10:17:48 | #3 일산 상3(…9ac4e8) | PXE 부팅 요청(IP .114 · **MAC …c4:e9** — 실기 3호 등록 때는 …c4:e8) → 신규 서버 등록 | 재투입이라 새 primary NIC = c4:e9. K8 은 이 뒤 다른 포트 부팅에서 본다 |
| 10:2x | — | **F-1 핫픽스 배포** — agent.sh 가 sysfs `/sys/block/<dev>/device/wwid` 로 WWN 을 채우게(HF15-5-1 · PR #78 · dev e79d204) → VM 앱 교체 엔드포인트로 agent.sh 만 교체(302 · 서빙 확인) | 다음 진단 부팅(펌웨어 · 설정 · RAID phase 재진입)부터 적용 — RAID 검증 재채집(본안 ①)이 이 에이전트로 돈다 |
| 10:18:49 | #3 일산 상3 | 진단 완주(미개시 유보) — SAS3008 · 물리 4 · 볼륨 2(실기 3호 잔존) | K1 3/3 · 10:18:51 조정자 해제 반영(K5 부분 3/3) |
| 10:19:16 | #2 · #3 | **그룹 일괄 할당** — 정의서 9(표준 CRA3338) → 할당 27(상2) · 26(상3) · 보유 phase [펌웨어 갱신 · 펌웨어 설정 · RAID 구성 · OS 설치] | 개시는 서버별 수동(U3-5 이연 그대로) |
| 10:19:2x | #2 · #3 | **개시 전 카드(K12)**: 상2 "설치 대상 디스크 — RAID 구성 뒤 확정 — 계획의 OS 영역 spvR1V1" · 상3 "… spvR1V2" · 준비도 READY | **K12 O 2/2** — 실기 3호 F-1(BLOCKED)과 대조. 계획의 OS 이름이 실기 3호 결과(상2 = V1 · 상3 = V2)와 같다 |
| 10:19:27 · 10:19:35 | #2 · #3 | **개시**(POST /start 200) → 10:19:29 · 10:19:38 조정자 `프로비저닝 중 PXE 보장 : 반영 확인`(Continuous PATCH · 되읽기 일치) | **K5 세움 2/2** — 미개시 해제 → 개시 Continuous 로 뒤집힘 |
| 10:19:38 · 10:19:40 | #2 · #3 | 펌웨어 갱신 착수 — BeginFlashStep 전원 차단 | 실기 3호와 같은 순서(BIOS → BMC 굽기 ~18분 뒤 전원 투입 · 이번엔 Continuous) |
| 10:19:47 · 10:19:49 | #1 사이버 웨이브 | 할당 28(정의서 8 표준 사이버다임 · 4 phase) → **개시** → 10:19:51 조정자 Continuous 반영 확인 | 세 대 전부 개시 · K5 세움 3/3 |
| 10:20:12~14 | #1 · #2 · #3 | #1 펌웨어 착수(전원 차단) · #2 · #3 BIOS 굽기 시작(F29 · image.RBU) | 실기 3호와 같은 시퀀스 — BIOS → BMC → 전원 투입(PXE Continuous) |
| 10:21:48 · 10:22:19 | #2 · #3 · #1 | BIOS 전송 완료(토큰 회수 · 1분 35초 / #1 1분 34초) → #2 · #3 BMC 굽기 시작(13.06.29 · rom_v130629.ima_enc) | |
| 10:26:44 | #2 일산 상2 | BMC 축 전송 완료 · 토큰 회수(4분 24초 · Task 번호 어긋남 → 컬렉션 최신 Task 관용 판정 재현) | 실기 3호(5대 동시 9~10분)보다 절반 — 동시 수가 줄어 BMC 축이 빨라짐 |
| 10:26:45 · 10:27:15 | #3 · #1 | BMC 축 전송 완료(4분 24초 · 4분 23초 · 관용 판정 3/3) | |
| 10:27:24 | #2 일산 상2 | **굽기 완료 · 전원 투입** — `프로비저닝 중 PXE 보장 : 반영 확인 · 전원이 켜졌습니다(Reset(On) → PowerState 폴링)` | PowerOnStep 이 Continuous 로 무장(HF15-1) — 이어지는 PXE · 설정 재시작(K2) · RAID 뒤 재부팅(K3)에서 셸 0회여야 함 |
| 10:27:32 · 10:28:10 | #3 · #1 | 굽기 완료 · 전원 투입(Continuous 반영 확인) | 3/3 |
| 10:31:36 | #2 일산 상2 | PXE 부팅 요청(전원 투입 4분 12초 뒤 · IP .139 · 같은 MAC) | 굽기 검증 → BIOS 설정 PATCH · ForceRestart(K2 · K4 관찰 구간) |
| 10:31:55 | #2 · #1 | #2 VerifyFlash 반영 확인 완료(펌웨어 phase 12분 28초) · #1 PXE 복귀(3분 45초) | 실기 3호 18~19분 대비 단축 |
| 10:32:01 | #2 일산 상2 | **BIOS 설정 5개 PATCH(pending 확인) · ForceRestart 발행 — `프로비저닝 중 PXE 보장 : 반영 확인`** | K2(내부 재시작이 Once 를 소비하던 F-2) · K4(폴링 오인 F-4 — 실기 3호에서 이 서버가 재현) 판정 구간. 판독은 이번엔 `/boot` 도착 뒤 |
| 10:32:25 · 10:32:27 · 10:32:29 | #1 · #3 | #1 두 번째 `/boot`(30초 뒤 · iPXE 대기 체인 폴링) → VerifyFlash 반영 확인 · #3 PXE 복귀(전원 투입 4분 57초) | **주의(O-2)**: iPXE 대기 체인의 30초 `/boot` 폴링도 `last_boot_at` 을 올린다 — F-4 정정(복귀 증거 = /boot)이 이 경우엔 3호와 같은 경합에 노출될 수 있다. #2 는 10:31:36 이후 `/boot` 가 없어(진단 리눅스 체크인 모드) 판독이 정확히 대기 중 |
| 10:32:37 | #1 사이버 웨이브 | BIOS 설정 5개 PATCH · ForceRestart(Continuous 반영 확인) | K2 · K4 두 번째 표본 — 직전 `/boot` 10:32:25 가 대기 체인이라 다음 30초 폴링(≈10:32:55)이 리셋보다 먼저 오면 F-4 재현 위험 |
| 10:32:58 · 10:32:59 · 10:33:12 | #3 일산 상3 | VerifyFlash 반영 확인(펌웨어 phase 13분 23초) → 대기 폴링 `/boot` → BIOS 설정 5개 PATCH · ForceRestart(Continuous) | #1 의 10:32:55 폴링은 오지 않았다(리셋이 먼저) — 세 대 전부 설정 재시작 뒤 부팅 대기 · READBACK_MISMATCH 0(10:33 기준) |
| 10:36:19 · 10:36:39 | #2 · #1 | **설정 재시작 뒤 PXE 복귀**(4분 18초 · 4분 2초) — 셸 0 | **K2 O 2/2**(실기 3호 F-2 구간 · 성우/웨이브 장비 1/1 재현했던 곳) |
| 10:36:43 · 10:36:44 | #1 · #2 | **BIOS 설정 5개 반영 확인, 다음 축으로** — 판독은 실제 `/boot`(10:36:39 · 10:36:19) 뒤에만 | **K4 O 2/2**(상2 = 실기 3호 F-4 재현 장비) |
| 10:37:15 | #1 사이버 웨이브 | **BMC_SETTING FAILED — WRITE_REJECTED : COLD_REDUNDANT**(`GET /api/cold_redundant-status` → BMC 거절 · code 1334 "Error in Getting Cold Redundant Status") → 10:37:18 조정자 해제(실패 = 창 밖) | **F-2(4호)** — 실기 3호(17:49)에는 같은 BMC 에서 4개 전부 적용됐다 · BMC 굽기 · 재기동 직후의 일시 오류로 추정. 사용자 [재시도] 필요 — [재시도 후 네트워크 부팅] 이면 K14 기회 |
| 10:37:17 | #2 일산 상2 | BMC 표준 세팅 4개 적용 · 축 종결 → RAID 단계 | 설정 phase 5분 16초 |
| 10:38:05 | #2 일산 상2 | RAID 단계 진입 첫 체크인 → **인벤토리 재채집**(HF15-2 "진입 대기 = 재채집") — 물리 4 · 볼륨 2(잔존) | 계획은 이 실물로(잔존 spvR* → deleteExistingFirst) · 다음 = RAID_APPLY(sas3ircu 삭제 + 생성) · 검증 재채집(새 agent · sysfs wwid) |
| 10:38:09 · 10:38:16 | #2 일산 상2 | **RAID 집행 · 검증 통과(11초)** — `sas3ircu 0 delete` → `create RAID1 MAX 1:0 1:2 spvR1V1` → `create RAID1 MAX 1:1 1:3 spvR1V2` 전부 Success · 새 wwid(V1 094daf1d… · V2 02a732ce…) · 인벤토리 [322 spvR1V2, 323 spvR1V1] · **OS 가시 디스크 갱신 2(sda HDD 14.6T · sde SSD 222.6G)** · 커서 OS_INSTALLING | K7 은 실패가 없어 미발동 · K13 재료 확보. **F-3(4호)**: 검증 재채집의 disks 에 `"wwn":""` — 새 에이전트가 돌았으나(키 존재) sysfs `device/wwid` 도 비었다 → 본안 ① 재료 없음 · ② 계열 순서 규칙이 번호를 낸다(예상 DiskID 0 = spvR1V1) |
| 10:38:16 | #1 사이버 웨이브 | 사용자 **[재시도 후 네트워크 부팅]**(POST /retry-boot 200 · 4.6초) → 10:38:13 조정자 Continuous 재무장 → 10:38:19 BMC 세팅 재실행 → **같은 COLD_REDUNDANT 거절(2/2) → 재실패** → 10:38:22 해제 | K14 동작 자체는 O(재시도 + 무장 + 전원). F-2 는 일시 오류가 아닐 수 있음 — 3호와 다른 점: 이번엔 굽기 뒤 BMC 13.06.29 로 갱신된 직후 · 3호(17:49)는 같은 버전이었나? 확인 필요 |
| 10:38:20 · 10:38:51 | #3 일산 상3 | 판독 통과(BIOS 설정 5개 반영 확인 · 설정 재시작 뒤 PXE 복귀 10:38:20 = 5분 8초) → BMC 표준 세팅 4개 적용 · 축 종결 → RAID 단계 | **K2 · K4 3/3** · 상3 BMC 는 COLD_REDUNDANT 통과(웨이브 BMC 만 거절) |
| 10:39:20 · 10:40:21 · 10:40:36 | #3 일산 상3 | RAID 단계 진단 부팅 → 진입 재채집(볼륨 2 잔존) → **RAID 집행 · 검증 통과 · OS 가시 디스크 갱신 2** · 커서 OS_INSTALLING | 2/2 · 상3 도 wwn 빈 값(F-3 재현 2/2) |
| 10:40:33 | #1 사이버 웨이브 | PXE 부팅 요청(재시도 후 네트워크 부팅의 ForceRestart 결과 · 2분 17초) — 실패 상태라 대기 | K14 의 전원 부분 실증(호스트가 PXE 로 돌아옴) |
| 10:40:42 | #2 일산 상2 | **RAID 뒤 에이전트 reboot → 수동 개입 0 으로 PXE 복귀(2분 26초) → wimboot 서빙 = 착수 : diskId=0 · basis=inventory-order · expectedUniqueId=600508e000000000a3b1aa501daf4d09**(V1 wwid 094daf1d… 뒤집기) → 10:40:44 조정자 해제(창 = 서빙에서 닫힘) | **K3 O 1/3**(실기 3호 5/5 셸 구간) · K5 해제 O · 번호는 ② 규칙 — Windows 가 V1 을 Disk 0 으로 열거하면 SSD 에 설치(K10) · UniqueId 일치(K11) |
| 10:43:41 | #3 일산 상3 | **RAID 뒤 에이전트 reboot → PXE 복귀(3분 5초 · 수동 0) → wimboot 서빙 = 착수 : diskId=1 · basis=inventory-order · expectedUniqueId=600508e000000000342cad252a20520e**(V2 wwid 0e52202a… 뒤집기) → 10:43:42 조정자 해제 | **K3 O 2/3** · K5 해제 2/2. 상3 은 인벤토리 [322 spvR1V2(OS · SSD), 323 spvR1V1(HDD)] — id 내림차순이라 OS 가 **Disk 1**. 상2(OS = 323 → Disk 0)와 번호가 다르다 — 만든 순서가 정의서 규칙 순이 아니라 잔존 삭제 뒤 create 순(상2 V1 먼저 · 상3 V2 먼저?)에 따라 갈린 것. 두 서버의 완료 보고가 ② 규칙의 확증 표본 2개 |
| 10:46:02 | #1 사이버 웨이브 | 사용자 **[재시도 후 네트워크 부팅] 2회째**(POST /retry-boot 200 · 5.9초) → Continuous 재무장 → 10:46:23 **BMC 표준 세팅 4개 적용 · 축 종결**(COLD_REDUNDANT 3회째 시도에서 통과) → RAID 단계 진입 대기 | **F-2(4호) = 일시 오류로 판정** — 같은 BMC · 같은 요청이 10:37 · 10:38 거절 → 10:46 통과. 세팅 단계가 GET 오류를 WRITE_REJECTED 로 즉시 실패시키는 대신 재시도로 흡수해야 한다는 근거(적립) |
| 10:48:58 · 10:49:02 | #1 사이버 웨이브 | RAID 단계 진입 재채집(1000:9361 · 물리 8 · 볼륨 0) → **RAID 집행**(storcli64 · `add vd raid1 spvR1V1 252:6,252:7` · `add vd raid5 spvR2V1 252:0~5`) — **`/c0/v0 set autobgi=off` · `/c0/v1 set autobgi=off` 둘 다 `AutoBGI Off Success 0`** · accesspolicy=rw · `start init force` 전부 Success | **K6 O** — 실기 3호 F-3(`set bgi=` 거절 2/2)이 `autobgi` 로 정정된 첫 실집행 |
| 10:49:09 | #1 사이버 웨이브 | **검증 통과 · raid_volume 2건 · OS 가시 디스크 갱신 2**(sda 446.6G · sdb 54.6T · tran 빈 값 · **wwn 빈 값**) · 커서 OS_INSTALLING | K7 은 실패가 안 나 미수행. MegaRAID VD 도 sysfs wwid 가 비어 있음 — F-3 이 IR 한정이 아님(§6 F-3 보강) |
| 10:50 | #1 · #2 · #3 | **K13 화면 확인**(curl · 상세 페이지 3/3): 웨이브 sda "— — 446.6G" · sdb "— — 54.6T"(종류 · 전송 고정 표기 사라짐) · 상2 sda HDD SAS 14.6T · sde SSD SAS 222.6G · 상3 sda SSD SAS 222.6G · sde HDD SAS 14.6T · 가상 미디어 행 0 · 카드 "디스크 0 · 근거 카드 계열 순서 규칙"(웨이브 서빙 전 · 상2 서빙 뒤 원장 meta) · 상3 "디스크 1" | **K13 △** — 배지 "RAID 볼륨 · spvR1V1" 은 3/3 미표시(wwn 매칭이라 F-3 파생). 표기 억제 부분은 O |
| 10:50:58 | #2 일산 상2 | Windows 설치 재부팅 뒤 PXE 재진입 → **설치 중 재진입 1회 → exit(로컬 부팅)**(E4-1-a-3 설계 · 60분 · 5회 창) | 보장 해제(10:40:44) 뒤라 부팅 순서는 BIOS 설정값 — 네트워크가 앞이라 PXE 로 들어왔고 exit 로 디스크 부팅. 실기 3호와 같은 경로 |
| 10:51:32 | #1 사이버 웨이브 | **RAID 뒤 PXE 복귀(2분 23초 · 수동 0) → wimboot 서빙 = 착수 : diskId=0 · basis=inventory-order · expectedUniqueId=600605b00d104d7d32353909095fa6e9**(VD0 spvR1V1 NAA 그대로) → 10:51:33 조정자 해제 | **K3 O 3/3** · K5 해제 3/3. MegaRAID 는 나열 순 그대로(VD0 = Disk 0) — 실기 3호 9361-8i 2/2 정상 설치와 같은 규칙 |
| 10:57:28 | #2 일산 상2 | **설치 완료 보고 → 종단**(개시 10:19:27 → 38분 1초 · 서빙 뒤 16분 46초 · 재진입 2) — computerName SPV-0F961AA1 · Windows Server 2025 Standard 10.0.26100 · 드라이버 47 · 문제 장치 0 · **`installedDiskUniqueId=600508E000000000A3B1AA501DAF4D09` = 기대값 → `diskConfirmed=true`** · 10:58 BMC Redfish GET `BootSourceOverrideEnabled=Disabled` | **K10 O 1/3 · K11 O 1/3 · K5 종단 1/3**. O-3 첫 표본: ② 가 준 Disk 0 을 Windows 가 확인했고, ①(lsblk 순서 sda HDD → sde SSD)이었다면 Disk 1 로 **틀렸을** 번호다 |
| 10:58:23 | #3 일산 상3 | **설치 완료 보고 → 종단**(개시 10:19:35 → 38분 48초 · 서빙 뒤 14분 42초 · 재진입 2) — SPV-0F9AC4E8 · 드라이버 47 · 문제 장치 0 · **`installedDiskUniqueId=600508E000000000342CAD252A20520E` = 기대값(Disk 1 = spvR1V2) → `diskConfirmed=true`** · BMC Redfish GET Disabled | **K10 O 2/3 · K11 O 2/3 · K5 종단 2/3**. O-3 두 번째 표본 — ② 의 Disk 1 이 맞았고 ①(lsblk sda SSD → sde HDD)이면 Disk 0 으로 틀렸을 번호. **① 의 전제는 MPT_IR 에서 2/2 반대**(§6 O-3 판정) |
| 11:06:24 | #1 사이버 웨이브 | **설치 완료 보고 → 종단**(개시 10:19:49 → 46분 35초 · BMC 세팅 재시도 2회 포함 · 서빙 뒤 14분 52초 · 재진입 2) — SPV-0F961A7D · 드라이버 47 · 문제 장치 0 · **`installedDiskUniqueId=600605B00D104D7D32353909095FA6E9` = 기대값(Disk 0 = spvR1V1 VD0) → `diskConfirmed=true`** · 11:07 BMC Redfish GET Disabled(bmc_ip 192.168.1.1) | **K10 O 3/3 · K11 O 3/3 · K5 종단 3/3 → 세 대 전부 종단**. 종단 뒤 조정자 로그 없음 = 서빙 때 이미 RELEASE 로 맞춰져 no-op(설계대로) |
| 16:38~16:47 | #2 일산 상2(재투입 · 삭제 뒤 신규 등록 01a08a43) | 16:40:52 MAC …1a:a2(다른 포트) · IP .193 로 PXE → **신규 서버 등록**(종전 행이 삭제돼 K8 의 "바인딩 추가" 경로가 아님) → 16:41:16 정의서 11 할당(29) → 16:41:54 진단 완주(미개시 유보 · SAS3008 · 볼륨 2 잔존) → 16:41:55 조정자 해제 | 화면에 `agent.sh: line 32: 1: parameter not set` 2회(F-4) |
| 16:43:57 | #2 | **[정상 종료] → Reset(GracefulShutdown) 접수 → 호스트 켜진 채 체크인 계속(K9 X)** · BMC Task: "IPMI 명령 성공 · 섀시 전원 상태 불변" | 콘솔 실측(사용자): `rc-service acpid status` = crashed · `lsmod | grep button` 없음 · `dmesg` button 없음 · `/etc/acpi/handler.sh button/power` 수동 호출 시 **전원 꺼짐**(핸들러 정상) → 원인 = `button` 모듈 미적재(+acpid 가 그 전에 떠서 죽음) |
| 16:55~16:57 | 배포(8차) | **HF15-5-2 + HF15-4-1 핫픽스 배포** — ① jar(sha 438eb3a8… · 3,117 green · `WindowsDiskSelection` 의 ① lsblk 근거 폐기 · 번호 = 계열 순서 규칙만 · `Basis.label()`) 16:57:08 기동 ② apkovl(firstboot: `modprobe button` + `before acpid`) 16:56 교체 · DIAGNOSTIC 재봉인(기록 6) ③ agent.sh(`esc` 오용 정정) 16:57:20 교체 엔드포인트 · 백업 `/var/lib/serverprovision/backup-hf15-4-1-20260910-165547/` | 순서가 중요했다 — agent.sh 만 먼저 갈면 wwn 이 채워져 옛 jar 가 ① 로 IR 오번호를 냈을 것. 다음 부팅에서 K9 재시도 · F-4 화면 오류 소거 · wwn 실측(F-3 재판정) |
| 17:06:41 · 17:09:53 · 17:11:24 | #2 일산 상2 | 사용자 Reset(On) → PXE(같은 포트 …1a:a2) → 새 apkovl · agent.sh 로 진단 완주(미개시 유보) — **하드웨어 스펙 디스크에 wwn 실림**: sda SSD `600508e000000000a3b1aa501daf4d09` · sdb HDD `600508e000000000703df079ce32a702` | **F-4 정정 확인 · F-3 종결** — sysfs `device/wwid` 는 IR 볼륨에서도 NAA 를 그대로 준다(오전 Windows 완료 보고의 UniqueId 와 같은 값). 오전과 lsblk 순서가 다르다(오전 재채집 [sda HDD, sde SSD] → 지금 [sda SSD, sdb HDD]) — Linux 열거 순서가 부팅마다 흔들린다는 O-3 보강 |
| 17:12:55 | #2 일산 상2 | **[정상 종료] → Reset(GracefulShutdown) → 17:13:05 BMC PowerState Off**(10초 안 · 마지막 체크인 17:12:54) — 콘솔 사전 확인 `lsmod`: `button 28672 0` · `rc-service acpid status`: started | **K9 O** — F-5 정정(firstboot `modprobe button` + `before acpid`) 확정. 17:13:16 사용자 Reset(On) → 17:13:21 On(재부팅) |
| 17:16:05 | #2 일산 상2 | **원래 포트(…1a:a1 · IP .139)로 PXE → "호스트 NIC 바인딩 추가(다른 포트로 부팅)"** · host_nic_binding 2행(…1a:a2 primary 16:40:52 · …1a:a1 17:16:05) · 상세 NIC 표 2행 | **K8 O** — 실기 3호 F-5(다른 포트 부팅이 NIC 섹션에 안 보임) 정정 확인 |
| 17:17:42~17:22:35 | #2 일산 상2(회귀 확인 회차) | 개시(정의서 11 · 3 phase) → 17:18:14 BIOS 설정 PATCH · ForceRestart(Continuous) → 17:20:38 PXE 복귀(셸 0) → 17:20:45 판독 통과 → 17:21:16 BMC 세팅 4개 → 17:22:24 RAID 진입 재채집 → **17:22:35 집행 · 검증 통과(11초)** · 검증 disks **wwn 동봉**: sda 14.6T `…21efa6c99fd25902`(= V2 wwid 0259d29f… 뒤집기) · sde 222.6G `…70d7cb8eee0aad07`(= V1 wwid 07ad0aee… 뒤집기) · 인벤토리 [322 spvR1V2, 323 spvR1V1(OS)] → 카드 **"디스크 0 · 근거 카드 계열 순서 규칙"** · 배지 "RAID 볼륨 · spvR1V2 — 14.6T" · "RAID 볼륨 · spvR1V1 — 222.6G" | **① 폐기의 회귀 증거** — lsblk 는 [sda HDD, sde SSD] 라 옛 ① 이면 wwn 매칭으로 SSD = **Disk 1**(오답)을 확신으로 냈을 구성. 새 jar 는 ② 로 Disk 0(오전 Windows 확증값과 같은 볼륨 배치). K2 · K3(대기) · K5 · K13 재현 |
| 17:25:07 → 17:42:55 | #2 일산 상2(회귀 확인 회차) | RAID 뒤 PXE 자동 복귀(2분 32초) → **wimboot 서빙 diskId=0 · basis=inventory-order · expectedUniqueId=600508e00000000070d7cb8eee0aad07** → 17:25:08 해제 → 재진입 2(17:36 · 17:40) → **완료 보고 · 종단**(개시 17:17:41 → 25분 14초 · 펌웨어 갱신 phase 없음) — SPV-0F961AA1 · 드라이버 47 · 문제 장치 0 · **`installedDiskUniqueId=600508E00000000070D7CB8EEE0AAD07` = 기대값 → `diskConfirmed=true`** · BMC Disabled · On | **8차 핫픽스 회귀 완주** — wwn 이 실린 상태에서 ② 가 낸 Disk 0 을 Windows 가 확증(K10 · K11 4/4 누적). 옛 ① 이면 Disk 1(HDD)이었을 구성 |

## 6. 발견 · 관찰

(결함은 F-, 관찰은 O- 로 번호)

- **F-1(#2 · 10:16:14 · HF15-5 실기 결함 · 즉시 정정)** — 진단 리눅스의 `lsblk -o WWN` 이 IR 볼륨(sda · sdb)에서 빈 값이다. lsblk 의 WWN 열은 udev DB(ID_WWN)에서 오는데 진단 리눅스는 mdev 라 udev 가 없다 — HF15-5 본안(lsblk 순서 + 식별자 매칭)이 실기에서 재료를 못 받아 늘 계열 순서 규칙(②)으로 내려갔을 것이다. 정정 = 에이전트가 `/sys/block/<dev>/device/wwid`(커널 sysfs · `naa.<hex>`)를 폴백으로 읽어 hex 만 싣는다(HF15-5-1 · PR #78). CP5 샌드박스는 하네스가 WWN 을 직접 동봉해 이 결손을 못 봤다 — 실기에서만 드러나는 종류.

- **F-3(4호 · #2 · 10:38:16 · HF15-5 본안 재료)** — 검증 재채집의 lsblk 가 새 에이전트(HF15-5-1 · `"wwn"` 키 존재)로 돌았는데도 IR 볼륨 두 장치의 wwn 이 빈 문자열이다. lsblk 의 WWN(udev)뿐 아니라 sysfs `/sys/block/<dev>/device/wwid` 도 비어 있었다는 뜻 — mpt3sas IR 볼륨에 대해 커널(6.12)이 VPD 83 기반 wwid 를 채우지 않거나 파일이 없다. 그래서 본안 ①(lsblk 순서 + 식별자 매칭)은 이 환경에서 재료를 못 얻고, ②(계열 순서 규칙 · MPT_IR = ID 내림차순)가 번호를 낸다 — 실기 3호 3/3 근거가 있는 규칙이라 이번 설치 결과(K10 · K11)로 다시 검증된다. 후속(HF15-5-2) = ① 재료를 `vpd_pg83`(바이너리 VPD 83 의 NAA 지정자 파싱) 또는 크기 등급 매칭(계획 usableBytes ↔ lsblk SIZE · 동급 1개일 때만)으로 대체 · 다음 실기에서 `ls /sys/block/<dev>/device` 의 실물 키 확인. **10:49 보강** — 사이버 웨이브(MegaRAID 9361-8i · VD 2)도 검증 재채집의 wwn 이 빈 문자열이다. 서빙 중 `agent.sh` 에 `device/wwid` 폴백이 들어 있음을 VM 에서 확인했으므로(3 곳), 진단 리눅스에서 `/sys/block/sdX/device/wwid` 가 두 계열 모두 없거나 비어 있다는 뜻이다(커널이 VPD 83 을 붙이지 않았거나 파일 부재). HF15-5-2 는 다음 실기 전에 진단 리눅스 셸에서 `ls /sys/block/sdX/device/` · `vpd_pg83` 유무 · `sg_inq` 가능 여부를 실측하고 설계한다 — 추측으로 폴백을 더 쌓지 않는다. 그때까지 ① 은 죽어 있고 ② 만 돈다(O-3 참조).
- **F-4(4호 · 16:41 · HF15-5-1 결함 · F-3 의 진짜 원인)** — 진단 리눅스 화면에 `/usr/local/bin/agent.sh: line 32: 1: parameter not set` 이 디스크 수만큼(2회) 찍힌다. 32행은 `esc()` 로 인자 `$1` 을 이스케이프하는 함수인데, HF15-5-1 이 넣은 `collect_disks_json` 이 `"$(printf '%s' "$wwn" | esc)"` 처럼 **stdin 필터로 잘못 호출**했다. `set -u` 라 `$1` 미설정 오류가 stderr 로 나가고 치환 결과는 빈 문자열 — 그래서 sysfs 가 wwid 를 줬더라도 JSON 의 `wwn` 은 항상 `""` 이었다. **F-3(sysfs wwid 도 비어 있다)은 이 결함에 가려 검증되지 않은 추정**으로 되돌린다. 정정은 `"$(esc "$wwn")"` 한 줄이지만, **지금 단독으로 고치면 안 된다** — wwn 이 채워지는 순간 `WindowsDiskSelection.judge` 가 ①(lsblk 순서)을 우선 택하고, O-3 판정대로 MPT_IR 에서 ① 은 반대 번호를 낸다(상2 · 상3 이 HDD 에 설치됐을 것). 정정은 HF15-5-2 에서 ① 폐기 · 계열 분기와 **한 묶음**으로 낸다.
- **F-5(4호 · 16:43:57 · K9 X)** — 진단 리눅스(미개시 유보)에 [정상 종료] → `Reset(GracefulShutdown)` 이 BMC 에 접수됐으나 호스트가 꺼지지 않았다. BMC Task 메시지: "IPMI command for GracefulShutdown operation returned success but power state of chassis did not change". 배포된 apkovl 에는 `etc/runlevels/default/acpid` · `etc/acpi/handler.sh` · `etc/acpi/events/power`(`event=button/power.*` → handler) · world 의 acpid 가 들어 있고 저장소에도 acpid-2.0.34-r6 · acpid-openrc 가 있다. 남은 후보: ① `button` 커널 모듈 미적재(hwdrivers 가 sysinit 에 있으나 modloop 뒤 ACPI modalias 를 다시 훑는지) ② acpid 서비스 기동 실패 ③ BMC 의 soft-off 가 ACPI 전원 버튼 이벤트로 오지 않는 플랫폼. 게스트 콘솔에서 `rc-service acpid status` · `lsmod | grep -w button` · `cat /proc/acpi/wakeup` · `dmesg | grep -i acpi.*button` · `/etc/acpi/handler.sh button/power`(수동 poweroff 확인)로 갈라야 한다. Windows 설치 뒤 같은 조작을 다시 해 BMC 경로 자체는 살아 있는지 분리한다.
- **O-3(#2 · #3 · 10:40 · 10:43 · HF15-5 본안 ① 의 전제 검증)** — 두 서버 모두 ①(lsblk 순서)과 ②(계열 순서 규칙)의 답이 **서로 다르다**. 상2: lsblk = [sda HDD 14.6T, sde SSD 222.6G] 이라 ① 이면 SSD = Disk 1, ② 는 id 내림차순 [323 V1 SSD, 322 V2 HDD] 로 Disk 0. 상3: lsblk = [sda SSD 222.6G, sde HDD 14.6T] 이라 ① 이면 Disk 0, ② 는 [323 V1 HDD, 322 V2 SSD] 로 Disk 1. 실기에서는 wwn 이 비어(F-3) ② 만 돌았으므로 결과가 갈리지 않았지만, **완료 보고의 `installedDiskUniqueId` 가 ② 의 기대와 일치하면 "lsblk 순서 = Windows 디스크 번호" 라는 ① 의 전제가 MPT_IR 에서 틀렸다는 뜻**이 된다. 그 경우 HF15-5-2(sysfs · vpd_pg83 로 wwn 을 채우는 후속)는 ① 을 살리는 방향이 아니라 **식별자 매칭만 취하고 번호는 ② 로** 가야 한다(① 이 채워지는 순간 잘못된 번호를 확신(CONFIDENT)으로 서빙하게 됨). 판정은 §7 에. **첫 표본(상2 · 10:57)**: ② 의 Disk 0 이 실제 설치 디스크(NAA 일치)였고 ① 은 Disk 1 을 냈을 것이므로 ① 의 전제는 이 서버에서 틀렸다. 상3(② = 1 · ① = 0)이 두 번째 표본. **판정(10:58 · 2/2)**: 상3 도 ② 의 Disk 1 이 실제 설치 디스크였다. 두 서버의 lsblk 순서를 보면 Linux(mpt3sas)는 볼륨 id 오름차순(322 → sda · 323 → sde)으로, Windows 는 내림차순(323 → Disk 0)으로 열거한다 — 즉 MPT_IR 에서 ① 의 전제(lsblk 순서 = Windows 번호)는 우연이 아니라 **체계적으로 반대**다. 결론: HF15-5-2 는 wwn 을 채우더라도 **MPT_IR 의 번호는 ② 로 고정**하고 lsblk 재료는 식별 확인(어느 장치가 OS 볼륨인지)에만 쓴다. `WindowsDiskSelection.judge` 의 ① 우선 순서를 계열별로 갈라야 한다(MEGARAID 는 나열 순 = lsblk 순 = Windows 순으로 세 순서가 일치해 어느 쪽이든 같다 — 웨이브 VD0 → sda → Disk 0).
- **O-1(#1 · 10:12:36 · 표기)** — 조정자 로그 `PXE 보장 해제 : PXE 보장 해제 : 반영 확인 · PXE 보장 해제 반영.` 처럼 라벨이 두 번 찍힌다. `RedfishPowerService.armBootOverride` 의 메시지가 `prefix(label)`(라벨 포함) 뒤에 다시 `label + " 반영."` 을 붙인 탓 — 동작 무관 · 문구 정리 대상.

## 7. 마감 상태

**본 흐름 종료 11:06:24** — 세 대 전부 종단(개시 → 종단 38분 · 39분 · 47분). **실기 4호 종료 17:44(사용자 통보) · 모니터 종료 · VM 임시 파일 정리.** **16:38~17:43 후속(1대 · 상2 재투입)**: K9 X → 원인 확정(button 미적재 · F-5) + 화면 오류로 F-4(esc 오용) 발견 → 핫픽스 8차 배포(16:57 · ① 폐기 jar + firstboot apkovl + esc agent.sh) → 재부팅에서 **F-4 소거 · F-3 종결(sysfs NAA 실림) · K13 배지 O · K9 O(10초 안 Off) · K8 O(바인딩 추가)** → 개시 → **회귀 완주 25분(Disk 0 · UniqueId 확증)**. 미수행은 K7 하나. 실기 3호에서 재현됐던 결함 중 이번에 판정된 것:

| 판정 | 항목 |
|---|---|
| **O** | K1 진단 부팅(acpid) · K2 설정 재시작 PXE 복귀 3/3 · K3 RAID → OS 전이 PXE 복귀 3/3(수동 0) · K4 판독 오인 0 · K5 세움 · 해제 · 종단 Disabled 3/3 · K6 autobgi 2 VD · K10 SSD 설치 3/3 · K11 UniqueId 확증 3/3 · K12 개시 전 카드 2/2 · K14 재시도 후 네트워크 부팅 2회 |
| **△** | K13 — 종류 · 전송 고정 표기 억제는 O, "RAID 볼륨" 배지는 wwn 이 비어(F-3) 3/3 미표시 |
| **미수행** | K7 — RAID 실패가 나지 않아 재시도 재채집 경로 미관찰(인위 재현은 사용자 판단) |
| **O(후속)** | K8 — 17:16:05 원래 포트 재부팅으로 바인딩 추가 확인 · **회귀 회차(17:17~17:42)** K2 · K3 · K5 · K10 · K11 · K13 재확인(wwn 동봉 상태에서 ② Disk 0 확증) |
| **X → O** | K9 — 1차(16:43:57) 진단 리눅스 무응답(F-5) → 8차 배포 뒤 17:12:55 정상 종료 10초 안 Off |

**적립(후속 단계 입력)**
1. **HF15-5-2 재정의(O-3 · F-3 · F-4)** — ⓐ `agent.sh` `esc` 오용 정정(F-4 · 한 줄) ⓑ MPT_IR 은 Windows 번호를 계열 순서 규칙(②)으로 고정하고 lsblk 재료는 식별 확인 · 배지(K13)에만 쓴다(① 의 전제가 IR 에서 체계적으로 반대) — ⓐ 와 ⓑ 는 반드시 한 묶음(ⓐ 만 내면 ① 이 살아나 IR 오설치 재발). sysfs wwid 실측은 ⓐ 뒤 자동으로 드러난다.
2. **F-2(4호)** — BMC 세팅의 GET 오류(COLD_REDUNDANT 1334 · 일시)를 WRITE_REJECTED 로 즉시 실패시키지 말고 재시도로 흡수.
3. **O-1** — 조정자 로그 라벨 중복 표기 정리.
4. **O-2** — iPXE 대기 체인 `/boot` 폴링이 `last_boot_at` 을 올리는 잔존 경합 — BIOS pending 해소 조건 추가 검토.
5. 웨이브 BMC 주소가 192.168.1.1 로 적재돼 있다 — 라우터 주소와 겹치지 않는지 사용자 확인(Redfish 응답은 정상).

## 8. 사후 발견 (2026-09-16 · 사용자 보고 · HF17 로 정정)

- **O-4(09-16 · K5 의 사각)** — 프로비저닝을 지난 서버의 BIOS 셋업에서 부트 순서 1번이 네트워크로 고정되고 디스크가 4번으로 내려가 있다(`disk → cd → usb → network → shell` 의 1번과 4번이 교체). 셋업에서 바꿔 저장 · 재부팅해도 되돌아가지 않고 CMOS 클리어 점퍼만 복구한다. K5 는 Redfish `BootSourceOverrideEnabled=Disabled` 만 확인해 이것을 놓쳤다. 원인 = HF15-1 의 `Continuous · Pxe` 를 AMI BIOS 가 persistent device 로 기억해 매 POST 덧씌우는 것 — 실측 5건(`bootparam get 5` = 비어 있음 · `Once · Hdd` 는 그 POST 만 · `Continuous · None` 은 무시 · `Continuous · Hdd` 는 기억을 Hdd 로 옮김 · 셋업 편집 여전히 무효)으로 확정. 정정 = **HF17**(Continuous 전면 폐기 · Once 회귀 · REBOOT 지시 직전 무장 · 미도착 재무장) — `plan/26-09-16_15-39-11_HF17_plan.html`. 이 회차의 세 대(상2 · 상3 · 웨이브)와 후속 1대는 전부 영향권이다.

