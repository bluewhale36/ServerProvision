# 실기 3호 — 서로 다른 하드웨어 3종 · 총 5대 동시 프로비저닝 관찰 기록

> **작성**: 2026-09-08 KST, 앵커 세션(실시간 기록). **목적**: RAID 카드와 디스크 스펙이 서로 다른 세 종류의 서버 다섯 대를 **동시에** 프로비저닝할 때 실행 엔진이 어떻게 움직이는지 경과를 자세히 파악하고 기록한다. 판정 항목표가 있는 회차(1호 · 2호)와 달리 이번은 **관찰 회차**다 — 계획된 A/B/C 식별자 대신 시간순 타임라인과 게스트별 대장으로 남기고, 발견은 §4 에 모은다.

## 1. 환경

| 항목 | 값 |
|---|---|
| 서버 앱 | VM `spvadmin@192.168.1.10` · **5차 재배포(09-08)** = `feat/E4-1-a-6_disk-selection` 작업 트리(dev 3448d57 + E4-1-a-6 미커밋) · jar 지문 b558525c… |
| 시계 | 배포 직후 VM 이 맥보다 9시간 앞서 있었음 → **16:58:16 KST 맥 시각으로 동기화**(`date -s` · hwclock 저장). 이후 원장 · 로그 시각은 맥과 같다 |
| DDL · env | 변경 0 · `/etc/serverprovision/env` 25 키 그대로 |
| `$OEM$` | 대시보드 chip "갱신 필요 — 설치 후 스크립트 변경"(E4-1-a-6 이 첫 로그온 스크립트를 바꿈) — **Windows 설치 게스트가 있으면 회차 전 [조립] 재실행 필요** |
| 기준선(16:58) | 활성 게스트 3(08-27 · 09-01 옛 게스트 · BMC_SETTING · RAID_VERIFYING 에서 멈춘 채) · 정의서 5(1 펌웨어 · 7 표준 9361-8i · 8 표준 사이버다임 · 9 표준 CRA3338 · 10 실기 2호 Windows Standard) · RAID 카드 자원 2 |

### 1-1. 드러난 하드웨어 종류(진단 실측 · 17:24 기준)

| 종류 | 대수 | 보드 · CPU · DIMM | RAID 카드 | 디스크 |
|---|---|---|---|---|
| B | 3(#1' · #2 · #3) | MS04-CE0 · Xeon 6517P · 2 | MPT_IR SAS3008(1458:3008 · CRA3338 계열) · JBOD 노출 | SSD 223.6G ×2 + HDD 14.55T ×2 |
| C | 1(#4 사이버 성우) | MS04-CE0 · Xeon 6505P · 1 | MegaRAID 9361-8i(1000:9361) · 볼륨만 노출 | SSD 446.6G ×2 + HDD 3.64T ×6 |
| C' | 1(#5) | MS04-CE0 · Xeon 6505P · 1 | MegaRAID 9361-8i · fw 4.680.00-8577 | SSD 446.6G ×2 + HDD 10.91T ×6 |

(#1 의 최초 등록분 = 종류 A · 디스크 3 은 회수 뒤 SSD 추가로 종류 B 에 합류)

## 2. 관찰 체계

- **저널 모니터**(앵커 세션 Monitor · 상시): `journalctl -u serverprovision -f` 에서 실행 엔진(`c.e.s.e.*`) · 할당(`c.e.s.p.a.*`) · 개시 POST · 완료 보고 · ERROR/WARN(SSE 시한 만료 · open-in-view 제외)만 골라 실시간으로 받는다. 체크인 · `/boot` 는 요청 로그에서 제외돼 있어 저널엔 안 보인다(RequestCorrelationFilter).
- **DB 폴링 모니터**(60초): 16:00 이후 등록된 활성 게스트의 `이름 | 현재 단계 | 동작 | 상태(미개시·진행·실패·완료) | 마지막 접촉` 을 비교해 바뀐 순간만 받는다 — 로그가 조용한 대기(HOLD · 재부팅 중)도 잡기 위해서.
- 필요할 때 DB 를 직접 읽어 게스트 대장(§3)과 RAID 인벤토리 · 원장을 채운다.

## 3. 게스트 대장 (등록되는 대로 채움)

| 이름 | guest id(앞 8) | 보드 · 하드웨어 종류 | RAID 카드(감지) | 디스크(진단) | 정의서 | 등록 | 개시 | 최종 |
|---|---|---|---|---|---|---|---|---|
| (미명명 · MAC …1a:a1 · **17:14:16 회수**) | 01a0800b-9379 | **종류 A** GIGABYTE MS04-CE0 · Xeon 6517P · DIMM 2 · IP .139 | **MPT_IR SAS3008(1458:3008 · CRA3338 계열) fw 15.00.00.00** · 볼륨 0 | SSD 223.6G(SAMSUNG MZ7L3240) ×1 + HDD 14.55T(WDC WUH722016CL) ×2 · lsblk sda=HDD · sdb=SSD · sdc=HDD | (할당 대기) | 17:03:57 | | 진단 완주 17:04:42 · BMC 계정 표준화 17:04:45 |
| **일산 상3**(MAC …c4:e8 · 그룹 1) | 01a0800b-dda3 | **종류 B** GIGABYTE MS04-CE0 · Xeon 6517P · DIMM 2 · IP .190 | MPT_IR SAS3008(1458:3008) fw 15.00.00.00 · 볼륨 0 | SSD 223.6G ×2 + HDD 14.55T ×2 · lsblk sda·sdb=SSD · sdc·sdd=HDD | 9(할당 23 · 17:25:07) | 17:04:16 | 17:39:18 | 진단 완주 17:05:02 · BMC 표준화 17:05:05 |
| **일산 상1**(MAC …c4:cd · 그룹 1) | 01a0800c-405a | **종류 B** GIGABYTE MS04-CE0 · Xeon 6517P · DIMM 2 · IP .184 | MPT_IR SAS3008(1458:3008) fw 15.00.00.00 · 볼륨 0 | SSD 223.6G ×2 + HDD 14.55T ×2 · **lsblk sda=HDD · sdb·sdc=SSD · sdd=HDD**(#2 와 다름) | 9(할당 22 · 17:25:07) | 17:04:41 | 17:39:11 | 진단 완주 17:05:41 · BMC 표준화 17:05:44 |
| **사이버 성우**(MAC …1a:9e · Run 3 장비 재투입) | 01a08012-e1bc | **종류 C** GIGABYTE MS04-CE0 · Xeon 6505P · DIMM 1 · IP .175 · systemUUID …961a9d(실기 2호 장비) | **MegaRAID 9361-8i(1000:9361)** · 볼륨 0 · 물리 8 전부 UGood(Run 3 의 RAID1·RAID5 를 사용자가 지움) | SSD 446.6G ×2 + HDD 3.64T ×6 · **lsblk 빈 목록**(볼륨 없어 OS 에 블록 장치 0) | 8(할당 24 · 17:25:31) | 17:11:56 | 17:25:33 | 진단 완주 17:12:48 · U6 재시도 경로 |
| **일산 상2**(MAC …1a:a1 · #1 재투입 · 그룹 1) | 01a08016-ebfa | **종류 B 로 합류** GIGABYTE MS04-CE0 · Xeon 6517P · IP .139(같은 systemUUID …961aa1) | MPT_IR SAS3008(1458:3008) · 볼륨 0 | **슬롯 1:2 에 SSD 223.6G 추가**(3 → 4: 1:0 SSD · 1:1 HDD · 1:2 SSD · 1:3 HDD) · lsblk 14.6T · 223.6G · 223.6G · 14.6T | 9(할당 21 · 17:25:07) | 17:16:20 | 17:38:53 | 진단 완주 17:17:05 · 회수 후 재시도 경로 |
| **사이버 웨이브**(MAC …1a:7d) | 01a0801d-a96d | **종류 C'** GIGABYTE MS04-CE0 · Xeon 6505P · DIMM 1 · IP .131 | MegaRAID 9361-8i(1000:9361) fw 4.680.00-8577 · 볼륨 0 · 물리 8 전부 UGood | SSD 446.6G ×2 + **HDD 10.91T ×6**(#4 는 3.64T) · lsblk 빈 목록 | 7(할당 25 · 17:25:42) | 17:23:42 | 17:25:43 | 진단 완주 17:24:22 |

## 4. 타임라인 (KST · 저널 · DB 실측)

| 시각 | 게스트 | 사건 | 비고 |
|---|---|---|---|
| 16:58:16 | — | VM 시계 동기화 · 모니터 2종 가동 | 기준선 활성 게스트 3(옛) |
| 17:00:10 | — | `$OEM$` 재조립(사용자 · 대시보드 [조립]) — 드라이버 2종 · 제외 0 · 18.6 MB | E4-1-a-6 첫 로그온 스크립트 반영 → 디스크 확증 보고 가능 |
| 17:03:57 | #1 MS04-CE0(MAC …1a:a1) | PXE 부팅 → 신규 등록(systemUUID daa5c000-…-30560f961aa1 · IP .139) | 보드 모델 정규화 MS04-CE0-000 → MS04-CE0 · Run 3 장비(…1a9d)와 다른 개체 |
| 17:04:16 | #2 MS04-CE0(MAC …c4:e8) | PXE 부팅 → 신규 등록(systemUUID …30560f9ac4e8 · IP .190) | 같은 보드 모델 두 번째 개체 · 19초 간격 |
| 17:04:41 | #3 MS04-CE0(MAC …c4:cd) | PXE 부팅 → 신규 등록(systemUUID …30560f9ac4cc · IP .184) | 세 번째 개체 · UUID 끝자리가 MAC 과 1 차이(base MAC 파생) |
| 17:04:42 | #1 | 진단 완주(미개시 · 수집 완료 대기) · RAID 인벤토리 적재: card 1458:3008(MPT_IR SAS3008 · CRA3338 계열) · 물리 디스크 3 · 볼륨 0 | 등록 45초 만에 진단 끝 · JBOD 상태(볼륨 없음) |
| 17:04:45 | #1 | BMC 계정 표준화 완료(E1.6 · 비동기) | |
| 17:05:02 | #2 | 진단 완주 · RAID 인벤토리: card 1458:3008 · 디스크 4 · 볼륨 0 | 등록 46초 |
| 17:05:05 | #2 | BMC 계정 표준화 완료 | |
| 17:05:41 | #3 | 진단 완주 · RAID 인벤토리: card 1458:3008 · 디스크 4 · 볼륨 0 | 등록 60초 |
| 17:05:44 | #3 | BMC 계정 표준화 완료 | 3대 모두 등록 → 진단 완주 1분 안팎 · 미개시 대기(R13) |
| 17:11:56 | #4 MS04-CE0(MAC …1a:9e) | PXE 부팅 → **회수 후 재시도** 경로로 신규 등록(systemUUID …961a9d · IP .175) | 실기 2호 Run 3 장비(21:59 회수) 재투입 · U6 경로 정상 |
| 17:12:48 | #4 | 진단 완주 · RAID 인벤토리: card 1000:9361(MegaRAID 9361-8i) · 물리 디스크 8 전부 UGood · 볼륨 0 | 등록 52초 · Run 3 볼륨(RAID1 · RAID5)이 지워진 상태 = RAID 구성 phase 가 처음부터 만들 조건 |
| (동기화 전 · 약 16:5x) | — | 정의서 1(펌웨어) · 10(Windows Standard) 비활성 + 사용 중단 권고(사용자) | 저널 시각은 옛 시계(01:55). **정정**: 표준 정의서 7 · 8 · 9 가 각각 펌웨어 갱신 → BIOS 설정 → RAID 구성 → Windows Server 2025 설치를 다 품고 있어, 이번 회차는 다섯 대 모두 Windows 까지 간다(E4-1-a-6 실기) |
| 17:13:28 | — | 서버 그룹 1 "한국동서 일산본부" 생성(사용자 · U3-4/5 그룹) | 표준 정의서 미지정 상태로 생성 |
| 17:13:38~55 | #1 · #4 | 그룹 1 구성원 추가 → #1 제거 → 추가 → #4 제거(사용자 조작 4회) | **최종 구성원 = #2 · #3(종류 B)** · 정의서 상태: 7 · 8 · 9 활성, 1 · 10 사용 중단 |
| 17:14:16 | #1 | **회수(decommission)**(사용자) | 종류 A(디스크 3) 장비를 이번 회차에서 뺀 것으로 보임 — 사유는 사용자 확인 필요 |
| 17:16:20 | #1' MS04-CE0(MAC …1a:a1) | PXE 부팅 → 회수 후 재시도 경로로 **재등록**(같은 systemUUID …961aa1) | 회수는 제외가 아니라 재투입 준비였음 — 새 게스트 행으로 다시 진단 |
| 17:17:05 | #1' | 진단 완주 · RAID 인벤토리: 디스크 **4**(슬롯 1:2 SSD 신규) · 볼륨 0 | 회수 사이에 SSD 1개를 꽂아 #2 · #3 과 같은 구성(SSD ×2 + HDD ×2)으로 맞춤 → 종류 B 3대 |
| ~17:23 | #4 | 이름 지정 "사이버 성우"(사용자) | 정의서 8 "표준 세팅 (사이버다임)" 과 같은 고객 계열로 읽힘 |
| 17:23:42 | #5 MS04-CE0(MAC …1a:7d) | PXE 부팅 → 신규 등록(systemUUID …961a7d · IP .131) | **다섯 대 등록 완료**(#1 은 재투입분 기준) |
| 17:24:22 | #5 | 진단 완주 · RAID 인벤토리: 9361-8i · 물리 8 UGood(SSD 446G ×2 + HDD 10.9T ×6) · 볼륨 0 | #4 와 카드 같고 HDD 용량이 다름(3.64T ↔ 10.9T) |
| ~17:24 | #2 · #3 | 이름 지정 "일산 상3"(#2) · "일산 상1"(#3)(사용자) | 그룹 "한국동서 일산본부" 구성원 |
| 17:25:07 | #1' · #2 · #3 | **그룹 일괄 할당**(U3-5) — 정의서 9 "표준 세팅 (Raid: CRA3338)" → 할당 21(#1') · 22(#3) · 23(#2) · assigned 3 · skipped 0 | 보유 phase = FIRMWARE_UPDATING · FIRMWARE_SETTING · RAID_CONFIGURATION · OS_INSTALLING(Windows) · RAID 규칙 = 카드 1(CRA3338) · RAID1 AUTO… |
| ~17:25 | #1' · #5 | 이름 지정 "일산 상2"(#1') · "사이버 웨이브"(#5)(사용자) | 다섯 대 전부 명명 완료 |
| 17:25:31 | #4 사이버 성우 | 할당 24 — 정의서 8 "표준 세팅 (사이버다임)"(카드 2 = 9361-8i · RAID1 SSD SATA …) | 개별 할당 |
| 17:25:33 | #4 | **개시**(POST /start 200) | 첫 개시 |
| 17:25:42 | #5 사이버 웨이브 | 할당 25 — 정의서 7 "표준 세팅 (Raid: 9361-8i)" | 개별 할당 |
| 17:25:43 | #5 | **개시** | |
| 17:25:58 | #4 | 펌웨어 갱신 **집행 착수 · 전원 차단**(BeginFlashStep · Redfish) | 개시 25초 뒤 스케줄러가 집행 시작 |
| 17:26:00 | #5 | 펌웨어 갱신 집행 착수 · 전원 차단 | 두 대 동시 flash 진행(E0-4 실측 기준 1대 약 7분 37초) |
| 17:26:30 | #4 | BIOS 이미지 토큰 발급(image.RBU) → 17:26:31 **BIOS 굽기 시작 : F29**(FlashAxisStep · Redfish SimpleUpdate) | 전원 차단 32초 뒤 굽기 착수 |
| 17:26:32 | #5 | BIOS 이미지 토큰 발급 → **BIOS 굽기 시작 : F29** | 두 대가 1초 간격으로 같은 스케줄러 틱에서 굽기 시작 · DB: 사이버 성우 · 사이버 웨이브 = BIOS_UPDATING · STEP_RUNNING · 진행 |
| 17:28:04 | #4 | BIOS 이미지 토큰 회수 | 굽기 시작 1분 33초 뒤 — 원장 BIOS_UPDATING SUCCEEDED · meta "전송 완료" · F29_MS04-CE0_Uni · TaskMonitors/12 — BIOS 축은 전송 완료(적용은 다음 전원 인가 · VerifyFlashStep 이 확인) |
| 17:28:35 | #4 | BMC 이미지 토큰 발급(rom_v130629.ima_enc) → 17:28:36 **BMC 굽기 시작 : 13.06.29**(firmwareId 9 · TaskMonitors/13) | BIOS 축 → BMC 축 순차 · E0-4 실측 .ima_enc 7분 37초 |
| 17:29:38 | #5 | BIOS 이미지 토큰 회수(BIOS 축 전송 완료) | 굽기 시작 3분 6초 — #4(1분 33초)의 두 배. 같은 이미지 · 같은 카드인데 BMC 전송 속도 차이(O-5 후보 · 두 대 동시 전송의 영향?) |
| 17:30:09 | #5 | BMC 이미지 토큰 발급(rom_v130629.ima_enc) → BMC 굽기 시작 13.06.29 | #4 보다 1분 33초 늦게 BMC 축 진입 |
| 17:31:48 | #4 | BMC 축 **전송 완료**(원장 BMC_UPDATING SUCCEEDED · 13.06.29 · TaskMonitors/13) · 토큰 회수 | 3분 12초. BMC(192.168.1.182)가 **Task 번호 어긋남**(monitor 13 ↔ Tasks/12)을 보여 컬렉션 최신 Task 로 판정하는 관용 경로가 탔다(E2-2 실측 파생 로직 실제 발동 · O-5). 커서는 BMC_UPDATING · STEP_RUNNING 유지 → 전원 인가 · 검증 단계로 |
| 17:32:26 | #4 | **굽기 완료 · 전원 투입**(PowerOnStep · Reset(On) → PowerState 폴링 확인) · 다음 부팅 PXE 강제(E2.5 network boot override) 반영 확인 | 전송 완료 39초 뒤 켜짐 — 전원 사이클 한 번으로 BIOS·BMC 둘 다 적용(E2-2 설계) |
| 17:36:33 | #4 | 재부팅 → PXE 부팅 요청(이미 등록된 서버 · 등록 생략) → 17:36:34 **VerifyFlashStep 반영 확인 완료** | 펌웨어 갱신 phase 완주 = 개시 17:25:33 → 검증 17:36:34 = **11분 1초**(BIOS F29 · BMC 13.06.29) |
| 17:36:41 | #4 | **BIOS 설정 phase 착수** — 템플릿 속성 5개 PATCH(pending 확인) · FORCE_RESTART 발행 · 다음 부팅 PXE 강제 | 펌웨어 → 설정 phase 전이 7초 |
| 17:2x~ | 일산 3대 | **사용자 지적**: 상세 카드의 Windows 설치 준비도가 "OS 영역 RAID 볼륨이 없습니다" 로 BLOCKED — 그러나 RAID 구성 계획 미리보기는 이미 spvR1V1(1:0 · 1:1 · 240 GB) = OS 영역 · spvR1V2 = Data 영역을 지정 | **F-1 후보(E4-1-a-6 설계 공백)** — §5 참조 |
| 17:38:53 | #1' 일산 상2 | **개시**(POST /start 200) — 준비도 BLOCKED 표시와 무관하게 개시 성립(가드 = 진행 상태 · 할당만) | F-1 의 "개시는 막히지 않는다" 실증 |
| 17:39:11 | #3 일산 상1 | 개시(200) | |
| 17:39:15 | #3 일산 상1 | 펌웨어 갱신 집행 착수 · 전원 차단 | 개시 4초 뒤 |
| 17:39:17 | #1' 일산 상2 | 펌웨어 갱신 집행 착수 · 전원 차단 | |
| 17:39:18 | #2 일산 상3 | 개시(200) | **다섯 대 전부 개시** — 4호는 BIOS 설정 · 5호는 BMC 굽기 · 일산 3대는 펌웨어 갱신 착수 = 동시 진행 5 |
| 17:39:50 | #2 일산 상3 · #3 일산 상1 | #2 펌웨어 집행 착수 · 전원 차단 / #3 BIOS 이미지 토큰 발급(image.RBU) | 같은 스케줄러 틱에 두 게스트 처리 — 직렬 틱이 다중 게스트를 순서대로 밟는다 |
| 17:39:51 | #3 일산 상1 | BIOS 굽기 시작 : F29 | |
| 17:39:51 | #1' 일산 상2 | BIOS 이미지 토큰 발급 → 17:39:52 BIOS 굽기 시작 : F29 | 일산 두 대가 1초 간격 |
| 17:39:52 | #5 사이버 웨이브 | BMC 축 전송 완료 · 토큰 회수 — BMC(192.168.1.1)도 Task 번호 어긋남(monitor 3 ↔ Tasks/2) → 관용 판정 | BMC 축 9분 42초(17:30:10 → 17:39:52) · #4(3분 12초)의 3배 — 세 대 동시 전송 영향 추정(O-5 · O-6 후보) |
| 17:40:23 | #2 일산 상3 | BIOS 이미지 토큰 발급 → BIOS 굽기 시작 : F29 | 일산 3대 전부 BIOS 축 진행 |
| 17:40:32 | #5 사이버 웨이브 | 굽기 완료 · 전원 투입 · 다음 부팅 PXE 강제 | 펌웨어 phase 14분 49초 째 |
| ~17:41 | #4 사이버 성우 | **사용자 보고: network boot 실패 → UEFI 셸로 떨어짐.** 17:36:40 ForceRestart(PXE 강제) 뒤 PXE 부팅 요청이 4분 넘게 없음 · 앱은 BIOS_SETTING · AWAITING_BOOT | **F-2** — §5 참조. 조치 = 셸에서 exit → 부트 매니저 → UEFI PXE, 또는 전원 제어의 네트워크 부팅 재시작 |
| 17:41:31 | #4 | PXE 부팅 요청 도착(사용자가 셸에서 수동 네트워크 부팅) → 17:41:38 **ReturnReadbackStep: BIOS 설정 5개 반영 확인, 다음 축으로** | F-2 우회 성립 — 앱은 대기 시한 안에 돌아온 부팅을 정상 소비 |
| 17:42:02 | #4 | PXE 부팅 요청(재부팅 1회 더) → 17:42:11 **BMC 표준 세팅 4개 적용 · 축 종결**(BeginBmcSettingStep) | 펌웨어 설정 phase 완주 = 17:36:35 → 17:42:11(5분 36초 · 우회 대기 포함) → 다음 = RAID 구성 |
| 17:42:32 | #4 | PXE 부팅 요청 → 커서 **RAID_INVENTORY_COLLECTING · AWAITING_BOOT**(RAID 구성 phase 진입) | 설정 종결 21초 뒤 재PXE — 진단 리눅스가 RAID 인벤토리 재채집 예정 |
| 17:42:39 | #3 일산 상1 · #1' 일산 상2 | BIOS 이미지 토큰 회수(BIOS 축 전송 완료) | 굽기 시작 2분 48초 · 2분 48초 — 세 대 동시라도 #5(3분 6초)와 비슷 |
| 17:43:11 | #3 일산 상1 · #1' 일산 상2 | BMC 이미지 토큰 발급 → 17:43:12 · 17:43:13 **BMC 굽기 시작 13.06.29**(두 대 1초 간격) | 일산 상3 · 사이버 웨이브 와 함께 BMC 축 4대 동시 |
| 17:43:22 | #4 사이버 성우 | RAID 집행 계획 확정(PENDING · PLANNED): spvR1V1 RAID1 252:0·252:1 446.6G **OS** · spvR2V1 RAID5 252:2~7 18.19T DATA · 규칙 2 소비 8/8 | 진단 재채집 뒤 계획 · 개시 후 17분 49초 |
| 17:43:24 | #4 사이버 성우 | **RAID_APPLYING FAILED · CREATE_REJECTED** — `add vd … spvR1V1` 은 **Success** 였으나 다음 `storcli64 /c0/v0 set bgi=off` 가 `syntax error, unexpected TOKEN_BGI` · 진행 = 실패 | **F-3** — §5. 카드에 spvR1V1 만 만들어진 부분 상태 |
| 17:43:44 | #2 일산 상3 | BIOS 이미지 토큰 회수 → 17:44:17 BMC 토큰 발급 → 17:44:18 BMC 굽기 시작 13.06.29 | 일산 3대 전부 BMC 축 |
| 17:43:54~ | #4 | 30초마다 agent/steps POST 409(AgentReportRejectedException) | 실패한 게스트의 에이전트가 계속 문을 두드림 — 무해(O-6) |
| 17:44:18 | #5 사이버 웨이브 | PXE 부팅 → 17:44:19 VerifyFlash 반영 확인 완료(펌웨어 phase 18분 36초) → 17:44:25 **BIOS 설정 5개 PATCH · ForceRestart · PXE 강제** | **F-2 재현 위험 구간** — KVM 주시 |
| 17:48:29 | #5 사이버 웨이브 | PXE 부팅 요청 도착(설정 재시작 4분 4초 뒤) | F-2 재현 여부 미확인 — 사용자 수동 개입 없이 왔다면 override 가 이번엔 성립(POST 4분은 8디스크 MegaRAID 서버의 평상 부팅 시간과 비슷) |
| 17:48:59 | #5 | PXE 부팅 요청(30초 뒤 한 번 더) → **BIOS 설정 5개 반영 확인, 다음 축으로**(ReturnReadbackStep) | #4 와 같은 순서(첫 PXE 는 도착 확인 · 다음 PXE 에서 판독) |
| 17:49:31 | #5 사이버 웨이브 | BMC 표준 세팅 4개 적용 · 축 종결 → 커서 RAID_INVENTORY_COLLECTING · AWAITING_BOOT | 설정 phase 5분 6초(17:44:25 → 17:49:31) · 이제 RAID 단계 → **F-3 재현 예상** |
| 17:50:35 | #5 사이버 웨이브 | **RAID_APPLYING FAILED — F-3 재현**(같은 `set bgi=off` 구문 오류 · spvR1V1 생성 뒤) | 9361-8i 두 대 모두 RAID 단계에서 정지 · 정정 후 [재시도] 대상 |
| 17:52:18 | #1' 일산 상2 | BMC 축 전송 완료 · 토큰 회수(BMC 192.168.1.111 도 Task 번호 어긋남 → 관용 판정) | BMC 축 9분 5초 · O-5 세 번째 재현(세 BMC 모두) |
| 17:52:59 | #1' 일산 상2 | 굽기 완료 · 전원 투입 · 다음 부팅 PXE 강제 | 일산 첫 번째 전원 투입 |
| 17:53:30 | #2 일산 상3 · #3 일산 상1 | BMC 축 전송 완료 · 토큰 회수(BMC .144 · .142 도 Task 번호 어긋남 → 관용 판정) | BMC 축 9분 12초 · 10분 18초 · O-5 다섯 BMC 전부 재현 |
| ~17:54 | — | **F-3 정정 준비 완료(앵커)** — `VdBackgroundInit` 토큰 `bgi=` → `autobgi=` · 테스트 4 파일 보정 · RAID 계열 154 테스트 green · jar 빌드(재배포는 사용자 지시 대기) | 사용자 결정: 준비만 · 재배포는 뒤에 |
| 17:54:09 | #2 일산 상3 | 굽기 완료 · 전원 투입 · PXE 강제 | |
| 17:54:17 | #3 일산 상1 | 굽기 완료 · 전원 투입 · PXE 강제 | 일산 3대 전부 전원 투입 — 곧 PXE 검증 → BIOS 설정 재시작(F-2 주시 ×3) |
| 17:57:02 | #1' 일산 상2 | PXE 부팅 요청(전원 투입 4분 3초 뒤) → 17:57:29 **VerifyFlash 반영 확인 완료** | 펌웨어 phase 18분 36초(17:38:53 → 17:57:29) — 세 대 동시라 #4 단독(11분 1초)보다 7분 반 더 걸림 |
| 17:57:35 | #1' 일산 상2 | **BIOS 설정 5개 PATCH · ForceRestart · PXE 강제** | **F-2 재현 위험 구간(일산 첫 번째)** — 이 서버는 볼륨 0 JBOD 라 부팅 디스크 없음 |
| 17:58:05 | #1' 일산 상2 | **BIOS_SETTING FAILED · READBACK_MISMATCH**(5개 전부 미반영) — ForceRestart 30초 뒤 판독 | **F-4** — §5. Redfish Reset Task(TaskMonitors/4) state=null · 서버는 아직 재시작 전 · iPXE 대기 폴링(30초)이 "복귀 부팅" 으로 읽힘 |
| 17:58:28 | #2 일산 상3 | PXE(전원 투입 4분 19초 뒤) → 17:58:32 VerifyFlash 반영 확인 완료 → 17:58:41 BIOS 설정 PATCH · ForceRestart · PXE 강제 | 펌웨어 phase 19분 14초 · **F-4 재현 위험** |
| 17:58:40 | #3 일산 상1 | PXE 부팅 요청(IP 가 .184 → .170 으로 바뀜 · DHCP 재임대) | PXE 요청의 MAC 이 등록 때(…c4:cd)와 달리 …c4:cc(다른 포트) — systemUUID 로 같은 서버 매칭(O-7) |
| 17:59:04 | #3 일산 상1 | VerifyFlash 반영 확인 완료 → 17:59:16 BIOS 설정 PATCH · ForceRestart · PXE 강제 | 펌웨어 phase 19분 53초 · **F-4 재현 위험** |
| 18:01:04 | #1' 일산 상2 | **[재시도]**(POST /retry 200 · 사용자) | F-4 실패 뒤 재PATCH · 재리셋 — BMC 안정 뒤 즉시 리셋 여부 관찰 |
| 18:01:22 | #1' 일산 상2 | 재시도의 BeginSettingStep — 5개 PATCH(**pending 미확인**) · ForceRestart · PXE 강제 | 앞선 리셋이 결국 실행돼 설정이 이미 적용된 상태로 추정(pending 없음) |
| 18:02:59 | #2 일산 상3 | PXE 부팅 요청(설정 재시작 4분 18초 뒤) → 18:03:23 **BIOS 설정 5개 반영 확인, 다음 축으로** | F-4 · F-2 모두 피함 — 리셋이 폴링보다 먼저 먹힌 경우 |
| 18:03:34 | #1' 일산 상2 | PXE 부팅 요청(재시도 리셋 2분 12초 뒤) | 판독 대기 |
| 18:03:38 | #3 일산 상1 | PXE 부팅 요청(설정 재시작 4분 22초 뒤 · NIC …c4:cc) | 판독 대기 |
| 18:03:55 | #2 일산 상3 | BMC 표준 세팅 4개 적용 · 축 종결 → RAID 단계 | 설정 phase 5분 14초 · **일산 첫 RAID 진입(sas3ircu 경로 · F-3 무관)** |
| 18:03:56 | #3 일산 상1 · #1' 일산 상2 | **BIOS 설정 5개 반영 확인, 다음 축으로**(두 대 1초 간격) | 상2 는 재시도로 F-4 극복 · 상1 은 첫 시도에 통과 → 일산 3대 BIOS 설정 전부 반영 |
| 18:04:28 | #3 일산 상1 | BMC 표준 세팅 4개 적용 · 축 종결 → RAID 단계 | 설정 phase 5분 12초 |
| 18:04:30 | #1' 일산 상2 | BMC 표준 세팅 4개 적용 · 축 종결 → RAID 단계 | 설정 phase 7분 1초(재시도 포함) · **일산 3대 전부 RAID_INVENTORY_COLLECTING · AWAITING_BOOT** |
| 18:04:48 | #2 일산 상3 | **RAID 집행 검증 통과 · raid_volume 2건 · 커서 전진 → OS_INSTALLING · AWAITING_BOOT** | RAID phase 53초(sas3ircu · 초기화 없음). 저장 = spvR1V1 RAID1 **DATA** 16 TB(1:0 · 1:1 HDD · wwn 00a82098…) / spvR1V2 RAID1 **OS** 240 GB(1:2 · 1:3 SSD · wwn 06923d86…) — 우선순위가 SSD 볼륨을 OS 로 골랐다(슬롯 순서와 무관). 재채집 인벤토리 볼륨 순서 = [322 spvR1V2(OS), 323 spvR1V1] → **E4-1-a-6 예상 DiskID 0**(WWN 매칭 · IR 볼륨 wwid 채집됨) |
| 18:05:22 | #1' 일산 상2 | RAID 집행 검증 통과 · raid_volume 2건 · 커서 전진 → OS_INSTALLING | RAID phase 52초 |
| 18:05:27 | #3 일산 상1 | RAID 집행 검증 통과 · raid_volume 2건 · 커서 전진 → OS_INSTALLING | RAID phase 59초 · **일산 3대 전부 Windows 설치 게이트 대기(AWAITING_BOOT)** — 다음 PXE 에서 준비도 · 디스크 선택 · wimboot 서빙 |
| ~18:06 | #2 일산 상3 | **사용자 보고: network boot 실패(UEFI 셸)** — RAID 검증 뒤 에이전트 REBOOT 지시로 재부팅했고 Redfish 리셋 · PXE 강제가 없었다(저널에 override 0) | **F-2b** — §5. 부팅 순서 디스크 우선 + 새 볼륨은 빈 상태 → 셸. 우회 = 셸에서 PXE 선택 |
| 18:06:46 | #5 사이버 웨이브 | Redfish Reset(GracefulShutdown)(사용자 · 전원 제어) | 실패 서버 전원 정리로 보임 |
| 18:08:04 | #2 일산 상3 | PXE 부팅 요청(사용자가 셸에서 PXE 선택) → 설치 번들 토큰 발급 → **wimboot 체인 서빙 = 착수 : Windows Server 2025 SERVERSTANDARD · diskId=0** | **E4-1-a-6 실기 첫 서빙** — OS 볼륨 spvR1V2(SSD RAID1 · wwid 06923d86…)가 인벤토리 index 0 → DiskID 0. 설치 뒤 완료 보고 UniqueId 대조가 판정 |
| ~18:09 | #3 일산 상1 · #1' 일산 상2 | **사용자 보고: 둘 다 network boot 실패 → 수동 PXE 전환** | F-2b 3/3 재현 — RAID 뒤 에이전트 재부팅 경로는 예외 없이 셸로 떨어진다(부팅 디스크 없는 서버) |
| 18:10:07 | #1' 일산 상2 | PXE 부팅 요청(수동 PXE 전환 · IP .139) → 설치 번들 토큰 발급 → **wimboot 체인 서빙 = 착수 · diskId=1** | OS 볼륨 = spvR1V1(SSD 1:0 · 1:2 · wwid 005829e2…)이 인벤토리 index 1 → DiskID 1. 상3(0)과 반대 번호 — §5 O-9 |
| 18:10:16 | #3 일산 상1 | PXE 부팅 요청(수동 PXE 전환 · NIC …c4:cc · IP .170) → 토큰 발급 → **wimboot 체인 서빙 = 착수 · diskId=1** | OS 볼륨 = spvR1V1(SSD 1:0 · 1:1 · wwid 08169c02…) 인벤토리 index 1 → DiskID 1. 일산 3대 전부 설치 착수(18:08:04 · 18:10:07 · 18:10:16) — 종류 B 셋의 DiskID 가 0 · 1 · 1 로 갈렸다 |
| 18:20:24 | — | **6차 재배포(F-3 정정 · 사용자 지시)** — jar 지문 8978b45a…(`autobgi=` 토큰) 를 `.new` 원자 교체 · restart → 18:20:32 기동(7.1초) · `/provisioning/server` 200 · ERROR 0 | 재시작 전 확인: 일산 3대의 wimboot 번들 5개 내려받기는 모두 끝났고(상3 18:08:10 · 상2 18:10:15 · 상1 18:10:22) 완료 보고는 DB 게스트 토큰을 쓰므로 메모리 토큰 레지스트리(`WindowsInstallTokenRegistry` · ConcurrentHashMap) 소실은 진행 중 설치에 영향 없음. 옛 프로세스의 종료 훅 `NoClassDefFoundError: ch/qos/logback/.../ThrowableProxy` 는 실행 중 jar 를 바꿔치기해 생기는 무해한 잡음(5차와 동일) |
| 18:23:51 | #5 사이버 웨이브 | **[재시도]**(POST /retry 200 · 사용자) — F-3 정정 뒤 첫 재시도 | 서버는 18:06:46 에 꺼진 상태 → 네트워크 부팅 전원 투입 뒤 진단 체크인 · RAID 재집행(spvR1V1 잔여 재구성 + `autobgi=off`) 관찰 |
| 18:24:05 | #4 사이버 성우 | **[재시도]**(POST /retry 200 · 사용자) | 9361-8i 두 대 모두 재시도 접수 — 다음 PXE 에서 진단 이미지 서빙 · 체크인 · RAID 재집행 |
| 18:24:42 | #1' 일산 상2 | **설치 완료 보고 · 종단** — SPV-0F961AA1 · 드라이버 47 · 문제 장치 0 · **diskConfirmed=false** · 보고 UniqueId `600508E0000000002D960EF89369F100` | 서빙 → 완료 14분 35초. UniqueId 꼬리 16자를 바이트 뒤집으면 `00f16993f80e962d` = **spvR1V2(DATA · HDD 14.5 TB)의 wwid** → Windows 가 **데이터 볼륨에 설치됐다**(F-6). OS 볼륨 SSD 는 빈 채. 손실 0 |
| 18:25:05 | #3 일산 상1 | **설치 완료 보고 · 종단** — SPV-0F9AC4CC · 드라이버 47 · 문제 장치 0 · **diskConfirmed=false** · UniqueId `600508E000000000B1015F19AFF93F02` | 14분 49초. 꼬리 뒤집기 = `023ff9af195f01b1` = spvR1V2(DATA · HDD) → 같은 결과(F-6 2/2). 상3(DiskID 0)은 spvR1V1 = DATA 라 같은 결과 예상 — 보고 UniqueId 가 `…2E2FF7379820A800`(00a82098… 뒤집기)이면 확정 |
| 18:30:47 | #4 사이버 성우 | PXE 부팅 요청(재시도 뒤 사용자 재부팅 · IP .175) → 진단 이미지 서빙 | 체크인 뒤 RAID 재집행 — spvR1V1 잔여 재구성 · `autobgi=off` 통과 여부가 F-3 정정 판정 |
| 18:30:56 | #5 사이버 웨이브 | PXE 부팅 요청(재시도 뒤 사용자 전원 투입 · IP .131) → 진단 이미지 서빙 | 9361-8i 두 대 동시 RAID 재집행 진입 |
| ~18:31 | #2 일산 상3 | **사용자 보고(콘솔): 잘못된 디스크에 설치 — C: 가 HDD 볼륨** | F-6 3/3 재현. DiskID 0 = Windows Disk 0 = spvR1V1(DATA · HDD) — 예측 그대로. 완료 보고 UniqueId(`…2E2FF7379820A800` 예상)로 원장 확정 대기 |
| 18:31:02 | #2 일산 상3 | **설치 완료 보고 · 종단** — SPV-0F9AC4E8 · 드라이버 47 · 문제 장치 0 · **diskConfirmed=false** · UniqueId `600508E0000000002E2FF7379820A800` | 서빙 → 완료 22분 58초(다른 두 대 14분 반보다 8분 더 — 사유 미상 · 콘솔 조작 여부 확인). 꼬리 뒤집기 = `00a8209837f72f2e` = spvR1V1(DATA · HDD) — **예측값과 정확히 일치 · F-6 3/3 · F-7 형식 규칙 3/3 확정**. 일산 3대 전부 종단(Windows 는 HDD RAID1 에) |
| 18:31:32 | #5 사이버 웨이브 | **RAID_APPLYING FAILED(재시도 1차) — CREATE_REJECTED** at `storcli64 /c0 add vd type=raid1 name=spvR1V1 drives=252:1,252:6 awb ra direct strip=256 pdcache=default` → `disk doesn't have enough capacity`(ErrCd 13) | 계획 meta 는 `setOps:[autobgi=off, accesspolicy=rw]` — **F-3 정정은 실렸다**. 그러나 `deleteExistingFirst=false`: 계획이 17:24:22 의 옛 인벤토리(볼륨 0 · 전부 UGood)로 세워져 1차 시도가 남긴 spvR1V1 을 모른다 → 같은 슬롯에 다시 만들려다 거절(F-8) |
| 18:31:38 | #4 사이버 성우 | **RAID_APPLYING FAILED(재시도 1차) — CREATE_REJECTED** 같은 명령(drives=252:0,252:1 wt) · 같은 사유 | 17:12:48 인벤토리 기준 계획 · F-8 2/2. 두 대 모두 컨트롤러에 spvR1V1(VD0 · SSD RAID1) 잔여 |
| 18:40:51 | #4 사이버 성우 | **[재시도] 2차**(POST /retry 200 · 사용자) — F-8 우회: 사용자가 진단 콘솔에서 `vall show` 로 VD 가 spvR1V1 하나뿐임을 확인하고 `del force` 로 삭제(두 대 모두) 뒤 재시도 · 네트워크 부팅 재진입 | 다음 PXE → 진단 체크인 → 계획(볼륨 0 인벤토리와 실물 일치) → 두 볼륨 생성 · `autobgi=off` 판정 |
| 18:43:40 | #5 사이버 웨이브 | **[재시도] 2차**(POST /retry 200 · 사용자) — 같은 우회(잔여 spvR1V1 삭제 확인 뒤) | 두 대 모두 2차 재시도 접수 |
| 18:44:00 | #4 사이버 성우 | PXE 부팅 요청(네트워크 부팅 재진입 · IP .175) → 진단 이미지 서빙 | 체크인 → 계획(볼륨 0 · 실물 일치) → 집행 |
| 18:44:56 | #4 사이버 성우 | **RAID 집행 검증 통과 · raid_volume 2건 · 커서 전진 → OS_INSTALLING** — 집행 로그: `add vd raid1 spvR1V1` Success → **`/c0/v0 set autobgi=off` Success** → `accesspolicy=rw` Success → `start init force` Success → `add vd raid5 spvR2V1`(pdcache=on) → `/c0/v1 set autobgi=off` Success → … 전부 Success | **F-3 정정 실증**(PXE 56초 만에 phase 완주). 저장 = spvR1V1 RAID1 **OS** 446 GB(252:0 · 252:1 SSD · wwn …0c762e2d) / spvR2V1 RAID5 **DATA** 18.2 TB(HDD 6). 재채집 인벤토리 [VD0 spvR1V1(OS), VD1 spvR2V1] → **E4-1-a-6 예상 DiskID 0** — MegaRAID 는 Run 3 에서 storcli 순서 = Windows 순서가 실증됐으므로 이 서버가 F-6 의 양성 대조군(diskConfirmed=true 기대 · UniqueId = WWN 그대로). 다음 = 에이전트 reboot → F-2b(셸) 예상 → 수동 PXE |
| 18:45:49 | #5 사이버 웨이브 | PXE 부팅 요청(네트워크 부팅 재진입 · IP .131) → 진단 이미지 서빙 | 체크인 → 집행(awb · 252:1 · 252:6 SSD RAID1 + HDD 6 RAID5) |
| 18:46:29 | #5 사이버 웨이브 | **RAID 집행 검증 통과 · raid_volume 2건 · 커서 전진 → OS_INSTALLING** — `add vd raid1 spvR1V1`(awb) · `set autobgi=off` · `accesspolicy=rw` · `start init force` · `add vd raid5 spvR2V1`(pdcache=on) · `set autobgi=off` 전부 Success | F-3 정정 2/2 실증(PXE 40초). 저장 = spvR1V1 RAID1 **OS** 446 GB(252:1 · 252:6 SSD · wwn …08471c90) / spvR2V1 RAID5 **DATA** 54.6 TB(10.9T HDD 6). 재채집 [VD0 spvR1V1(OS), VD1] → DiskID 0 예상. **9361-8i 두 대 모두 Windows 설치 게이트 대기** — 다음 = 에이전트 reboot → F-2b 예상 |
| ~18:48 | #4 사이버 성우 | **사용자 보고: network boot 실패 → 수동 PXE 진입** | **F-2b 4/4 재현**(MegaRAID 에서도 동일 — 카드 계열 무관 · RAID 뒤 에이전트 reboot 경로의 공통 결함) |
| ~18:49 | #5 사이버 웨이브 | **사용자 보고: network boot 실패 → 수동 PXE 진입** | F-2b 5/5 — 이번 회차 RAID → OS 전이를 거친 다섯 대 전부 |
| 18:48:44 | #5 사이버 웨이브 | PXE 부팅 요청(수동 PXE · IP .131) → 토큰 발급 → **wimboot 체인 서빙 = 착수 · diskId=0** | 예상대로 0(VD0 = spvR1V1 OS). MegaRAID 양성 대조 1 — WinPE diskpart 기대: Disk 0 = 446 GB · Disk 1 = 54.6 TB. 완료 보고 UniqueId = wwn `600605b00d104d7d323305f008471c90` 그대로면 diskConfirmed=true |
| 18:48:50 | #4 사이버 성우 | PXE 부팅 요청(수동 PXE · IP .175) → 토큰 발급 → **wimboot 체인 서빙 = 착수 · diskId=0** | 양성 대조 2 — 기대: Disk 0 = 446 GB · Disk 1 = 18.2 TB. 완료 보고 UniqueId = wwn `600605b00d18aa1e3233141a0c762e2d` 기대. 다섯 대 전부 Windows 설치 착수(일산 3 종단 · 9361-8i 2 진행) |
| 18:59:57 | #5 사이버 웨이브 | **설치 완료 보고 · 종단** — SPV-0F961A7D · 드라이버 47 · 문제 장치 0 · **diskConfirmed=true** · UniqueId `600605B00D104D7D323305F008471C90` = spvR1V1 wwn 그대로 | 서빙 → 완료 11분 13초. **MegaRAID 양성 대조 성립** — SSD OS 볼륨에 설치 · 확증 일치. F-6 은 SAS3008(sas3ircu 순서) 한정으로 확정 |
| 19:00:04 | #4 사이버 성우 | **설치 완료 보고 · 종단** — SPV-0F961A9D · 드라이버 47 · 문제 장치 0 · **diskConfirmed=true** · UniqueId `600605B00D18AA1E3233141A0C762E2D` = spvR1V1 wwn 그대로 | 11분 14초. 양성 대조 2/2. **다섯 대 전부 종단** — 관찰 회차 마감 |

## 5. 발견 · 관찰

(회차 진행 중 채움 — 결함은 F-, 관찰은 O- 로 번호)

- **O-1(#1 · 17:04:42)** — MPT_IR(SAS3008) 서버의 진단 lsblk 순서가 `sda=HDD 14.6T · sdb=SSD 223.6G · sdc=HDD 14.6T` 로, SSD 가 두 번째다(슬롯 순이 아님). 볼륨 0 인 JBOD 상태라 E4-1-a-6 이 미실측으로 남긴 **물리 디스크 직결 열거 순서의 실물 표본**이다 — 이 서버가 Windows 설치까지 가면 WinPE diskpart 번호와 대조할 기회(E4-1-a-7 입력).
- **O-2(#1~#3 · 17:05)** — 같은 보드 · 같은 SAS3008 · 같은 디스크 모델인데 **진단 lsblk 순서가 세 대 모두 다르다**: #1 `HDD·SSD·HDD`, #2 `SSD·SSD·HDD·HDD`, #3 `HDD·SSD·SSD·HDD`. RAID 인벤토리(컨트롤러 슬롯 순)는 일정한데 커널 열거는 부팅마다·개체마다 흔들린다. **JBOD 직결에서는 lsblk 순서를 디스크 번호의 근거로 쓸 수 없다** — E4-1-a-6 이 직결 혼재를 BLOCKED 로 막은 판단이 옳았고, E4-1-a-7 은 순서가 아니라 식별자(WWN · 시리얼)로 잡아야 한다는 실물 근거.
- **O-3(#4 · 17:12:48)** — MegaRAID 뒤 물리 디스크 8 이 전부 UGood(미구성)이면 진단 리눅스 `lsblk` 가 **빈 목록**이다(컨트롤러가 볼륨 없는 디스크를 OS 에 노출하지 않음). 진단 · BMC 표준화는 그래도 정상 완주했다 — hardware_spec.disks=[] 를 관용하는 것이 확인됨. SAS3008(IR 모드 JBOD 노출)과 MegaRAID(볼륨만 노출)의 이 차이가 RAID 인벤토리 카드가 따로 있는 이유다.
- **O-4(#1' · 17:17:05)** — 회수 → 디스크 추가 → 재부팅 → 재등록의 재투입 흐름(U6)이 새 인벤토리(디스크 4)를 정확히 반영했다. 옛 게스트 행(디스크 3)은 회수 상태로 보존돼 대조가 된다. lsblk 순서는 이번에도 달랐다(O-2 보강 · 표본 4).
- **O-5(#4 · 17:31:48)** — BMC 의 Redfish TaskMonitor 번호(13)와 Tasks 컬렉션 번호(12)가 어긋나는 현상이 실기에서 재발했고, `RedfishSimpleUpdateProvider` 의 "컬렉션 최신 Task 로 판정" 관용 경로가 그대로 흡수했다(E2-2 실측 파생). 두 대 동시 굽기에서도 판정이 어긋나지 않았다. BMC 축 전송 3분 12초는 E0-4 단독 실측(7분 37초)보다 짧다 — E0-4 는 적용(재부팅)까지 잰 값이고 여기 "전송 완료" 는 업로드 · Task 종료까지라 잣대가 다르다(비교 주의).
- **F-1(일산 3대 · 사용자 지적 · E4-1-a-6 설계 공백)** — Windows 설치 준비도의 디스크 선택이 `raid_volume` 행(RAID 구성 phase 가 **집행한 뒤**에야 생기는 실물)만 보므로, RAID 단계가 아직 돌지 않은 정의서(9: 펌웨어 → 설정 → RAID → OS)에서는 개시 전 카드가 "OS 영역 RAID 볼륨이 없습니다" 로 BLOCKED 를 띄운다. 그러나 RAID 구성 **계획**(RaidPlanner 미리보기)은 규칙 1(RAID1 · 자동 탐지 · 2개씩)과 우선순위로 이미 spvR1V1 을 OS 영역으로 정해 두었다. 판정 시점을 phase 에 맞춰야 한다: RAID 단계가 남아 있으면 **계획의 OS 영역 지정**을 근거로 "RAID 구성 뒤 디스크 번호 확정" 로 두고(BLOCKED 아님), RAID 집행 뒤(OS 설치 게이트 시점)에는 지금처럼 실물 행으로 번호를 계산한다. 계획에도 OS 영역이 없을 때만 BLOCKED. 개시 자체는 준비도와 무관(`isStartableWith`)해 흐름은 막히지 않으나 카드가 잘못 안내한다.
- **F-2(#4 · 17:36:40~ · 사용자 발견 · E2.5/E3-1 실기 결함)** — BIOS 설정 5개 PATCH(전원 계열 · pendingSeen) 뒤 Redfish ForceRestart 를 "다음 부팅 PXE 강제" 와 함께 발행했으나 서버가 PXE 로 오지 않고 UEFI 셸로 떨어졌다. 펌웨어 굽기 뒤 전원 투입(17:32:26)의 같은 강제는 성립했다(17:36:33 PXE 도착). 유력 원인: BIOS 가 pending 설정을 적용하는 재시작에서 내부 리셋을 한 번 더 하며 **한 번만 유효한 BootSourceOverride(Once)** 를 첫 사이클에서 소비 → 두 번째 부팅은 평소 순서 → 부팅 가능한 디스크가 없어(볼륨 0 · UGood 8) UEFI 셸. 08-27 실기는 OS 디스크가 있어 로컬 부팅으로 가려졌을 가능성. **영향**: BIOS 설정 phase 를 지나는 다섯 대 전부 재현 위험(부팅 디스크 없는 서버 공통). **회차 중 우회** = 셸에서 `exit` → 부트 매니저 → UEFI PXE 선택, 또는 전원 제어의 네트워크 부팅 재시작(강제 재발행). **정정 방향(E2.5 후속)** = 프로비저닝 중에는 override 를 Continuous 로 두고 종단·실패·회수 때 해제, 또는 설정 적용 재시작 뒤 override 를 재발행하고 PXE 도착을 확인 — 부팅 순서 자체를 네트워크 우선으로 강제하는 안(E2-4 논의)도 후보.
- **F-3(#4 · 17:43:24 · E3.5-6 실기 결함 · MegaRAID 경로 차단)** — RAID 집행이 `storcli64 /c0 add vd type=raid1 name=spvR1V1 drives=252:0,252:1 wt ra direct strip=256 pdcache=default` 까지 **Success** 로 만들고, 곧이은 VD 후속 설정 `storcli64 /c0/v0 set bgi=off` 에서 storcli 007.2508.0000.0000(2023-02-27)이 `syntax error, unexpected TOKEN_BGI` 로 거절 → CREATE_REJECTED · 진행 실패. E3.5-6(VD 파라미터 · PR #64)이 넣은 setOps `bgi=off` 의 **키워드가 이 CLI 판에서 유효하지 않다**(실기 첫 집행에서 드러남 — E3.5-6 CP7 은 일괄 실기 대기 중이었음). 영향: 9361-8i 두 대(#4 · #5) 모두 RAID phase 에서 같은 벽. 부분 상태 = 카드에 spvR1V1 만 생성(spvR2V1 미생성). 재시도는 우리 잔여(spvR*)를 재구성 대상으로 지우고 다시 만드는 설계라 정정 후 [재시도]로 이어갈 수 있다. 정정 = setOps 키워드를 이 CLI 가 받는 형태로(후보 `autobgi=off` — 게스트 콘솔에서 `storcli64 /c0/v0 set help` 로 실측 확인 필요) 또는 bgi 후속 설정을 생략. **→ 18:20 6차 재배포(`autobgi=`)로 정정 · 18:44:56 사이버 성우 재집행에서 `set autobgi=off` Success 실증(F-3 종결 · HF 커밋 대기).**
- **F-4(#1' 일산 상2 · 17:58:05 · E3-1 실기 결함 · 판독 시점 경합)** — BIOS 설정 PATCH(pending 확인) 뒤 Redfish ForceRestart 를 발행했으나(Task state=null), 서버가 재시작하기 전에 진단 리눅스 iPXE 대기 체인의 30초 폴링(`/boot`)이 먼저 도착했고, `ReturnReadbackStep` 이 그것을 재시작 후 복귀로 간주해 속성을 읽어 5개 전부 미반영 → READBACK_MISMATCH 실패. #4 · #5 는 ForceRestart 가 즉시 먹혀 폴링 전에 iPXE 가 죽었기에 피했고, 일산 상2 는 BMC 가 직전 굽기 · 자체 재부팅 직후라 리셋 집행이 늦었던 것으로 보인다(BMC 리셋 지연 의존 = 경합). **영향**: BMC 굽기 직후 설정 phase 에 드는 서버 전부(일산 상3 · 상1 이 곧 같은 구간). **회차 중 조치** = [재시도](재PATCH · 재리셋 — BMC 가 안정된 뒤라 즉시 리셋될 가능성 큼). **정정 방향(E3-1 후속 HF)** = 판독은 "실제 재부팅 증거" 뒤에만: Reset Task 완료 · PowerState 사이클 확인 · 또는 리셋 발행 뒤 최소 POST 시간(예 60초) 안의 `/boot` 는 대기 체인 폴링으로 보고 무시 · 또는 BIOS pending 플래그가 사라진 뒤 판독.
- **O-7(#3 일산 상1 · 17:58:40)** — 펌웨어 굽기 뒤 PXE 요청이 등록 때와 다른 NIC 포트(MAC …c4:cc, IP .170)에서 왔다. 등록 매칭은 systemUUID 라 같은 서버로 이어졌다(U1 설계의 값). 부팅 NIC 순서가 리셋으로 바뀔 수 있음 — MAC 기반 매칭이었다면 유령 등록이 생겼을 사례.
- **F-5(#3 일산 상1 · 사용자 지적 · U1/E1 기능 공백)** — 사용자가 LAN 선을 등록 때와 다른 포트에 꽂았는데(O-7 의 원인), 상세의 호스트 NIC 섹션은 여전히 등록 때의 NIC(…c4:cd · .184) 하나만 보인다. `GuestServerRegistrationService` 의 재부팅 경로는 토큰만 보장하고 돌아가며, NIC 바인딩(`HostNicBinding`)은 첫 등록에서만 생성되고 어떤 경로도 추가·갱신하지 않는다. 흐름은 systemUUID 매칭이라 끊기지 않았지만 표시가 실물과 어긋나고, 예약 IP 가 옛 MAC 에 묶여 있으면 새 포트는 동적 IP 를 받는다(.170). **정정 방향** = 매 `/boot` 에서 MAC · IP 를 바인딩에 반영(새 MAC → 바인딩 추가 · 현재 부팅 포트 표시 / 같은 MAC → IP 재임대 갱신).
- **O-8(#2 일산 상3 · 18:04:48 · E4-1-a-6 실기 진입)** — MPT_IR(sas3ircu) 경로에서 RAID 구성이 OS 역할 볼륨(spvR1V2 · SSD 쌍)을 만들고 WWN(wwid)까지 저장했다. 재채집 인벤토리는 OS 볼륨을 첫 번째(id 322)로 나열해 디스크 선택이 **DiskID 0** 을 계산할 조건이다. 이 카드 계열의 Windows 열거 순서(D1)는 미실측이라, 설치 뒤 완료 보고의 UniqueId ↔ wwid 대조가 이번 회차의 핵심 판정이다(어긋나면 데이터 볼륨에 설치된 것 — 방금 만든 빈 볼륨이라 손실은 없음). IR 볼륨의 Windows UniqueId 가 wwid 와 같은 형식인지도 함께 확인.
- **F-2b(#2 일산 상3 · ~18:06 · 사용자 발견 · F-2 의 변종)** — RAID 집행 검증 통과 뒤 커서가 OS_INSTALLING 으로 전진하면 진단 리눅스 에이전트가 **자기 `reboot`** 로 재부팅한다. 이 경로엔 Redfish 전원 조작이 없어 PXE 강제(BootSourceOverride)가 전혀 걸리지 않고, 부팅 순서가 디스크 우선인 서버는 방금 만든 빈 RAID 볼륨을 부팅 디스크로 시도하다 UEFI 셸로 떨어진다. 이전 실기(E3.5 · 09-01)는 이 전이 뒤 OS 설치 phase 가 없거나 부팅 순서가 네트워크 우선이라 가려졌던 것으로 보인다. 18:48 사이버 성우 · 18:49 사이버 웨이브(MegaRAID)에서도 재현돼 **5/5** — 카드 계열과 무관한 경로 결함으로 확정. **F-2 와 같은 뿌리**: 프로비저닝 중 부팅은 반드시 PXE 여야 하는데 그것을 보장하는 장치가 phase 마다 다르다(펌웨어 = PowerOn 강제 O · 설정 = ForceRestart 강제 O(경합) · RAID→OS = 에이전트 reboot 강제 X). **정정 방향(통합)** = 개시 시점에 BootSourceOverride 를 Continuous+Pxe 로 걸고 종단 · 실패 · 회수 때 해제(E2.5 후속 · 모든 경로를 한 장치로), 또는 에이전트 REBOOT 지시 직전에 서버가 Redfish 로 Once 강제를 발행.
- **O-9(일산 3대 · 18:10:16 · E4-1-a-6 자연 대조 실험)** — 같은 보드 · 같은 SAS3008 · 같은 디스크 4개인데 계산된 DiskID 가 상3 = 0, 상1 · 상2 = 1 로 갈렸다. 원인은 두 겹이다. ① `RaidPlanner` 는 자동 탐지 규칙에서 디스크를 슬롯 순으로 모델별 묶음을 만들어 spvR1V1 · spvR1V2 로 번호를 매기고, OS 역할은 우선순위(SSD)로 준다 — 상3 은 SSD 가 뒤 슬롯(1:2 · 1:3)이라 spvR1V2 가 OS, 상1(1:0 · 1:1) · 상2(1:0 · 1:2)는 SSD 가 앞이라 spvR1V1 이 OS(역할이 슬롯과 무관하게 SSD 를 따라간 것은 O-8 의 보강). ② sas3ircu 재채집 인벤토리는 세 대 모두 볼륨을 [322 = spvR1V2, 323 = spvR1V1] 순으로 나열한다(두 번째로 만든 볼륨이 앞) → OS 가 spvR1V2 면 index 0, spvR1V1 이면 index 1. 그래서 이번 회차는 "인벤토리 순서 = Windows 디스크 번호" 라는 E4-1-a-6 의 전제를 SAS3008 계열에서 **상반된 두 값으로 동시에 검증**한다: 전제가 맞으면 세 대 모두 diskConfirmed=true, 틀리면(예: Windows 가 생성 순 V1 → V2 로 열거) 세 대 모두 false 이고 설치가 방금 만든 빈 DATA 볼륨(HDD 14.5 TB)으로 간다(손실 0). WinPE diskpart 표에서 먼저 볼 수 있다 — 기대: **상3 은 Disk 0 = 223 GB · Disk 1 = 14.5 TB**, **상1 · 상2 는 Disk 0 = 14.5 TB · Disk 1 = 223 GB**. 추가 확인: IR 볼륨의 Windows UniqueId 가 wwid(16 hex)와 같은 형식인지 — 다르면 설치가 맞아도 MISMATCH 로 남을 수 있어 diskpart 사진과 설치된 C: 용량이 최종 근거.
- **F-6(일산 상1 · 상2 · 18:25:05 · E4-1-a-6 실기 결함 · SAS3008 에서 인벤토리 순서 ≠ Windows 디스크 번호)** — 두 대 모두 계산된 DiskID 1 로 설치했고 완료 보고의 UniqueId 가 **DATA 볼륨(spvR1V2 · HDD 14.5 TB RAID1)** 을 가리켰다. 즉 Windows 는 spvR1V1 을 Disk 0, spvR1V2 를 Disk 1 로 열거했는데(만든 순서), sas3ircu 재채집 인벤토리는 [322 = spvR1V2, 323 = spvR1V1] 로 **거꾸로** 나열한다(두 번째로 만든 볼륨이 낮은 ID 를 받아 앞에 온다). MegaRAID 에서는 storcli 의 VD 순서가 Windows 순서와 같았으므로(Run 3 D1) E4-1-a-6 은 그 전제를 카드 계열 무관으로 일반화했는데, MPT_IR 에서 깨졌다. 결과: OS 가 SSD 가 아니라 HDD 볼륨에 설치됐고 SSD 볼륨은 비어 있다(방금 만든 빈 볼륨이라 손실 0 · 두 볼륨 모두 지워진 상태이므로 재설치로 복구). **정정 후보** ① 카드 계열별 순서 규칙을 `RaidChipFamily` 에 method-per-constant 로: MEGARAID = 인벤토리 순서 그대로, MPT_IR = 볼륨 ID 내림차순(= 생성 순 · 표본 3대 · 볼륨 2개 — 3개 이상은 미실측이라 가설) ② 카드 계열 무관 방법: RAID 검증 재채집 때 `lsblk` 도 wwid 와 함께 다시 채집해(지금은 hardware_spec 이 RAID 전 JBOD 4개 그대로 남아 있음) OS 볼륨 wwid 가 lsblk 몇 번째인지로 DiskID 를 정한다 — Run 3 에서 lsblk 순서 = diskpart 순서가 실증됐고 Linux · Windows 모두 SAS 타깃 탐색 순서를 따르므로 계열 분기가 사라진다. ② 를 본안으로, ① 은 ② 가 없을 때의 보조 규칙으로 제안. E4-1-a-6 후속 HF. **19:00 양성 대조**: MegaRAID 두 대(사이버 성우 · 웨이브)는 DiskID 0 → SSD OS 볼륨 설치 · diskConfirmed=true — 결함은 SAS3008 계열의 순서 전제에 한정된다(E4-1-a-6 의 계산 · 치환 · 확증 배선 자체는 정상).
- **F-7(일산 상1 · 상2 · E4-1-a-6 확증 결함 · IR 볼륨의 UniqueId 형식)** — MPT_IR 볼륨의 Windows `Get-Disk` UniqueId 는 wwid 그대로가 아니라 **`600508E0` + `00000000` + wwid 8바이트를 뒤집은 16 hex**(NAA 6 · LSI OUI 0508E0) 다. MegaRAID 는 UniqueId = WWN 32 hex 가 그대로 일치했다(Run 3). 지금 대조는 대소문자 무시 문자열 비교라 IR 계열은 **맞는 디스크에 설치돼도 MISMATCH** 로 남는다. 정정 = 카드 계열별 정규화(`RaidChipFamily` method-per-constant: MEGARAID = 그대로 · MPT_IR = 꼬리 16 hex 바이트 뒤집기 뒤 wwid 와 비교). F-6 정정과 같은 HF 로 묶는다. 이번 회차의 false 둘은 F-7 이 아니라 실제 어긋남(F-6)이었음을 뒤집기 대조로 확인했다. MegaRAID 는 이번에도 UniqueId = WWN 32 hex 그대로(대문자)로 확증 true 2/2.
- **O-10(사이버 웨이브 · 18:23:51 · 사용자 질문)** — 꺼진 서버에 [재시도] 를 눌러도 켜지지 않는다. 코드상 `GuestServerCommandService.retry` 는 실패 플래그를 지우고(`clearFailed`) 변경 이벤트만 발행하며 Redfish 전원 조작이 없다 — 재시도는 "커서를 부팅 대기로 되돌리는" 조작이고 부팅은 운영자 몫이다. 사용자 판단(그 사이 배선 · 연결 상태가 바뀌었을 수 있으니 전원은 손대지 않는 것도 맞다)과 현행 설계가 일치한다. 다만 재시도 뒤 운영자가 예외 없이 하는 다음 동작이 "네트워크 부팅" 이므로, 전원 제어에 이미 있는 네트워크 부팅 재시작(꺼짐 = On + PXE 강제 · 켜짐 = ForceRestart + PXE 강제)을 재시도와 한 번에 묶는 명시적 선택지(예: [재시도 후 네트워크 부팅])를 두는 안을 F-2 · F-2b 의 PXE 보장 통합과 함께 검토(E2-4 · E2.5 후속). 기본 [재시도] 는 지금처럼 전원 중립으로 둔다.
- **F-8(사이버 성우 · 웨이브 · 18:31:38 · E3.5 재시도 결함 · 재시도가 인벤토리를 다시 채집하지 않는다)** — [재시도] 는 커서를 실패한 단계(RAID_APPLYING · AWAITING_BOOT)에 그대로 두고 실패 플래그만 지운다. 다음 체크인에서 집행 페이로드는 `guest_server_detail.raid_inventory_json`(첫 진단 때 채집 · 볼륨 0)으로 계획을 세우므로 `RaidPlanner` 의 `deleteExistingFirst = !inventory.volumes().isEmpty()` 가 false 가 되고, 1차 시도가 컨트롤러에 남긴 spvR1V1(VD0) 을 모른 채 같은 슬롯에 `add vd` 를 다시 보내 storcli 가 `disk doesn't have enough capacity`(ErrCd 13 · 드라이브가 이미 VD 소속)로 거절한다. E3.5 토론의 "재시도는 우리 잔여(spvR*)를 재구성 대상으로 지우고 다시 만든다" 는 인벤토리가 최신이라는 전제 위에 있었고, 재시도 경로가 그 전제를 만들지 않는다. **정정 방향** = phase 별 재시도 진입 단계를 실행기가 결정하게(다형: `RaidConfigurationExecutor` 가 재시도 시 RAID_INVENTORY_COLLECTING 으로 되감아 같은 부팅에서 재채집 → 계획 → 집행). 또는 집행 직전 게스트 인벤토리를 다시 받아 계획을 세우는 방식(체크인 페이로드에 현재 인벤토리 동봉). **회차 중 우회** = 진단 리눅스 콘솔에서 잔여 VD 를 지운 뒤(`storcli64 /c0/v0 del force` — 두 대 모두 VD0 = spvR1V1 하나뿐 · 첫 진단 때 8 디스크 전부 UGood 이었으므로 다른 볼륨 없음) [재시도] → 재부팅. 옛 인벤토리(볼륨 0)가 실물과 다시 일치하므로 계획이 두 볼륨을 새로 만든다.
- **F-9(9361-8i · 사용자 지적 · 마감 뒤 · E1 표시 결함)** — MegaRAID 뒤 가상 디스크(VD)가 진단 리눅스에 `sda` · `sdb` 로 보일 때 상세의 "OS 가시 장치 디스크" 표가 **종류 HDD · 전송 —** 로 고정된다(win test · win test - run 3 실측: `{"type":"HDD","transport":null,"size":"446.6G"}`). 원인 = 에이전트가 `lsblk -o NAME,SIZE,ROTA,TRAN` 만 보고하고 서버 파서가 ROTA=1 → HDD 로 옮기는데, 컨트롤러 뒤 VD 는 SSD 멤버라도 ROTA=1 이고 TRAN 은 비어 있어 두 값이 실물을 말하지 않는다. **사용자 지시: 수집이 불가하면 그 행에서 값을 띄우지 않는다.** 정정 방향 = 에이전트가 WWN 을 함께 보고(`lsblk -o …,WWN`) → 서버가 RAID 인벤토리 볼륨 wwn 과 대조해 VD 행은 종류 · 전송을 비우고 "RAID 볼륨 spvR1V1" 로 표기(F-6 본안의 lsblk 재채집 · wwid 매칭과 같은 재료 — 한 HF 로 묶인다). 대조 재료가 없을 때의 보조 규칙 = TRAN 비고 RAID 카드가 있으면 종류를 비운다. 덤으로 BMC 가상 미디어(`USB · 0B` 4행)는 표에서 걸러도 된다(O-11).
- **F-10(다섯 대 · 18:06~19:01 · 사용자 지적 · E1.5 전원 제어 결함)** — 전원 제어의 **정상 종료(Redfish Reset GracefulShutdown)** 가 호스트를 끄지 못했다. 저널: 사이버 웨이브 18:06:46 Graceful → 18:07:52 ForceOff · 사이버 성우 18:08:17 Graceful → 18:15:02 ForceOff · 일산 상2 18:46:56 Graceful → 18:48:12 ForceOff — 세 번 모두 BMC 는 요청을 받아 Task 를 만들었고(TaskMonitors/n · state=null · UI 200) 호스트는 1~7분 안에 꺼지지 않아 사용자가 **강제 종료(ForceOff)** 로 끝냈다(강제 종료는 7/7 즉시 성립). 호스트 상태는 진단 리눅스(Alpine · 전원 버튼 ACPI 이벤트를 처리하는 acpid 없음 추정) 둘과 설치 직후 Windows 하나. GracefulShutdown 은 BMC 가 ACPI 전원 버튼을 누르는 것이라 호스트 OS 가 응답해야 성립하고, 우리 앱은 Task state=null 을 받아 결과를 모른다 → 운영자에게 "전달됐는지 · 무시됐는지" 가 구분되지 않는다. **정정 방향** ① 앱: Graceful 뒤 PowerState 를 폴링(E1.5 의 On 폴링 폴백과 같은 틀)해 일정 시간 안에 Off 가 안 되면 "호스트가 정상 종료에 응답하지 않습니다 — 강제 종료를 쓰십시오" 로 안내(UI 1차 차단 원칙의 사후판) ② 진단 이미지: acpid(BusyBox 내장)로 전원 버튼 → `poweroff` 를 연결해 진단 리눅스에서도 정상 종료가 성립하게 ③ Windows 에서의 무응답은 로그온 화면 · 전원 버튼 정책 확인이 필요(상2 1건 · 재현 필요).
- **Q-1(사용자 지적 · 게스트 이름 유일성)** — 사용자 요구: **회수 여부와 관계 없이 이름은 무조건 겹치지 않아야 한다.** 실 DB 실측(22:21): `guest_server.name` 에 전역 UNIQUE(`UKrom2ekfu43bd2tu1ub8p0u5el`)가 있고, 앱 가드 `GuestServerCommandService.isNameTakenByOther` 도 회수 여부를 가리지 않는 `existsByNameAndIdNot` 이며, 전체 24행(활성 5 · 회수 19)에 중복 이름 0. 즉 코드 · DB 는 이미 전역 유일성이다(U6 가 활성 한정으로 바꾼 것은 system_uuid 뿐 · 이름은 손대지 않았다). 사용자가 본 "제약이 성립하지 않는 상황" 이 어느 화면 · 조작인지 확인 필요 — 안내 문구가 '활성끼리' 로 적혀 있거나, 다른 표시(예: 이름 + 버전 병기)에서 겹쳐 보였을 가능성.

## 6. 마감 상태 (19:00 KST · 다섯 대 전부 종단)

| 서버 | 종류 · 카드 | 개시 → 종단 | Windows 설치 위치 | 확증 | 거친 결함 |
|---|---|---|---|---|---|
| 일산 상2 | B · SAS3008 | 17:38:53 → 18:24:42 (45분 49초) | **HDD DATA 볼륨(오설치)** · DiskID 1 | false(실제 어긋남) | F-4(재시도로 극복) · F-2b · F-6 · F-7 |
| 일산 상1 | B · SAS3008 | 17:39:11 → 18:25:05 (45분 54초) | **HDD DATA 볼륨(오설치)** · DiskID 1 | false | O-7 · F-5 · F-2b · F-6 · F-7 |
| 일산 상3 | B · SAS3008 | 17:39:18 → 18:31:02 (51분 44초) | **HDD DATA 볼륨(오설치)** · DiskID 0 | false | F-2b · F-6 · F-7 |
| 사이버 성우 | C · 9361-8i | 17:25:33 → 19:00:04 (1시간 34분 31초) | **SSD OS 볼륨(정상)** · DiskID 0 | **true** | F-2 · F-3(재배포로 정정) · F-8(수동 우회) · F-2b |
| 사이버 웨이브 | C' · 9361-8i | 17:25:43 → 18:59:57 (1시간 34분 14초) | **SSD OS 볼륨(정상)** · DiskID 0 | **true** | F-3 · F-8 · F-2b |

- **완주**: 다섯 대 모두 펌웨어 → 설정 → RAID → Windows 설치까지 종단. 드라이버 47 · 문제 장치 0 이 다섯 대 공통. 동시 진행에서 BMC 축 전송(9~10분)이 단독(3분)보다 길어진 것 외에 동시성 자체의 결함은 없었다.
- **손실**: 0. 일산 3대의 오설치는 방금 만든 빈 볼륨 위였고 SSD 볼륨도 빈 채 남아 있다 — 정정 뒤 재설치 대상.
- **수동 개입**: 셸 → PXE 전환 5회(F-2b) + 1회(F-2) · [재시도] 5회(F-4 1 · F-3/F-8 4) · 잔여 VD 삭제 2회(F-8) · 6차 재배포 1회(F-3).
- **결함 배정 후보**(사용자 판정 대기): ① **E4-1-a-6 후속 HF** = F-6(디스크 번호 근거를 RAID 뒤 lsblk 재채집 + wwid 매칭으로 · 계열 분기 제거) + F-7(IR UniqueId 정규화 · `RaidChipFamily` method-per-constant) + F-1(RAID 단계 전엔 계획의 OS 영역으로 준비도) ② **PXE 보장 통합 HF(E2.5 · E3-1 후속)** = F-2 + F-2b(개시 시 BootSourceOverride Continuous · 종단/실패/회수 해제) + F-4(판독은 실제 재부팅 증거 뒤) + O-10([재시도 후 네트워크 부팅] 선택지) ③ **E3.5 후속 HF** = F-3(코드 · jar 준비 완료 · 실증 · 커밋 대기) + F-8(재시도 진입 단계를 실행기가 결정 — RAID 는 인벤토리 재채집부터) ④ **U1/E1 HF** = F-5(매 `/boot` 에서 NIC 바인딩 갱신) ⑤ 적립 = OS 역할 유일성 방어 · 그룹 일괄 개시(U3-5 이연) · JBOD 직결 순서(D2) · E4-1-a-7(WinPE 사전 게이트). **마감 뒤 추가(22:20 사용자 지적)**: F-9(VD 행 종류 · 전송 비우기 — ① 에 합류 · 같은 재료) · F-10(정상 종료 무응답 안내 + 진단 이미지 acpid — E1.5 후속 HF) · Q-1(이름 유일성 — 코드 · DB 는 이미 전역 · 사용자 관측 확인 대기).
- **미확인**: 일산 상3 의 설치 시간이 8분 더 걸린 사유 · 사이버 웨이브 첫 설정 재시작(17:44 → 17:48)의 F-2 재현 여부(수동 개입 없이 왔다면 override 성립).

### 6-1. 정정 진행(2026-09-09 · HF15)

결함 F-1~F-10 · O-10 은 **HF15 우산(하위 5)** 로 정정했다 — HF15-1(F-2 · F-2b · F-4 · O-10 PXE 보장 조정자 · last_boot_at · 재시도 후 네트워크 부팅) · HF15-2(F-3 · F-8 재시도 되감기 · autobgi) · HF15-3(F-5 NIC 갱신) · HF15-4(F-10 정상 종료 감시 · 진단 acpid) · HF15-5(F-6 · F-7 · F-1 · F-9 디스크 번호 lsblk 근거 + 계열 규칙 · IR 확증 · 계획 기반 준비도 · VD 표시). plan `plan/26-09-09_*_HF15-*_plan.html` · CP5 `sbx-e416/HF15-CP5-report.md`(17 PASS · 정정 2 · 미수행 1) · report `report/26-09-09_*_HF15*_report.html`. Q-1(이름 유일성)은 코드 · DB 가 이미 전역 유일이라 건너뜀(사용자). 실기 확증(F-2b 0/5 · 일산 3대 SSD 재설치 · acpid)은 실기 4호.

