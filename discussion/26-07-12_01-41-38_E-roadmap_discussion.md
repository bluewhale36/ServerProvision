> **문서 종류**: E 단계 discussion — 토론 · 브레인스토밍용 (CLAUDE.md "E 단계 — discussion 문서 규약" 의 첫 문서. plan/report html 을 대체하지 않는다.)
> **작성**: 2026-07-12 01:41 KST. 산출 방식 — multi-agent workflow: 4방향 코드 조사(현행 execution 골격 / legacy 실행 흐름 / 세팅 정의서 계약 / 인프라 · 통신) → 서로 다른 관점의 독립 설계 3안(엔진 코어 우선 / 수직 슬라이스 우선 / 리스크 · 미지수 우선) → 설계안별 적대 비평 2건 → 종합.
> **상태**: 초안 v1 — 사용자 피드백 대기. 이 파일은 서로 직접 수정하며 토론하는 문서다.
> **토론 방법 제안**: §5 토론 포인트(우선순위 순 10건)가 결정 대기 질문 목록이다. 각 항목 아래에 의견을 인라인으로 적어 주면(예: `> [답] ...`) 반영해 v2 로 갱신한다. §4 의 '토론 필요' 표기 결정도 동일. 로드맵의 하위 단계 구상은 모두 추후 변경 여지가 있는 초안이다 — 단, §6-2 의 명명 원칙(enum 상수는 첫 행 적재 후 rename 불가)만 예외.

---

# E 단계 로드맵 (초안 v1)

> 세 설계안(엔진 코어 우선 / 수직 슬라이스 우선 / 리스크 · 미지수 우선)과 각각의 적대 비평을 종합한 토론용 초안이다. 단일 승자를 고르지 않고 각 안의 강점을 접붙였으며, 채택하지 않은 대안과 탈락 사유를 §4 에, 사용자 결정이 필요한 열린 질문을 §5 에, 비평 mustFix 의 반영 여부 전수표를 §6 부록에 기록한다. 미확정 결정은 모두 '토론 필요' 로 표기했다.

## 1. 전제와 현재 골격 — U1/U2 가 남긴 것, E0 을 건너뛰는 이유, U3 의 위치

**U1(부트스트래핑)이 남긴 것.** 물리 서버가 iPXE 로 부팅해 `GET /api/pxe/v1/boot` 를 호출하면 서버가 멱등하게 등록된다(GuestServer / GuestServerDetail / HostNicBinding / ProvisioningProgress). 다만 현재 응답은 빈 200 이고, SetupStep 이력 테이블은 "행 적재는 엔진 책임" 으로 유보되어 항상 비어 있으며, GuestServerStatus 의 FAILED / PROVISIONED 두 상태는 도출 입력(실패 · 종단 신호)이 없어 도달 불가다. 즉 어휘(ProvisioningPhase / ProvisioningPhaseStep / ProvisioningStatus)와 저장 골격은 있으나, 그 어휘를 움직이는 주체 — 전이 엔진과 게스트 통신 프로토콜 — 가 비어 있다.

**U2(세팅 정의서)가 남긴 것.** SettingProcess.payload JSON 이 실행 입력의 SSOT(Single Source of Truth, 단일 진실 출처)로 확정됐고, 실행 경고 의미론도 말로는 확정됐다 — 펌웨어 파일 부재는 해당 축 skip + 부분 성공(비차단), ISO 소실 · 마커 무결성 실패는 실행 불가(차단). 이 규칙은 아직 코드가 아니며, E2 · E4 에서 처음 코드가 된다.

**E0(Redfish 조사)을 건너뛰는 이유.** E0 은 서버 구현과 별개의 조사 트랙으로 분리되어 있었고, 사용자가 E1 = DIAGNOSE_LINUX 순번을 지정했다. 건너뛴 공백이 가장 크게 노출되는 지점은 E3(FIRMWARE_SETTING)이므로, 이 로드맵은 E0 의 잔여를 E3 앞의 조사 트랙(E3-R)으로 흡수한다. 조사 트랙의 산출물은 CLAUDE.md 의 "E 단계 — discussion 문서 규약"(`discussion/*.md`)을 따른다 — 리스크 · 미지수 우선안이 자인했던 "조사 슬라이스와 CP 체계의 마찰" 은 이 규약이 이미 해소하도록 만들어져 있다.

**U3(정의서-서버 할당)의 위치.** 세 안과 여섯 비평이 모두 동일하게 확인한 코드 사실: SettingProcessType 4종(BASIC_UPDATE / BASIC_SETTING / OS_INSTALLATION / OS_SETTING)에 진단 phase 를 소비하는 계약이 없다. 즉 DIAGNOSE_LINUX 의 입력은 운영자 입력 필드와 하드웨어 사실뿐이므로 **E1 은 U3 없이 착수 가능하고, payload 가 유일한 입력이 되는 FIRMWARE_UPDATING(E2)부터 U3 가 엄격한 선행 의존이다.** 따라서 U3 를 E1 뒤 · E2 앞의 독립 슬라이스로 배치한다. 인벤토리 코드는 U3 를 그대로 유지한다(리스크안의 E2-0 재명명은 한 작업에 두 코드를 부여해 Notion 동기화 대상을 모호하게 하므로 탈락). 한 가지 표현 교정: 이 프로젝트는 단일 사용자 + 직렬 체크포인트 워크플로이므로 "E1 과 병행 가능" 은 실제로는 "순서만 바꿀 수 있다" 는 뜻이다 — 일정 착시를 피하기 위해 병행 표현은 쓰지 않는다.

**세 안에서 접붙인 것.** 엔진 코어 우선안에서는 "공통 규약(전이 · 원장 · 통신)을 phase 실행기보다 먼저 확정한다" 는 순서와 SPI(Service Provider Interface — 도메인이 구현해 끼우는 확장점) 구조를, 수직 슬라이스 우선안에서는 원장 적재 규약(이벤트 시점 append-only)과 "매 슬라이스가 체감 가능한 산출물을 갖게 하는" 분할 · U1 유보분 인수 아이디어를, 리스크 · 미지수 우선안에서는 조사 트랙의 명시 배치와 검증 Tier 선언(무엇을 모르는지 산출물에 남기기)을 취했다. 세 안이 독립적으로 동일 결론에 도달한 항목(게스트 pull 구동, 채널 이원화, 실패 · 종단 명시 컬럼, PARTIAL 도출, 수동 재시도, U3 배치)은 이 초안에서 사실상 확정 권고로 취급한다 — 최종 확정은 물론 사용자 승인 이후다.

## 2. 로드맵 한눈에 보기

조사 트랙(R 접미)은 discussion 문서가 산출물이며 코드 슬라이스가 아니다. 코드 부여 방식(`E*-R`)은 토론 필요(§5-7).

| 코드 | 명칭 | enum 체크포인트 | 하위 단계 | 주요 산출물 | 선행 의존 |
|---|---|---|---|---|---|
| E1-R | 조사: 진단 리눅스 이미지 + PXE 부트스트랩 인프라 | — | — | 이미지 선정 · 에이전트 배포 방식 · DHCP/TFTP/iPXE 바이너리 구성 · QEMU(T2)의 macOS 실행 가능성 discussion 문서 | 없음 (즉시 착수 가능, E1-1 의 게이트) |
| E1-0a | 엔진 코어(원장 · 전이 · 신호) | phase 무관 | — | ProvisioningProgress 전이 도메인 메서드 + 커서 의미론, SetupStep 적재 서비스 + U1 유보분(NETWORK_ALLOCATING · INIT_PERSISTING) 2행 실적재, failedAt/failedStepCode/completedAt DDL, GuestServerStatus.FAILED 분기 + 진리표 | U1 |
| E1-0b | 게스트 프로토콜 v1 | phase 무관 | — | `/boot` text/plain 전환 + 커서 dispatch(재부팅 매트릭스 v1), 체크인 · 보고 API(멱등 키 + 게스트 토큰), iPXE 오류 채널(한정 advice), 개시 게이트 규칙, 모의 게스트 하네스(T1) | E1-0a |
| E1-1 | 진단 리눅스 부팅 체인 | DIAGNOSE_LINUX | DIAGNOSTIC_BOOTING(신규) | 커널/initrd/에이전트 서빙, `pxe.server.*` 첫 소비, `server.address=localhost` 해제 절차 | E1-0b, E1-R |
| E1-2 | 진단 정보 수집 · 적재 | DIAGNOSE_LINUX | INFORMATION_COLLECTING, INFORMATION_PERSISTING | hardwareSpec/softwareSpec sealed record(`vo/`), GuestServerDetail 갱신 도메인 메서드, DIAGNOSTIC_ENRICHED 승급, BMC 신원 구조화 저장, 운영자 수동 FAILED 전환 · 재시도 액션 | E1-1 |
| E1-3 | IPMI 각인 · phase 완주 · 대기 | DIAGNOSE_LINUX | IPMI_SETTING | 각인 지시 · 보고, 완주 판정 + 전이, 할당 부재 대기 스크립트(sleep+chain 루프), 미입력 시 정상 skip + UI 사전 안내 | E1-2 |
| U3 | 세팅 정의서 할당 | — | — | 할당 엔티티 + 상세 UI, SettingDefinition 참조 차단 가드, 재할당 · 해제 의미론, 실행 중 변경 정책(§4-D9) | U2 (E2 의 엄격 게이트) |
| E2-R | 조사: 펌웨어 실행 메커니즘 | — | — | entrypoint(`f.nsh`) 실행 환경 확정(EFI Shell 체인 vs 리눅스 벤더 도구 vs Redfish) discussion 문서 | E1-R |
| E2-1 | 펌웨어 resolve 판정 | FIRMWARE_UPDATING | FIRMWARE_RESOLVING(신규) | 펌웨어 버전 Value Object, AUTO/LATEST resolve, 마커 무결성 게이트, 경고성 skip 판정 + statusMeta 기록 (T1 완결) | U3, E1-3 |
| E2-2 | 펌웨어 집행 | FIRMWARE_UPDATING | BIOS_UPDATING, BMC_UPDATING | 번들 서빙, 실행 지시 · 보고, flash 후 재부팅 루프 + 버전 재수집(softwareSpec 갱신), 집행 자체는 T3 유보 명기 | E2-1, E2-R |
| E3-R | 조사: Redfish 도달성 + BMC 자격증명 정책 | — | — | E0 잔여 흡수 — Redfish 세대 · `Bios/SD` 지원, 자격증명 부트스트랩 경로, 에뮬레이터(T2) 가능성 discussion 문서 | E1-2 |
| E3-0 | BMC 신원 · 자격증명 실체화 | phase 무관 | — | BMC binding · 자격증명 스키마(평문 금지), E1-2 수집분 연결 | E3-R, E1-2 |
| E3-1 | BIOS 설정 적용 | FIRMWARE_SETTING | BIOS_SETTING | 템플릿 resolve, Redfish PATCH 클라이언트(WebClient 첫 개봉), 적용 재부팅 + readback | E3-0, E2-2 |
| E3-2 | BMC 설정 적용 | FIRMWARE_SETTING | BMC_SETTING | payload 계약 신설부터(현재 계약 공백) — 최후순위 배치 가능(토론 필요) | E3-1 |
| E4-0 | 설치 소스 서빙 전략 | OS_INSTALLING | — | ISO loop-mount / 사전 추출 / 외부 미러 비교 결정 + 구현 | E1-0b |
| E4-1 | OS 설치 | OS_INSTALLING | OS_INSTALLING (판정 가시화 step 도입 여부는 CP1 판단) | Kickstart/autoinstall 렌더러 이식(이스케이프 계층 추가), `inst.ks`/nocloud-net 엔드포인트, ISO 무결성 실패 FAILED 차단, `%post` 완료 콜백. **E5 연결 방식을 이 CP1 에서 선확정** | U3, E4-0 |
| E5 | OS 후처리 | OS_SETTING | OS_SETTING | RHELOSSettingRequest 렌더 · 실행 · 보고, Ubuntu 계약 신설 동반 | E4-1 |
| E6 | 테스트 · 종단 | TESTING | TESTING | 기본 대조 검증(수집 재실행), completedAt 종단 신호, GuestServerStatus.PROVISIONED 분기 + 진리표. 설정 readback 검증은 E3 의존 항목으로 분리 | E5, U3, (readback 범위는 E3-1) |

phase 순서(DIAGNOSE_LINUX → … → TESTING)가 E 순번을 결정하고, 정의서에 해당 SettingProcessType 이 없는 phase 는 실행 시 통째로 통과된다(§4-D8) — 예컨대 정의서가 OS_INSTALLATION 만 가지면 E2 · E3 구현 후에도 해당 서버는 곧장 OS 설치로 간다.

## 3. 단계별 상세

### E1 — DIAGNOSE_LINUX (E1-R / E1-0a / E1-0b / E1-1 / E1-2 / E1-3)

**유스케이스.** 운영자가 서버를 등록하고 (개시 게이트 방식이 확정되면) 프로비저닝을 개시한다. 물리 서버가 재부팅해 `/boot` 를 호출하면 빈 200 대신 진단 리눅스 체인로드 iPXE 스크립트를 받는다. 진단 리눅스의 에이전트가 서버에 체크인하고(이 사실 신호가 BOOTSTRAPPING→DIAGNOSE_LINUX 전이 트리거), dmidecode 등으로 하드웨어 · 초기 펌웨어 버전 · BMC LAN 정보를 수집해 보고하며, 서버가 준 각인 지시에 따라 ipmitool 로 모델명 · 시리얼을 각인한다. 운영자는 상세 페이지에서 `'— (진단 단계에서 수집)'` placeholder 가 실값으로 바뀌고 step 이력이 쌓이는 것을 본다(UI 렌더 골격은 U1 이 완성 — 행만 적재되면 표시된다).

**E1-0a(엔진 코어).** 원래 엔진 코어 우선안의 E1-0 이 산출물 7종 이상으로 한 체크포인트 사이클을 초과한다는 비평(세 안 공통)을 수용해 2분할했다. E1-0a 는 서버 내부 규약만 다룬다: ProvisioningProgress 의 전이 도메인 메서드(phase 역행 금지 invariant + lastTransitionAt 명시 set), SetupStep 적재 서비스, 실패 · 종단 컬럼 DDL, GuestServerStatus.FAILED derive 분기. 체감 산출물 과장 비평에 대한 교정으로, 수직안의 아이디어를 채택한다 — **등록 트랜잭션이 U1 유보분인 NETWORK_ALLOCATING · INIT_PERSISTING 2행(SUCCEEDED)을 적재**한다. 등록은 systemUUID 존재 시 조기 반환하는 멱등 구조라 중복 적재가 없고, 이것이 E1-0a 의 실재하는 CP5 가시물이다(빈 이력 테이블이 처음 채워진다).

**E1-0b(게스트 프로토콜 v1).** `/boot` 를 text/plain iPXE 스크립트 응답으로 전환하고 커서 dispatch 규칙(§4-D2, D3)을 싣는다. 체크인 · 보고 API 를 신설하되 멱등 규약(보고는 대상 step 행 식별자에 바인딩 — 중복 POST 는 no-op)과 게스트 신원 토큰(부팅 응답에 발급, 이후 보고에 요구 — §4-D4)을 계약에 포함한다. iPXE 오류 채널은 Spring primitive 로 확정한다: PXE 부팅 계열 컨트롤러에 한정한 `@RestControllerAdvice`(assignableTypes 한정)가 낙관적 락 충돌을 포함한 전 예외를 text/plain 안전 스크립트(echo + sleep + chain 재시도)로 변환한다 — 전역 advice 의 JSON 409 가 iPXE 에 새어 나가 무한 부팅 루프가 되는 레거시 결함의 차단이며, 컨트롤러 try/catch 복붙 금지 원칙의 이행이다. 모의 게스트 하네스(T1 — curl 시퀀스)도 여기서 만든다.

**E1-1(부팅 체인).** DIAGNOSTIC_BOOTING(신규) step 은 "체인로드 스크립트 발급부터 에이전트 체크인까지가 성공했다" 는 사실을 체크인 수신 시점에 단발 기록한다 — 원래의 "진행 중 가시화" 목적은 이벤트 시점 적재 규약(§4-D5)과 시간 순서가 모순되어 포기하고, 사실 기록으로 재정의했다. 단계 도입 여부 자체는 추후 변경 여지가 있으나, 상수 이름은 이 슬라이스 CP1 에서 확정한다(첫 행 적재 후 rename 은 STRING 영속 이력의 역직렬화를 깨므로 불가 — §6). 선행 의존인 E1-R(이미지 선정 · 에이전트 배포 방식 · DHCP/TFTP/iPXE 바이너리 인프라)이 확정되지 않으면 착수 불가다 — 세 안 모두에서 지적된 이 로드맵 최대의 비 Java 미지수로, 로드맵 표의 1급 행으로 올렸다.

**E1-2(수집 · 적재).** 원래 리스크안의 E1-2(수집)/E1-3(영속) 분리는 수집 슬라이스 단독으로 관찰 가능한 결과가 없다는 비평을 수용해 병합했다. hardwareSpec/softwareSpec sealed record 는 `vo/` 에 배치하고(패키지 관례 준수 — `spec/` 신설안 탈락) Jackson 3 `tools.jackson.*` AttributeConverter 선례를 답습한다. **BMC LAN 정보(IP/MAC)는 hardwareSpec JSON 에 넣지 않고 이 시점에 구조화 저장을 결정한다** — JSON 임시 저장 후 E3 에서 엔티티로 이관하는 예정된 재작업(churn)을 피하기 위해서다(수직안 비평 수용). E3 의 입력을 미리 수집하는 것은 "미리 분리 금지" 원칙과 긴장하지만, 진단 리눅스 체류가 수집 기회가 있는 유일한 시점이고 소급 시 재부팅 비용이 실재한다는 실용 근거로 채택한다 — 확신 수위가 낮은 선반영임을 명기한다. 실패 보고가 최초로 발생 가능한 지점이므로 failedAt 실사용, 그리고 무보고 실패 대응(운영자 수동 FAILED 전환 액션 + lastTransitionAt 경과 표시)을 여기 배치한다.

**E1-3(각인 · 완주 · 대기).** 각인 지시 · 보고, phase 완주 판정과 전이. 운영자 입력(modelName/serialNumber — 둘 다 nullable) 미비 시의 처리는 개시 게이트 결정(§5-1)에 종속된다: 개시 게이트를 채택하면 게이트가 입력 충족을 요구하므로 이 케이스가 정상 흐름에서 사라지고, 자동 진입을 유지하면 정상 skip + UI 사전 안내가 된다. 완주 후 할당(U3)이 없으면 `/boot` 는 대기 스크립트를 준다 — **대기는 sanboot 가 아니라 iPXE sleep + chain 재시도 루프다.** OS 미설치 베어메탈에 sanboot 는 부팅 실패 루프가 되며(두 비평 공통 지적), sanboot(내지 UEFI 의 exit 부트오더 폴스루)는 OS 설치 완료 이후에만 안전하다. 할당 후 재개 트리거는 이 폴링 루프 자체다(다음 chain 재시도가 할당을 감지). "할당 없이 진단만 완주한 서버" 는 결함이 아니라 유효한 운영 상태(입고 검수)다.

**미지수 · 조사 항목.** 진단 배포판 선택(Alpine netboot / Debian live / buildroot — 에이전트는 이미지 내장보다 부팅 후 서버에서 curl 다운로드 우선: 이미지 재빌드 미지수 축소), ipmitool 각인 대상 FRU 필드의 벤더별 쓰기 가능성(실기 없이는 확정 불가 — T3 유보 명기), QEMU `-smbios` 주입의 T2 재현성과 macOS 실행 가능성.

### U3 — 세팅 정의서 할당

**유스케이스.** 운영자가 게스트 서버 상세에서 정의서를 선택 · 할당/해제한다. 할당이 생기는 순간 SettingDefinition 의 수정 · 삭제에 참조 차단 가드가 발동한다(Javadoc 이 U3 로 명시 유보한 항목의 이행). **재할당 · 해제 의미론을 산출물에 명시 포함한다** — 진행 중 재할당 · 해제는 UI 1차 차단(버튼 disabled + tooltip, 차단 조건은 도메인 메서드 1개로 SSOT 화) 대상 후보다. 실행 중 정의서 변경 정책(차단 가드 vs 스냅샷)은 §4-D9 — 토론 필요. 할당 스키마는 execution 애그리거트 소유, UI 소비는 provisioning 컨트롤러라는 기존 경계를 따른다.

### E2 — FIRMWARE_UPDATING (E2-R / E2-1 / E2-2)

**유스케이스.** 진단 완주 + 할당된 서버가 체크인하면 엔진이 BasicUpdateRequest 를 resolve 한다 — AUTO 보드는 GuestServerDetail.boardModel FK 읽기 1회(등록 시점에 확정됨 — 별도 감지 불필요), LATEST 는 enabled 펌웨어 중 최신. **버전 비교는 문자열 사전순이 아니라 펌웨어 버전 Value Object 의 비교 규약으로 한다** — "10.0" < "9.0" 오판은 잘못된 펌웨어 flash 라는 정확성 결함이고, 버전은 Primitive Obsession 금지 불가침의 명시 열거 대상이므로 세 안이 미지수로 유보했던 것을 여섯 비평 전부의 요구대로 확정 사항으로 승격한다(E2-1 CP1 이전, UI 정렬 질의와 동일 비교 규약 공유). enabled 펌웨어 0개 축은 SKIPPED(경고성 사유를 statusMeta 에 구조화 기록) + 비차단, 마커 무결성 실패는 `*IntegrityService.verifyIntegrity`(boardId, biosId 2-arg — resolve 선행 필요) 게이트로 FAILED 차단 — U2 가 말로 확정한 의미론이 처음 코드가 된다. FIRMWARE_RESOLVING(신규) step 이 "무엇이 왜 선택/skip 됐나" 를 원장에 남긴다.

**E2-2 의 현실 제약.** entrypoint(`f.nsh`)는 EFI Shell 스크립트라 진단 리눅스에서 직접 실행이 불가할 수 있다 — E2-R 조사가 실행 환경(EFI Shell 체인로드 / 리눅스 벤더 도구 / Redfish 업데이트)을 확정하기 전에는 집행 슬라이스를 시작하지 않는다. BIOS 버전은 재부팅 후에야 반영되므로 **flash → 재부팅 → 진단 리눅스 재진입 → 버전 재수집(softwareSpec 갱신)** 의 재부팅 루프가 E2-2 스코프다(§4-D3 매트릭스의 phase 내 재부팅 케이스). flash 집행 자체는 어떤 시뮬레이터로도 재현 불가 — 검증 상한 T1(계약) / T3(집행) 을 산출물에 명기한다. 펌웨어 step 은 실패 원인 미상 재시도가 벽돌 리스크이므로 운영자 재시도 버튼도 차단한다(tooltip 안내, 차단 조건 도메인 메서드 SSOT).

### E3 — FIRMWARE_SETTING (E3-R / E3-0 / E3-1 / E3-2)

**유스케이스.** 엔진이 BasicSettingRequest 의 템플릿을 resolve 하고(SPECIFIED = 1개 / AUTO = 감지 보드 FK 일치 1개 — 저장 시점 검사기가 중복을 차단해 결정 불능 없음), BiosSettingValues 를 무변환으로 Redfish `PATCH …/Bios/SD` 의 Attributes 에 싣는다(U2-2 가 구조 동형으로 설계한 이유의 실현). 적용 재부팅 후 readback 으로 확인한다.

**E3-R(E0 잔여 흡수).** 사내 보드의 Redfish 세대 · `Bios/SD` 지원, 대체 경로(in-band 벤더 도구) 존재 여부, Redfish 에뮬레이터/OpenBMC QEMU 의 T2 활용 가능성, 그리고 **BMC 자격증명 부트스트랩 경로** — Redfish 호출은 자격증명이 선행인데, 자격증명을 하드웨어에 설정할 in-band 기회(ipmitool user 설정)는 진단 리눅스 체류 중이 자연스럽다. E1 에 자격증명 설정 step 을 소급 추가할지, 공장 기본값 운영자 입력으로 갈지는 토론 필요(§5-5)다. E3-0 은 그 결정을 받아 BMC binding · 자격증명 스키마를 실체화한다(평문 컬럼 금지 — 레거시 답습 금지 항목).

**E3-2 의 계약 공백.** BASIC_SETTING payload 에는 BMC 설정 값의 출처가 없다. U2 계약 확장 / 고정 표준 설정 / step 유보 중 결정이 필요하며(§5-8), 최후순위 배치가 가능하다.

### E4 — OS_INSTALLING (E4-0 / E4-1)

**유스케이스.** 엔진이 isoId 를 resolve 하고 ISO 소실 · 비활성 · 마커 무결성 실패면 FAILED 차단(blocking=true 계약의 집행). 통과 시 `/boot` 가 레거시 검증 지식 그대로의 커널 인자(RHEL `inst.repo`+`inst.ks` / Ubuntu nocloud-net trailing slash · `---` 규약)를 반환하고, 게스트별 엔드포인트가 payload 로부터 Kickstart/autoinstall 을 렌더한다. 레거시의 Template Method 구조(RHELBasedInstallation 버전 훅) · RenderedScript/InstallScriptFormat · InstallationContext 경계는 이식하되, StringBuilder 무이스케이프 조립은 이스케이프 계층으로 교정한다. 설치 완료는 `%post` 말미의 curl 콜백이 명시 보고한다 — "재부팅 = 암묵 완료" 답습 금지 1호.

**E5 연결 방식의 선행 결정.** `%post` 병합(설치와 원자적) vs first-boot 에이전트(step 원장 · 재시도 분리 정합)의 결정은 E4 의 Kickstart 렌더러 훅 구조를 좌우하므로, 두 비평의 요구대로 **E4-1 CP1 의 선행 결정 사항으로 이동**한다(§5-6 — 토론 필요). E4 는 QEMU 로 가장 완전하게 재현 가능한 phase 이나, macOS 에서의 QEMU x86 에뮬레이션 속도가 미검증이므로 "T2 완전 검증 가능" 단정은 하지 않는다(E1-R 조사 항목). ISO 판정 가시화 step(ISO_VERIFYING 류)의 도입 여부는 FIRMWARE_RESOLVING 운영 경험을 보고 E4-1 CP1 에서 판단한다 — 선제 세분화를 피한다.

### E5 — OS_SETTING

**유스케이스.** E4 CP1 에서 확정된 연결 방식에 따라 RHELOSSettingRequest(SELinux/services/additionalPackages)를 실행 지시로 변환하고 별도 step 으로 보고한다 — 레거시에서 완성 · 테스트되고도 실행 경로에 연결되지 않아 죽은 코드였던 `%post` 생성 로직의 교훈은 "생성기에 물리적으로 배선 + 별도 보고" 로 해소한다. Ubuntu OS_SETTING 은 해석기 맵에 없어(현재 전송 시 400) payload 계약 신설이 동반된다. **Subprogram(드라이버 · 유틸) 자원은 이번 로드맵 전체에서 소비처가 없다** — 의도적 out of scope 로 명시하며, 자연스러운 소비처(OS 후처리 배포)가 생기면 계약 확장으로 다룬다.

### E6 — TESTING · 종단

**유스케이스.** 프로비저닝된 OS(또는 first-boot 에이전트)가 최종 검증을 수행하고, 성공 시 completedAt 종단 신호가 기록되어 GuestServerStatus.PROVISIONED 가 최초 도달한다 — U1 이 배지 마크업까지 준비해 둔 도달 불가 상태 2개가 모두 살아나는 종착점. 검증 범위는 2단으로 나눈다: **기본 대조**(E1 수집 재실행으로 확인 가능한 것 — 각인 값 · 펌웨어 버전 · OS 버전, 선행 의존 U3 · E1)와 **설정 readback 대조**(BIOS setup 값은 dmidecode 로 읽을 수 없어 Redfish GET 필요 — 선행 의존 E3-1). 비평이 지적한 "수집 능력 과대평가" 를 이 분리로 교정한다. 검증 계약은 정의서에 없으므로(SettingProcessType 에 TESTING 부재) 내장 대조 검증의 최소 정의로 시작한다 — 사용자 정의 테스트로의 계약 확장은 후순위(§5-9). 종단 기록용 별도 step(RESULT_FINALIZING 류)은 두지 않는다 — completedAt 컬럼이 이미 그 사실을 표현하므로 동일 사실의 이중 표현이다.

## 4. Cross-cutting 설계 결정

**D1. 엔진 구동 모델 — 게스트 구동 pull + 명시 보고 (세 안 합의, 확정 권고).** 게스트의 HTTP 요청(부팅 · 체크인 · 보고)만 상태를 전진시키고 서버는 판정 · 기록한다. 상태가 전부 DB(커서 + 원장)에 있어 재시작 생존성이 구조적으로 확보되고, PXE 의 본질과 방향이 일치하며, 모의 게스트가 curl 만으로 전 구간을 리허설할 수 있다. — 탈락: 서버 push(SSH/`@Async` 오케스트레이터) — in-memory 실행 컨텍스트 생존성 설계 선불 + SSH 키 배포 · 타이밍 레이스라는 신규 미지수. 탈락: long-poll 작업 큐 — 부팅 사이클과 부정합. 단 E3 의 Redfish 처럼 아웃바운드가 본질인 작업은 phase 실행기 내부 구현으로 허용한다(구동 트리거는 여전히 게스트 체크인).

**D2. 커서 의미론과 전이 시점 (비평 수용, 신규 확정 대상).** 세 안 모두에 없던 공백. 이 초안의 제안: **currentPhase 커서 = "현재 진행 중(또는 진입 대기 중)인 phase"**, 전이는 **게스트의 사실 신호(체크인 · 마지막 step 종료 보고)를 수신한 트랜잭션 내에서 즉시** 수행한다 — 보고가 곧 게스트 명시 신호이므로 "명시 신호만 전진" 원칙과 정합하고, `/boot` 는 상태를 바꾸지 않는 읽기 전용 dispatch 로 남아 멱등이 유지된다. `/boot` 응답의 입력은 커서 단독이 아니라 (개시 여부 × 커서 × 해당 phase 원장 상태 × 할당 존재 × failedAt/completedAt) 다 — "커서의 순수 함수" 라는 원안 서술을 실제 입력 집합으로 교정한다. — 탈락: 스크립트 발급(의도) 시점 전이 — 발급 후 게스트 미도달 시 커서가 사실과 어긋난다. E1-0a/E1-0b CP1 의 최우선 확정 항목.

**D3. 재부팅 경계 매트릭스와 폴백 이분 (비평 수용, 1급 결정 승격).** 전체 파이프라인에서 게스트는 여러 번 재부팅하고 매번 `/boot` 로 재진입한다. phase 내 재부팅(BIOS flash 적용, BIOS 설정 적용)은 원장의 미종결 step 이 dispatch 입력이 되므로 phase 커서만으로는 부족하다. 부팅 응답 전수 매트릭스(부록 §6-4 초안)를 E1-0b 가 v1 으로 소유하고, 각 phase 슬라이스가 자기 행을 추가한다 — 추가 방식은 분기문 증식이 아니라 phase 실행기 SPI 의 부팅 스크립트 기여 메서드다(조건분기 확장 금지의 적용). 폴백은 이분한다: **OS 설치 완료 전 대기 = iPXE sleep + chain 재시도 루프 / OS 설치 완료 후 = 로컬 부팅**(sanboot 는 UEFI 지원이 제한적이라 exit 부트오더 폴스루 대안 포함 T2 검증 항목). — 탈락: 단일 sanboot 폴백 — OS 미설치 베어메탈에서 부팅 실패 루프.

**D4. 게스트 통신 채널 — 이원화 + 예외 변환 메커니즘 + 신원 토큰 + 멱등 (세 안 합의 + 비평 보강).** 부팅 계열(iPXE)은 text/plain 스크립트, 에이전트 계열은 기존 ApiExceptionHandler JSON 체계 재사용 — 오류 형식의 이원화는 의도된 계약으로 명문화한다. 예외→안전 스크립트 변환은 PXE 부팅 컨트롤러 한정 `@RestControllerAdvice`(낙관적 락 충돌 포함 전 예외 → sleep+chain 재시도 스크립트)로 확정 — 기존 두 advice 의 HIGHEST_PRECEDENCE + Accept 협상 구조와의 공존 방식(assignableTypes/basePackages 한정)을 E1-0b CP1 에서 검증한다. 컨트롤러 try/catch 복붙 경로를 봉쇄하는 것이 목적이다. **게스트 신원**: 무인증 체크인은 사칭 보고로 타 서버의 진행 상태를 오염시킬 수 있다("외부변조 = 진짜 예외" 원칙의 직접 대상) — 부팅 응답에 게스트별 토큰을 발급하고 이후 보고에 요구한다. 내부 프로비저닝망 전제는 전제대로 명시 기록한다(§5-10). **멱등**: 보고는 대상 step 행 식별자에 바인딩 — 동일 보고 재전송은 no-op, 재시도에 의한 새 행 append 와 구별된다. 에이전트(쉘 스크립트)의 409/5xx 재시도 규약(단순 백오프 재시도)을 계약 문서에 포함한다.

**D5. SetupStep 원장 규약 — 이벤트 시점 append-only (수직안 채택).** 게스트 실행 step 은 시작 보고 시 RUNNING 생성 → 종료 보고 시 SUCCEEDED/FAILED/SKIPPED + statusMeta, 서버 측 판정 step(FIRMWARE_RESOLVING 류)은 판정 즉시 단발 적재. 행 삭제 · 재작성 금지, 재시도는 새 행 append. 현재 상태 = stepCode 별 최신 행. — 탈락: phase 진입 시 PENDING 일괄 선적재(엔진 코어안 · 리스크안) — ① AUTO/LATEST 의 skip 이 실행 시점에야 판정되는 것과 모순, ② payload/enum 에서 파생 가능한 실행 계획의 이중 저장(레거시 stepOrder drift 와 동형), ③ DIAGNOSTIC_BOOTING 처럼 phase 진입 이전 구간을 표현하는 step 과 시간 순서 모순. 선적재의 장점이던 "남은 작업 표시" 는 UI 가 enum 정의에서 정적으로 도출해 표시하는 것으로 대체 가능하다(저장 없이). — 탈락: 완전 이벤트 소싱 — startedAt/finishedAt nullable 인 현 엔티티 구조와 불일치, 조회 복잡도 상승.

**D6. 실패 · 종단 · 부분성공 · 재시도 (세 안 합의 + 비평 보강).** 실패 · 종단은 ProvisioningProgress 의 명시 컬럼(failedAt + failedStepCode(ProvisioningPhaseStep 타입) / completedAt)으로 — GuestServerStatus.derive 가 순수 함수로 유지되고 실패 phase 는 커서가 그대로 가리킨다. derive 우선순위는 회수(decommission) > 실패 > 완료 > phase 진행 순으로 진리표에 명시하고, failedAt · completedAt 동시 set 이라는 표현 가능한 무효 상태는 도메인 메서드가 상호 배타로 차단한다. — 탈락: ProvisioningPhase 에 종단값 추가(커서 의미 오염), phaseMeta JSON 은닉(derive 불투명). **부분성공**은 SKIPPED 행에서 도출하되, 비평이 지적한 의미 뭉개짐을 교정한다 — statusMeta sealed record 에 skip 사유 분류(경고성: 펌웨어 파일 부재 / 정상: 미적용 축 · 미입력)를 넣고 **경고성 skip 만 부분성공 도출 입력**으로 삼는다. 노출은 상세 화면 한정(단건 조회라 N+1 무관)으로 확정하고, 목록 노출 요구가 실재하는 시점에 명시 컬럼 승격 경로를 밟는다. — 탈락: ProvisioningStatus 에 PARTIAL_SUCCESS 값 추가 — 동일 사실 이중 표현. **재시도**는 자동 없음 — FAILED 는 커서를 멈추고 운영자 명시 액션(상세 페이지 재시도 버튼, FAILED 상태에서만 활성)이 새 행으로 재개한다. 펌웨어 step 은 재시도 버튼도 차단(벽돌 리스크). **무보고 실패**(게스트 침묵 — 커널 패닉 · 전원 단절)는 lastTransitionAt 경과 표시 + 운영자 수동 FAILED 전환 액션으로 시작하고, 자동 타임아웃 워치독은 실측 후로 명시 유보한다.

**D7. phase 실행기 SPI — 최소 폭 단일 인터페이스 + 부분 등록 registry.** phase 판별자를 가진 Spring 빈들을 registry 가 수집한다(MarkableScanner 선례와 동형의 기동 시 수집). 단 비평이 지적한 "점진 배송과 완전성 검증의 모순" 을 수용해 검증 의미론을 재정의한다: **전체 enum 커버를 강제하지 않고, 커서가 미구현 phase 에 도달하면 명시적 대기 스크립트(HOLD — FAILED 아님)를 반환**한다. 미커버가 조용히 통과되는 것(레거시 silent skip)만 기동 시점에 차단한다(등록 phase 집합 로깅 + 미등록 도달 시 HOLD 는 명시 동작). 인터페이스는 최소 폭(부팅 스크립트 기여 + 보고 처리)의 **단일 인터페이스로 시작**하고, "부팅 스크립트가 불필요한 phase" 라는 두 번째 실물이 나타나는 시점(E5)에 분리를 재검토한다 — 구현체 0개 시점의 ISP(Interface Segregation Principle) 2분리는 "미리 분리 금지" 원칙에 따라 탈락. `appliesTo(정의서 보유 여부)` 질의도 소비자가 생기는 E2 에서 시그니처에 추가한다. — 탈락: enum method-per-constant(DI 불가 — repository · 마커 서비스 접근 필요), sealed interface + exhaustive switch(Spring 빈 현실과 부정합).

**D8. phase 순서 SSOT — ProvisioningPhase 선언 순 (비평 수용 교정).** 다음 phase 결정 = ProvisioningPhase 선언 순 위에서 "할당 정의서가 해당 phase 의 process 를 보유하는가" 필터를 적용. SettingProcessType 은 phase 매핑만 제공한다. — 탈락: SettingProcessType 선언 순(수직안 원안) — 관리자 폼 표시 순서 재정렬이 실행 순서를 조용히 바꾸는 취약 결합이고, SettingProcessType 자신의 Javadoc("실행 단계 모델 SSOT 는 execution enum")과도 모순.

**D9. U3 실행 중 정의서 변경 — 참조 차단 가드 우선 권고 (토론 필요).** 진행 중 할당이 존재하는 정의서의 수정 · 삭제를 차단(UI tooltip 안내 + 서버 가드, 조건은 도메인 메서드 SSOT). 근거: 단순 구현 우선이라는 본 동기, SSOT 이중화 회피, Javadoc 예고 방향과 일치. — 대안(기록 유지): 첫 소비 phase 진입 시 payload 스냅샷 복사 — 실행 결정론이 더 강하고 정의서 재사용성이 유지되나, point-in-time 복사본과 원본의 관계 관리(의도적 분기 vs 우발적 drift 의 구별)가 새 복잡도를 만든다. 장시간 프로비저닝 중 템플릿 편집 불가라는 차단 가드의 비용이 실제 운영에서 수용 가능한지가 판단 기준 — U3 CP1 에서 최종 확정한다.

**D10. 검증 Tier 와 조사 트랙 (리스크안 채택 + 규약 정리).** T0 = `@WebMvcTest` 계약 테스트(기존 4범주 규율 그대로 — 2xx 는 스크립트 바디 문자열까지) / T1 = 모의 게스트 하네스(curl 시퀀스 — Step 8 테스트 규율을 대체하지 않는 수동 검증 자산으로 위치 확정) / T2 = QEMU PXE 랩(macOS 실행 가능성은 E1-R 조사 항목 — 미검증 전제를 척추로 선언하지 않는다) / T3 = 실기(사용자 단독). 각 CP4 보고에 그 슬라이스의 검증 상한 Tier 를 명기한다 — 이 규약의 성문화 위치(CLAUDE.md vs 로드맵 문서)는 토론 필요. 조사 트랙 산출물은 CLAUDE.md 의 discussion 문서 규약을 따른다(코드 시제가 필요한 조사는 조사=문서 / 구현=10단계로 분리). "계약만 구현 · 집행 T3 유보" 패턴이 죽은 코드의 재판이 되지 않도록, **T3 실기 검증의 시점 합의를 각 유보 슬라이스의 Notion 후속 마일스톤 항목으로 강제 기재**한다.

**D11. 펌웨어 버전 Value Object — 확정 (여섯 비평 전부 수용).** 버전은 Primitive Obsession 금지의 명시 열거 대상이고, LATEST resolve 가 비교에 직접 의존한다. E2-1 CP1 이전에 비교 규약 포함 Value Object 로 도입하고, UI 정렬과 실행 resolve 가 같은 규약을 공유한다. "도입 여부 미지수" 표기는 세 안 모두에서 철회한다.

## 5. 토론 포인트 (우선순위 순)

1. **프로비저닝 개시 게이트.** 등록 직후 자동으로 진단에 진입하면 운영자 입력(modelName/serialNumber — nullable) 이전에 IPMI 각인 시점이 와서 null 각인 또는 기회 상실이 생긴다. 선택지: (a) 명시 개시 액션 — 상세 페이지 "프로비저닝 개시" 버튼, 입력 미비 시 disabled + tooltip(UI 1차 차단 원칙 정합), 개시 전 `/boot` 는 대기 스크립트 / (b) 자동 진입 + IPMI_SETTING 정상 skip + 사전 안내. 초안 권고는 (a) — 운영자 의도가 명시적이어서 "명확한 사용성" 에 부합한다. 채택 시 개시 표식 저장 위치(ProvisioningProgress 컬럼)가 E1-0a DDL 에 동승한다.

> modelName 과 serialNumber 는 처음에는 진단 리눅스 단계에서 ipmitool 을 이용하여 추가하는 것으로 계획했으나, 해당 프로그램의 실 사용처 공정 특성 상 provisioning 초기에 이 값을 입력할 수 없는 구조.
> provisioning 이 완료된 이후 별도 액션을 통해 'model serial 기록' 을 독립적으로 수행할 수 있도록 단계 및 로직 분리 필요.
> 다만 이 시점에서도 진단 리눅스 (alpine linux)를 메모리에 적재하여 돌리는 것은 설계에 변함이 없다.

2. **CP5 의 E 단계 재정의.** 엔진 슬라이스의 진짜 검증은 브라우저가 아니라 게스트 프로토콜이다. 모의 게스트 curl 시퀀스(+ 상세 페이지 확인)를 E 슬라이스의 CP5 절차로 인정할지 — 이는 CLAUDE.md 불가침 프로세스의 변경이므로 사용자 합의 + CLAUDE.md 명문화(E 단계 예외 조항)가 선행돼야 한다. 하네스 저장 위치(`tools/` 또는 `scripts/` 신설)도 신규 관례라 합의 대상.

> E 단계의 CP5 예외 조항 승인.
> 하네스 부분은 구체적으로 어떤 것을 의미하는지 추가 설명 요망.

3. **진단 리눅스 이미지 · PXE 인프라(E1-R).** 이미지의 제작 · 관리 방식(기성 라이브 배포판 vs 커스텀 빌드, Management 자원으로 등록해 마커 · lifecycle 대상으로 삼을지 여부 — 자원화면 MA* 급 작업 추가), DHCP/TFTP/iPXE 바이너리 인프라의 소유(앱 외부 구성 문서 vs 앱 내 통합). E1-1 의 착수 게이트이므로 로드맵에서 가장 먼저 열어야 할 질문이다.

> 진단 리눅스는 alpine linux 를 커스텀 빌드하여 사용한다. `dmidecode`, `ipmitool` 등이 미리 가용한 상태에 있어야 하기 때문. 이를 마커와 lifecycle 대상으로 분류하여 관리하는 것은 적절해 보이나, 삭제 또는 신규 생성이 제한되어야 하고 접근이 상대적으로 어려워야 하므로 단순히 MA 단계에서 진행했던 자원 도메인 내에서 동일하게 관리하는 주체로서는 부적절한 상황.
> DHCP/TFTP/iPXE 바이너리의 경우 최초의 계획으로는 Provisioning Server 프로그램을 배포하는 서버 내에 별도로 인프라를 구축하려고 했으나, 두 개의 관리 포인트가 생김으로 인하여 발생하는 유지보수의 어려움은 병목. Notion 의 U1 페이지 내용은 최초 부팅 시 DHCP부터 Provisioning Server까지 어떤 과정을 거쳐 Guest Server가 등록되는지를 설명하고 있으며, DHCP/TFTP/iPXE 바이너리의 인프라 구축 과정은 notion Provisioning Server 페이지의 'PXE Server 구축' 하위 페이지에 기술되어 있음.
> 만일 이러한 인프라가 배포하는 Rocky 9.x 리눅스 위에 별도로 존재하는 것이 아닌 Provisioning Server 내에서 관리가 가능하다면, 해당 방향으로의 인프라 구축을 희망한다. 다만 이 경우 이러한 인프라를 관리할 수 있는 html 페이지가 별도로 생겨야 하므로 상기 단계별 로드맵에서 일부 분기 또는 신설되어 별도 관리 페이지 구축의 단계를 진행할 필요가 있음.

4. **U3 실행 중 정의서 변경 정책** — §4-D9 의 차단 가드 vs 스냅샷. 판단 기준: 장시간 실행 중 템플릿 편집 불가가 운영상 수용 가능한가.

> 스냅샷으로 진행. 다만 이 경우 DB table 별도 생성 및 관리 필요할 것으로 전망.

5. **BMC 자격증명 부트스트랩 경로.** in-band 설정 기회는 진단 리눅스 체류 중이 유일하게 자연스럽다 — E1 에 자격증명 설정 step 을 둘지(E1-3 결합), 공장 기본값 운영자 입력으로 갈지, 보관 방식(로컬 암호화 vs 외부 참조)은 무엇인지. E3-R 의 핵심 질문이지만 E1 스코프에 소급 영향이 있어 미리 방향만이라도 합의가 필요하다.

> BMC 의 최초 자격증명은 진단 리눅스에서 M/B 의 serial number 를 `dmidecode` 로 수집하여 실행 가능. 이후 redfish api 를 통해 비밀번호 변경 작업이 1회 수행되어야 하며, 변경되는 비밀번호는 항상 고정이므로 운영자 입력 대기 등은 불필요한 상황.

6. **E5 연결 방식** — Kickstart `%post` 병합(설치와 원자적, 단순) vs first-boot 에이전트(step 원장 · 재설치 없는 재시도 정합). E4-1 CP1 전 확정 필수(렌더러 훅 구조를 좌우). 초안은 phase 어휘가 OS_SETTING 을 별도 단계로 확정해 둔 점에서 first-boot 쪽으로 기울지만, 구현 단순성은 병합이 우세 — 토론 필요.

> 추가 설명 요망.
> 현재 나는 guest server 최초 부팅 직후 provisioning server 에 등록되는 과정에서만 guest-pull 방식의 실행 엔진이 그려질 뿐, 이후의 과정에서는 어떻게 해야 할지 감이 잡히지 않는 상황.

7. **조사 트랙의 인벤토리 코드.** `E*-R` 접미 제안 — Notion DB 에 조사 트랙 행을 신설할지, 기존 E 슬라이스의 본문 항목으로만 둘지(임의 신설 금지 규약과의 정합).

> 승인.

8. **E3-2 BMC_SETTING 계약 스코프** — U2 계약 확장 vs 고정 표준 설정 vs step 유보. 최후순위 배치 허용 여부 포함.

 > 현재 BMC의 세팅 값 변경을 위해 URI 조사가 선행되어야 하나 이 부분에서 어려움을 겪고 있는 상황. 추후 Claude Desktop App 내 신규 기능인 Browser 를 이용하여 URI 수집을 진행할 예정.
 > 또는 가능하다면, gigabyte 사의 메인보드 4종 (MS03-CE0, MS73-HB1, MS74-HB0, MS04-CE0)에 대한 bmc redifsh api 를 통한 자동화 또는 provisioning 선례를 조사를 진행해볼 것.

9. **E6 검증 계약** — 내장 대조 검증 최소 정의로 시작(초안 권고, 본 동기 정합) vs 정의서 계약 확장(TESTING payload 신설).

> Testing 은 Windows OS 가 Provisioning 대상 OS 로 구현되었을 때 사용하는 단계. 현재로써는 구현을 보류한다.

10. **게스트 채널 보안 수준의 명시 확정** — 내부 프로비저닝망 전제 + 부팅 발급 토큰(D4)까지로 할지, `inst.ks`(root 패스워드 포함) 서빙의 추가 보호를 둘지.

> Provisioning Server 웹페이지는 사내망을 이용하여 접속 가능하도록 배포할 예정.
> 다만 Provisioning Server 자체는 LAN 선을 연결해두고, 사내 와이파이를 이용하여 접속할 수 있도록 하는 방식이 가능한지 불분명. `192.168.0.x` 대에 배포하면 가능하지 않을까 하는 생각은 있으나, 나는 네트워크 쪽으로는 아는 바가 없다.

## 6. 부록: 기술 상세

### 6-1. 코드 영역 매핑 (패키지 수준)

- `execution/engine/` (신설): 전이 서비스, SetupStep 적재 서비스, phase 실행기 SPI + registry, resolve 판정(E2 하위 `resolve/`).
- `execution/script/` (신설): iPXE · Kickstart/autoinstall 렌더(E4 하위 `os/` — 레거시 Template Method 이식 + 이스케이프 계층).
- `execution/controller/`: `/boot` text/plain 전환, 체크인 · 보고 API, 자산(에이전트 · 번들 · install script) 서빙, PXE 한정 advice.
- `execution/dto/` / `execution/vo/`: 보고 수신 Request, hardwareSpec/softwareSpec sealed record + `tools.jackson.*` AttributeConverter, statusMeta sealed record, 펌웨어 버전 Value Object(관리 도메인과 공유 위치는 E2-1 CP1 결정), 게스트 토큰.
- `execution/entity/`: ProvisioningProgress 컬럼 추가(failedAt/failedStepCode/completedAt/개시 표식), GuestServerDetail 갱신 메서드, 할당 엔티티(U3), BMC binding(E3-0).
- `provisioning/`: 할당 UI(controller+템플릿), `setting/service/` 참조 차단 가드.
- `management/bios · bmc/`: IntegrityService · 질의 재사용(수정 최소), 버전 VO 비교 규약 공유.
- `global/`: PathPolicyService 경유 파일 서빙, 기존 advice 와 PXE advice 공존 구성.
- `sql/`: 수동 DDL(ALTER 권한 계정 필요 — `claude_code` 불가). 스키마 변경은 1회가 아니라 최소 4곳(E1-0a 컬럼 / E1-2 BMC 신원 / U3 할당 / E3-0 자격증명)임을 정직하게 기록한다 — "스키마 변경 1회 집중" 주장(엔진 코어안)은 채택하지 않는다.

### 6-2. enum 확장 필요분

- ProvisioningPhaseStep 신규: `DIAGNOSTIC_BOOTING`(E1-1), `FIRMWARE_RESOLVING`(E2-1) — 모두 step_code varchar(25) 수용, phase 파생 매핑 추가만. 도입 유보: ISO 판정 가시화 step(E4-1 CP1 판단). 도입 안 함: 종단 기록 step(completedAt 이 표현 — 이중 표현 회피). **명명 원칙**: 상수 이름은 해당 슬라이스 CP1 에서 확정하고 첫 SetupStep 행 적재 후 rename 불가(STRING 영속 이력의 역직렬화 파손) — "추후 변경 여지" 는 단계의 도입 여부 · 의미 범위에만 적용되고 이름에는 적용되지 않는다. 약어 명명(DIAG_ 류) 금지.
- ProvisioningStatus: 확장 없음(SKIPPED 기존재, PARTIAL_SUCCESS 값 추가 안 함).
- ProvisioningPhase: 확장 없음(종단 · 실패 값 추가 안 함 — 명시 컬럼).
- GuestServerStatus: 값 추가 없음, derive 분기 확장 2회(FAILED = E1-2, PROVISIONED = E6) + 진리표 테스트 확장.

### 6-3. 테스트 · 시뮬레이션 전략

- Step 8: 기존 불가침 규율 그대로 — 단위(Service 분기별 happy 1 + 실패 1) + `@WebMvcTest` 통합(2xx 스크립트 바디 문자열 / 400 / 409 낙관적 락 포함 / 404 forging 포함). 부팅→체크인→보고→전이 시퀀스를 HTTP 계층에서 체인 검증. 신설 예외마다 트리거 시나리오 동반.
- T1 모의 게스트 하네스: curl 시퀀스 — Step 8 을 대체하지 않는 수동 리허설 자산. CP5 절차 인정 여부는 §5-2.
- T2 QEMU 랩: `-smbios` 주입으로 등록 경로 재현, E4 설치 E2E, ipmi_sim/Redfish 에뮬레이터 — macOS 실행 가능성 자체가 E1-R 조사 항목(미검증 전제를 신뢰 기반으로 선언하지 않음).
- T3 실기: 사용자 단독. flash 집행 · FRU 각인 · Redfish 실보드는 T3 전용 — 유보 슬라이스마다 실기 검증 시점을 Notion 후속 마일스톤에 기재.
- CP1 plan html: 인터랙티브 상태기계 시뮬레이터(전이 · skip · 차단 · 경합 체험)로 규칙 결함을 코드 전에 검출 — 기존 불가침 규약 그대로.

### 6-4. `/boot` 응답 매트릭스 초안 (E1-0b 가 v1 소유, phase 슬라이스가 자기 행 추가)

| 상태 | 응답 |
|---|---|
| 미등록 | 등록(멱등) + 대기 스크립트(개시 전) |
| 등록 + 미개시(게이트 채택 시) 또는 입력 미비 | sleep + chain 재시도 대기 스크립트 |
| 개시 + DIAGNOSE_LINUX 미완주 | 진단 리눅스 체인로드 |
| DIAGNOSE_LINUX 완주 + 할당 없음 | 대기 스크립트(폴링이 할당 감지 시 자동 재개) |
| FIRMWARE_UPDATING 진행 중(flash 후 재부팅 포함) | 원장 미종결 축 기준 재진입 스크립트(실행 환경은 E2-R 결과) |
| FIRMWARE_SETTING 적용 재부팅 | readback 경로 재진입 |
| OS_INSTALLING | 설치 커널 + inst.ks/nocloud-net 인자 |
| OS_INSTALLING 완료 이후 | 로컬 부팅(UEFI exit 폴스루 — sanboot 실효성 T2 검증) |
| failedAt set | 대기 스크립트(운영자 재시도 대기) |
| 미구현 phase 도달 | HOLD 대기 스크립트(silent 통과 금지) |

### 6-5. 비평 mustFix 반영표

| mustFix (중복 통합) | 처리 |
|---|---|
| 기동 시 완전성 검증 vs 점진 배송 모순 | 반영 — 부분 등록 registry + 미구현 phase HOLD 대기(§4-D7) |
| DIAGNOSTIC_BOOTING 과 선적재의 시간 모순 | 반영 — 이벤트 시점 적재 채택으로 모순 소멸, step 을 사실 기록으로 재정의(§3-E1-1, §4-D5) |
| iPXE 예외→안전 스크립트 메커니즘 확정 | 반영 — PXE 한정 `@RestControllerAdvice`, 낙관적 락 포함(§4-D4) |
| E1-0 체감 산출물 과장 교정 + 분할 | 반영 — 2분할 + U1 유보분 2행 적재가 실재 가시물(§3-E1-0a) |
| 보고 API 멱등 · 중복 규약 | 반영 — step 행 식별자 바인딩 + 에이전트 재시도 규약(§4-D4) |
| 버전 VO 확정 | 반영 — §4-D11, E2-1 CP1 이전 |
| 운영자 개시 게이트 | 반영 — 토론 포인트 1순위 + (a)안 권고, E1-0 스코프 배정(§5-1) |
| 커서 의미론 · 전이 주체/시점 | 반영 — §4-D2 (E1-0 CP1 최우선 확정 항목) |
| 폴백 이분(sanboot 부팅 실패 루프) | 반영 — §4-D3 |
| 무보고 실패(RUNNING 고착) | 반영 — 수동 FAILED 전환 + 경과 표시, 워치독 명시 유보(§4-D6) |
| "스키마 1회 집중" 교정 | 반영 — 최소 4곳 명기(§6-1) |
| E5 연결 방식을 E4 CP1 로 이동 | 반영 — §3-E4, §5-6 |
| 진단 이미지 · DHCP/TFTP 인프라의 표 편입 | 반영 — E1-R 을 1급 행으로(§2) |
| phase 순서 SSOT 교정 | 반영 — §4-D8 |
| 신규 상수 '변경 여지' 표기 | 부분 반영 — 이름은 첫 행 적재 전 확정(rename 불가 근거 명기), '여지' 는 도입 여부에 한정. 전면 제거하지 않는 이유: 본 임무의 문서 규약이 신규 구상 단계에 변경 여지 명시를 요구하므로 적용 범위를 분리해 둘을 양립시켰다(§6-2) |
| PARTIAL 노출 위치 확정 | 반영 — 상세 한정 + 승격 경로 기록(§4-D6) |
| CP5 재정의 = CLAUDE.md 변경 합의 명시 | 반영 — §5-2 |
| 재부팅 경계 매트릭스 1급 승격 | 반영 — §4-D3, §6-4 |
| 할당 대기 의미론 · 재개 트리거 | 반영 — §3-E1-3 (폴링 루프가 재개 트리거) |
| BMC 자격증명 부트스트랩 로드맵 승격 | 반영 — E3-R + §5-5 |
| BMC 신원 저장 위치 E1-2 확정(구조화) | 반영 — §3-E1-2 |
| SKIPPED 사유 구분(경고/정상) | 반영 — statusMeta 구조화, 경고성만 부분성공 입력(§4-D6) |
| E1-0 분할 + E1-2/E1-3 경계 재설정 | 반영 — E1-0a/E1-0b 분할, 수집 · 영속 병합(§2) |
| 조사 트랙 = discussion 문서 규약 매핑 | 반영 — CLAUDE.md 115~120행 실재 확인, 조사=문서/구현=10단계 분리(§4-D10) |
| 게스트 신원 토큰 | 반영 — §4-D4, 보안 수준 확정은 §5-10 |
| U3=E2-0 코드 이중화 해소 | 반영 — U3 단일 코드 유지(§1) |
| E6 선행 의존에 U3 · E3 추가 | 반영 — 검증 2단 분리 + 의존 표기(§2, §3-E6) |
| T2 macOS 실행 가능성 조사 편입 | 반영 — E1-R 조사 항목(§4-D10) |