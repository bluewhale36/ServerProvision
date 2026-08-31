# RAID 카드 관리 방식 조사 — GIGABYTE CRA3338 · AVAGO MegaRAID 9361-8i

작성 2026-08-28 (KST). RAID_CONFIGURATION 단계(U4-1-1 v2 가 정의서에 둔 단계 · `ProvisioningPhase.RAID_CONFIGURATION`)의 집행 설계에 앞선 조사 브리핑이다. 사내 주력 카드 둘(U4-1 토론 6회차 E30)을 대상으로 네 질문에 답한다
1. Redfish 로 다룰 수 있는가
2. 리눅스(진단 Alpine · RHEL 계열)에서 다루는 명령이 있는가
3. 다루는 방식은 무엇이 정하는가
4. 다룰 수 있는 범위는 어디까지인가.
문서로 확인한 것과 실측이 필요한 것을 구분해 적는다.

## 0. 한눈에

| 항목 | GIGABYTE CRA3338 | AVAGO MegaRAID 9361-8i |
|---|---|---|
| 칩 | Broadcom SAS3008 (Fusion-MPT SAS-3) | Broadcom SAS3108 ROC (RAID-on-Chip) |
| PCI ID | `1000:0097` · Subsystem GIGABYTE `1458:????`(실측) | `1000:005d` · Subsystem `1000:9361` |
| 펌웨어 스택 | IR (Integrated RAID) 모드 펌웨어 | MegaRAID |
| 캐시 | 없음 | DDR3 1 GB 또는 2 GB (2 GB 변형 공식 존재) |
| RAID 레벨 | 0 · 1 · 1E · 10 | 0 · 1 · 5 · 6 · 10 · 50 · 60 |
| 리눅스 드라이버 | `mpt3sas` | `megaraid_sas` |
| CLI | `sas3ircu` (구성) · `sas3flash` (펌웨어) | `storcli64` (StorCLI) |
| PCI 클래스 | SAS 컨트롤러 `0x0107` → BMC · lspci 에 "Serial Attached SCSI controller" | RAID 컨트롤러 `0x0104` → "RAID bus controller" |
| Redfish(BMC 경유) | 카드는 가능(3008 OOB 지원 사례 있음) · GIGABYTE BMC 구현 여부 실측 필요 | 동일 |

## 1. 두 카드의 정체

### 1-1. CRA3338 = SAS3008 IR 모드 카드
GIGABYTE 공식 스펙은 Broadcom SAS3008 컨트롤러, PCIe 3.0 x8, Mini-SAS HD 두 포트(직결 8 대, 익스팬더 경유 최대 14 대), **RAID 0 / 1 / 1E / 10 의 Integrated RAID(IR) 기능**, "볼륨 2 개에 걸쳐 14 대(RAID 0 · 1E · 10 은 볼륨당 10 대, RAID 1 은 2 대)", 그리고 "IR 기능을 쓰지 않으면 물리 장치 최대 512 대" 다. 마지막 문장이 중요하다 — IR 펌웨어는 **볼륨에 속하지 않은 드라이브를 그대로 패스스루**하므로, 같은 펌웨어 하나로 "RAID 카드" 와 "HBA" 두 역할을 함께 한다. IT(Initiator-Target) 펌웨어로 바꾸면 RAID 기능이 아예 사라지고 순수 HBA 가 된다.

RAID 5 이상이 없는 것은 캐시가 없어서가 아니라 **SAS3008 에 패리티 연산 엔진과 캐시 메모리 인터페이스가 없기 때문**이다. 즉 칩의 한계이지 GIGABYTE 의 제품 정책이 아니다. U4-1 토론에서 "캐시 유무" 로 적었던 구분은 실제로는 **칩 계열(Fusion-MPT vs ROC)** 의 구분이다.

BMC inventory 와 BIOS 가 이 카드를 "Serial Attached SCSI controller" · "LSI SAS3 MPT Controller" 로 보이는 이유도 여기서 나온다. PCI 클래스 코드가 SAS 컨트롤러(`0x0107`)이고, 이름 문자열은 카드 옵션 ROM 에 내장된 Broadcom 드라이버가 낸다. lspci 도 같은 클래스로 뜬다. 진단 파서 `DiagnosticReportParser.kindOf` 는 현행 코드에서 이미 `serial attached scsi` 클래스를 RAID 로 분류한다(MS74-HB0 실측 반영, E1-2 정정) — U4-1 토론 8회차가 적었던 "SAS 클래스가 ETC 로 떨어진다" 는 결함은 닫혔다(2026-08-28 정정).

### 1-2. 9361-8i = SAS3108 MegaRAID
Broadcom 제품 브리프: SAS3108 듀얼코어 PowerPC ROC, 72-bit DDR3 인터페이스로 **1 GB 또는 2 GB 캐시**, RAID 0 · 1 · 5 · 6 과 스팬 10 · 50 · 60, 논리 드라이브 64 개, 장치 128 대, CacheVault 플래시 캐시 보호(옵션 모듈), 온라인 용량 확장(OCE) · 온라인 RAID 레벨 이관(RLM), 스트라이프 최대 1 MB, 정합성 검사, 패트롤 리드, 전역 · 전용 핫스페어, SES/SGPIO 인클로저 관리. 사내 카드가 "2 GB" 인 것은 이 캐시 옵션 중 큰 쪽이다.

## 2. Redfish 로 다룰 수 있는가

### 2-1. 원리 — 카드가 아니라 BMC 가 답한다
RAID 카드 자체는 Redfish 서버가 아니다. BMC 가 카드와 **대역외(OOB, out-of-band) 채널**로 대화하고, 그 결과를 DMTF Storage 스키마(`/redfish/v1/Systems/{id}/Storage/{controller}` 아래 `Drives` · `Volumes`)로 내보낸다. 채널은 두 세대다 — Gen3 카드(3008 · 3108)는 PCIe 슬롯의 SMBus 핀을 통한 **I2C**, Gen3.5 이후(94xx · 95xx · 96xx)는 여기에 **MCTP over PCIe** 가 더해진다. 그러므로 "Redfish 로 되는가" 는 카드 하나의 속성이 아니라 **카드 × BMC 펌웨어 × 보드 배선** 세 조건의 곱이다.

### 2-2. 카드 측 — 둘 다 OOB 대상이 될 수 있다
Supermicro 의 BMC Redfish 안내는 Storage Management 와 RAID Configuration 을 **Broadcom 3108 · 3008 · 3216 · 3616** 에서 지원한다고 적는다. 3008 이 목록에 있으므로 IR 모드 카드도 하드웨어적으로는 OOB 구성 대상이다. 이는 같은 칩을 쓴 우리 두 카드가 원리상 배제되지 않는다는 뜻이지, GIGABYTE 보드에서 된다는 뜻은 아니다.

### 2-3. BMC 측 — GIGABYTE 는 AMI MegaRAC SP-X, RAID 는 옵션 팩
AMI 의 MegaRAC SP-X 데이터시트는 **"Broadcom RAID" 를 Technology Pack**(핵심 펌웨어와 별도로 ODM 이 선택해 넣는 모듈)으로 분류한다. GIGABYTE 가 우리 보드(MD72-HB3 · MS03-CE0 등) BMC 빌드에 이 팩을 포함했는지는 공개 문서로 확인되지 않았다. GIGABYTE Management Console 사용자 안내서에도 RAID 관리 페이지에 대한 서술은 찾지 못했다. **실측이 유일한 답이다**:

```
GET /redfish/v1/Systems/Self/Storage          → Members 가 비어 있으면 미지원
GET /redfish/v1/Systems/Self/Storage/{id}     → StorageControllers[].Model 에 카드가 보이면 지원
GET /redfish/v1/Chassis/Self/Drives           → 드라이브 노출 여부
```
E0-4 실측 계정으로 게스트 세 대(CRA3338 장착 · 9361-8i 장착)에서 각각 한 번씩 보면 된다. BMC 웹 UI 좌측 메뉴에 RAID 항목이 있는지도 같은 답을 준다.

### 2-4. 지원될 때 표준이 정의하는 조작 범위
DMTF 스키마 기준 — 컨트롤러 · 드라이브 · 볼륨 조회, `POST …/Volumes` 로 볼륨 생성(`RAIDType` · `Links.Drives[]` · `CapacityBytes` · `StripSizeBytes` · `ReadCachePolicy` · `WriteCachePolicy`), `DELETE` 로 삭제, 드라이브 핫스페어 지정(`HotspareType`), 위치 LED(`LocationIndicatorActive`), 펌웨어 갱신(UpdateService). 벤더 구현이 이 중 어디까지 노출하는지는 제각각이다 — Lenovo XCC · Dell iDRAC · Supermicro 는 생성까지 낸다. IR 카드의 볼륨 생성이 노출되는지는 구현마다 다르다.

### 2-5. 실무 판단
GIGABYTE BMC 가 지원한다는 증거가 없고, 지원하더라도 IR 카드 구성까지 노출될지는 더 불확실하다. **집행 본류는 리눅스 CLI 로 두고, Redfish 는 실측 결과에 따라 조회 · 보조 경로로만 고려**하는 것이 맞다. 실측에서 Storage 컬렉션이 비어 있으면 이 항목은 닫는다.

## 3. 리눅스에서 다루는 명령

### 3-1. 커널 드라이버 — 두 리눅스 모두 갖고 있다
- **진단 Alpine**(linux-lts 6.12, modloop): `kernel/drivers/scsi/mpt3sas/mpt3sas.ko.gz` 와 `kernel/drivers/scsi/megaraid/megaraid_sas.ko.gz` 둘 다 패키지에 있다(pkgs.alpinelinux.org 확인). 현재 진단 이미지는 modloop 를 공식 아티팩트 그대로 쓰므로 추가 빌드 없이 모듈이 있다.
- **RHEL 9 계열**: RHEL 8 · 9 가 제거한 것은 SAS2 세대(SAS2008 · 2108 · 2116 등)다. Rocky 9 에서 mpt3sas 가 안 되던 사례도 SAS2008(`1000:0072`) 이었고 ELRepo kmod 로 해결했다. SAS3008(`0097`) · SAS3108(`005d`) 은 RHEL 9 커널에 남아 있다. **RHEL 10 의 제거 · 비유지 목록은 Red Hat 문서 접근이 막혀 확인하지 못했다** — E4 OS 설치 대상이 RHEL 10 이면 T3 에서 설치 미디어 부팅 후 `lspci -k` 로 확인한다.

### 3-2. CRA3338 — `sas3ircu` (SAS3 Integrated RAID Configuration Utility)
Broadcom 이 배포하는 IR 전용 CLI 다(SAS3IRCU P16/P17). 커널 `mpt3sas` 위에서 ioctl 로 카드와 대화한다. 지원 OS 는 RHEL 5 이상 · SLES 10 이상 · UEFI 셸 · DOS · FreeBSD 다. 명령 전체는 다음과 같다 — `CREATE` · `DELETE`(전체) · `DELETEVOLUME` · `DISPLAY` · `HOTSPARE` · `STATUS` · `LIST` · `CONSTCHK`(정합성 검사) · `ACTIVATE`(비활성 볼륨 활성화) · `LOCATE`(LED) · `LOGIR`(로그) · `BOOTIR`(부팅 볼륨 지정) · `BOOTENCL`(부팅 인클로저 지정) · `HELP`.

```
sas3ircu list                                   # 컨트롤러 번호 · 칩 · 펌웨어
sas3ircu 0 display                              # 물리 드라이브(Enclosure:Bay) · 볼륨 · RAID Support 여부
sas3ircu 0 create RAID1 MAX 1:0 1:1 vol0        # 첫 Enclosure:Bay 가 primary
sas3ircu 0 create RAID10 MAX 1:0 1:1 1:2 1:3 vol1
sas3ircu 0 hotspare 1:4                         # 컨트롤러당 최대 2 대
sas3ircu 0 status                               # 동기화 · 상태
sas3ircu 0 deletevolume 0
```
제약은 가이드 명문 그대로다 — 컨트롤러당 볼륨 최대 2 개, RAID 0 은 2~10 대, RAID 1 은 정확히 2 대, RAID 1E · 10 은 3~10 대, 핫스페어 컨트롤러당 최대 2 대, SSD 와 HDD 는 핫스페어를 섞지 못한다. 용량은 MB 단위 또는 `MAX`. 스트라이프 크기 · 캐시 정책 옵션은 없다(캐시가 없다).

StorCLI 는 MegaRAID 용이라 SAS3 HBA 에는 조회 · 펌웨어 정도만 통하고 IR 볼륨 생성은 하지 못한다(Broadcom 이 9300 계열 다운로드에 StorCLI 를 올린 것은 펌웨어 갱신 용도). 펌웨어 갱신과 IT/IR 모드 전환은 `sas3flash` 다.

**미확인**: `sas3ircu` 리눅스 바이너리가 정적 링크인지. Alpine(musl) 에서 안 돌면 `apk add gcompat` 으로 glibc 호환층을 올린다. 실측 항목이다.

### 3-3. 9361-8i — `storcli64` (StorCLI)
Broadcom 의 현행 MegaRAID CLI 다(구 MegaCli 의 후속). 바이너리는 **정적 링크**로 배포되므로(Debian 패키징 검토에서 libm · ncurses 내장이 확인됨) Alpine 에서 그대로 돌 가능성이 높다 — 실측으로 닫는다.

```
storcli64 /c0 show                               # 컨트롤러 · VD · PD 요약
storcli64 /c0/eall/sall show                     # 물리 드라이브 (EID:Slt)
storcli64 /c0 add vd type=raid5 drives=252:0-2 strip=256 wb ra pdcache=default
storcli64 /c0 add vd type=raid1 drives=252:0-1
storcli64 /c0/e252/s3 add hotsparedrive          # 전역 · dcs=0 으로 전용
storcli64 /c0/v0 set wrcache=wb rdcache=ra       # 캐시 정책
storcli64 /c0/v0 start init [full]               # 초기화
storcli64 /c0/v0 start cc                        # 정합성 검사
storcli64 /c0/vall del force                     # 전체 삭제
storcli64 /c0/fall del                           # 외부 구성 제거
storcli64 /c0 set jbod=on ; /c0/e252/s5 set jbod # 패스스루
storcli64 /c0/e252/s0 start locate               # LED
storcli64 /c0 show all                           # BBU · CacheVault · 펌웨어
storcli64 /c0 download file=mr3108fw.rom         # 펌웨어
```
personality(RAID / JBOD / HBA) 전환, 패트롤 리드, OCE · RLM 도 같은 CLI 다.

### 3-4. 공통 — UEFI HII 메뉴
두 카드 모두 옵션 ROM 이 BIOS 셋업의 Advanced 탭에 구성 메뉴(HII)를 넣는다. 사람이 하는 경로라 자동화 대상은 아니다. 메뉴 이름이 GIGABYTE 가 아니라 "LSI SAS3 MPT Controller" · "AVAGO MegaRAID <SAS3108>" 인 이유는 §1-1 과 같다 — 카드 ROM 안의 Broadcom 드라이버가 화면을 낸다.

## 4. 다루는 방식은 무엇이 정하는가

결정 인자를 우선순위로 풀면 다음과 같다.

1. **칩 계열**이 커널 드라이버와 CLI 계열을 정한다. Fusion-MPT SAS-3(SAS3004 · 3008 · 3216 · 3224 …) → `mpt3sas` + `sas3ircu` / `sas3flash`. MegaRAID ROC(SAS3108 · 3316 · 3324, 이후 3508 · 3516 · 3908 …) → `megaraid_sas` + `storcli`. 브랜드는 여기에 영향이 없다.
2. **펌웨어 스택과 모드**가 할 수 있는 조작을 정한다. 같은 SAS3008 이라도 IT 펌웨어면 RAID 기능이 없고, IR 펌웨어면 RAID 0/1/1E/10 이다. MegaRAID 는 personality(RAID / JBOD / HBA)로 같은 구분을 한다.
3. **OEM(브랜드)** 이 정하는 것은 Subsystem ID(식별), 출하 시 어떤 펌웨어 모드를 실었는가, 펌웨어 파일의 배포처, 드물게 OEM 잠금이다. CRA3338 은 IR 모드로 출하됐고, 정품 9300-8i 는 IT · IR 둘 다 배포된다. IBM M1215 · Supermicro AOC-S3008L · Lenovo N2215 처럼 SAS3008 을 쓰는 남의 카드에 정품 9300-8i 펌웨어를 넣는 사례가 널리 있다는 것 자체가, 이 카드들이 도구 관점에서 동일하다는 증거다.
4. **BMC 펌웨어와 보드 배선**이 OOB(Redfish) 가능 여부를 정한다. 카드 벤더가 아니라 보드 벤더 소관이다.

따라서 사용자의 질문에 직접 답하면 — **CRA3338 을 다루는 방식은 "SAS3008 을 쓰는 모든 카드" 와 같고, "GIGABYTE 라인업" 과는 무관하다.** GIGABYTE 의 다른 카드가 MegaRAID 칩을 쓰면 그 카드는 9361-8i 쪽 방식이다. 프로젝트의 카드 식별 축은 그러므로 **Vendor:Device(칩) → 명령 계열, Subsystem(카드) → 자원 대조** 두 층으로 두는 것이 맞다(MA-raidcard 브리핑의 A/B 층 구분과 일치).

## 5. 다룰 수 있는 범위

| 조작 | CRA3338 (sas3ircu) | 9361-8i (storcli) |
|---|---|---|
| 볼륨 생성 · 삭제 | O — 최대 2 볼륨 | O — 최대 64 VD |
| RAID 레벨 | 0 · 1 · 1E · 10 | 0 · 1 · 5 · 6 · 10 · 50 · 60 |
| 드라이브 수 | RAID1 = 2, 그 외 최대 10 | 레벨별 최소만, 최대 128 장치 |
| 핫스페어 | 전역 2 대 | 전역 · 전용, 되돌림 지원 |
| 패스스루(JBOD) | 볼륨 미소속 드라이브 자동 | JBOD 모드 · personality |
| 캐시 정책 | 없음 | WB/WT · RA · 드라이브 캐시 · CacheVault |
| 스트라이프 · 용량 | 용량만(MB/MAX) | 스트라이프 최대 1 MB · 용량 |
| 초기화 · 정합성 검사 | 생성 시 동기화 · CONSTCHK | init · cc · 패트롤 리드 |
| 온라인 확장 · 이관 | 없음 | OCE · RLM |
| 부팅 볼륨 | BOOTIR | VD boot 설정 |
| LED · 로그 | LOCATE · LOGIR | locate · events |
| 펌웨어 | sas3flash | storcli download |
| Redfish | BMC 지원 시 조회 · 생성 · 삭제 · 핫스페어 · LED | 동일 + 캐시 정책(구현별) |

집행에서 알아둘 동작 차이 — IR 볼륨은 생성 직후 백그라운드 동기화를 하며, 리눅스에는 단일 `/dev/sdX` 로 보인다. MegaRAID 볼륨은 fast init 이면 즉시 쓸 수 있고 full init 은 대기가 필요하다. OS 설치 전에 "동기화 완료" 를 기다릴지는 RAID_CONFIGURATION 단계 설계에서 정한다.

## 6. 프로젝트에 대한 함의

- **집행 경로**: 진단 Alpine 에 `storcli64` 와 `sas3ircu` 를 동봉하고, `lspci -nn` 의 Vendor:Device 로 칩 계열을 판별해 명령 계열을 고른다. 두 계열을 다형(칩 계열 enum → 구성 명령 전략)으로 두면 세 번째 카드가 와도 분기가 늘지 않는다. Redfish 는 §2-3 실측 뒤 조회 보조로만.
- **파서**: `kindOf` 는 이미 SAS 클래스("Serial Attached SCSI controller")를 RAID 로 분류한다 — 추가 정정 불요(2026-08-28 확인).
- **자원 모델**: MA7 RAID 카드 자원의 "지원 RAID 레벨" · `minimumDisks` 는 칩 계열이 정본이다(캐시 유무는 파생 속성).

### 실측 항목(T3 적립 후보)
1. `GET /redfish/v1/Systems/Self/Storage` — 보드별(MD72-HB3 · MS03-CE0 …) 응답. BMC 웹 UI RAID 메뉴 유무.
2. Alpine 에서 `storcli64` · `sas3ircu` 실행(`file` 로 정적 링크 확인, 안 되면 `gcompat`).
3. `lspci -nn` — CRA3338 Subsystem ID · 클래스 표기, 9361-8i Subsystem.
4. `sas3ircu 0 display` 의 "RAID Support: Yes" 로 CRA3338 이 IR 펌웨어임을 확정.
5. RHEL 10 설치 미디어에서 두 드라이버 바인딩 확인.
6. IR 볼륨 생성 → 동기화 시간 · 리눅스 노출 형태(단일 sd 장치) 기록.

## 출처
- GIGABYTE CRA3338 제품 페이지 · 스펙(RAID 0/1/1E/10 IR, 볼륨 2, IR 미사용 시 512 장치): https://www.gigabyte.com/Enterprise/Accessory/CRA3338-rev-1x · https://www.gigabyte.com/Microsite/521/CRA3338.html
- Broadcom SAS3IRCU User Guide(명령 · 제약 · 지원 OS): https://docs.broadcom.com/doc/12353382
- Broadcom SAS3008 제품 브리프: https://docs.broadcom.com/docs/12351998
- Broadcom MegaRAID SAS 9361-8i 제품 브리프(캐시 1/2 GB · RAID 레벨 · VD 64 · 장치 128): https://docs.broadcom.com/doc/BC00-0478EN
- StorCLI 12Gb/s MegaRAID Tri-Mode User Guide: https://techdocs.broadcom.com/content/dam/broadcom/techdocs/data-center-solutions/tools/generated-pdfs/StorCLI-12Gbs-MegaRAID-Tri-Mode.pdf
- StorCLI 정적 링크(Debian 패키징 검토): http://thomas.goirand.fr/blog/?p=376
- 9300-8i 와 SAS3 HBA 의 CLI(StorCLI vs sas3ircu) — Proxmox 포럼: https://forum.proxmox.com/threads/command-line-tool-for-broadcom-sas-hba-9300-8i.59522/
- SAS3008 OEM 카드의 IT/IR 펌웨어 교차 flash(ServeTheHome · GitHub 가이드): https://www.servethehome.com/flash-lsi-sas-3008-hba-e-g-ibm-m1215-mode/ · https://github.com/CyrusDS/LSI-SAS3008-ITMODE
- Supermicro Redfish RAID 관리(3108 · 3008 · 3216 · 3616 지원): https://www.supermicro.com/en/solutions/management-software/redfish · https://www.supermicro.com/manuals/other/redfish-user-guide-4-0/Content/general-content/raid-management.htm
- AMI MegaRAC SP-X 데이터시트(Broadcom RAID = Technology Pack): https://9443417.fs1.hubspotusercontent-na1.net/hubfs/9443417/Data_Sheets/Firmware_Solutions/MegaRAC_SP-X_Data_Sheet_PUB.pdf
- Redfish 볼륨 생성 예(Lenovo XCC · Dell · 커뮤니티): https://pubs.lenovo.com/xcc-restapi/resource_volume_create_volume_post · https://jonamiki.com/2019/04/14/example-redfish-rest-calls-create-raid-volume/
- Broadcom 94xx Tri-Mode 어댑터 사용자 안내(PCIe 커넥터의 I2C 가 IPMI 버스에 연결): https://docs.broadcom.com/doc/pub-005851
- Alpine linux-lts 모듈 목록(mpt3sas · megaraid_sas): https://pkgs.alpinelinux.org/contents?file=mpt3sas.ko*&name=linux-lts&branch=v3.22&arch=x86_64
- RHEL 9 하드웨어 지원 고려사항 · Rocky 9 mpt3sas(SAS2008) 사례 · ELRepo 장치 ID 목록: https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/9/html/considerations_in_adopting_rhel_9/assembly_hardware-enablement_considerations-in-adopting-rhel-9 · https://forums.rockylinux.org/t/mpt3sas-does-not-work-with-rockylinux-9/6935 · https://elrepo.org/wiki/doku.php?id=deviceids
