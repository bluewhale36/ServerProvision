# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 이 저장소에서 작업할 때의 가이드다. **"무엇을 만드는가"(목적·현황)와 "어떻게 일하는가"(불가침 프로세스·철학)만 담는다.** 단계별 상태·일정의 SSOT(Single Source of Truth, 단일 진실 출처)는 Notion DB, 엔티티 필드·메서드 상세의 SSOT는 코드다 — 둘을 여기 중복 나열하면 drift(불일치 표류)가 생기므로 하지 않는다.

## 프로젝트 개요 (무엇을 만드는가)

**ServerProvision = 물리 서버 프로비저닝 자동화 시스템.** 데이터센터 운영자가 베어메탈 서버에 OS·펌웨어·드라이버를 일관되게 자동 설치·설정하기 위한 관리 시스템이다.

- **관리자(Management)** 가 프로비저닝에 쓸 자원을 등록·관리한다 — OS 이미지/ISO, 메인보드 모델, BIOS·BMC 펌웨어, Subprogram(드라이버·유틸리티). 각 자원은 디스크 파일 + **HMAC 서명 마커**(`.provision.json` in-tree 또는 sidecar)로 무결성을 추적한다.
- **운영자(Maintenance)** 영역은 자가 점검·복구 — 파일 경로가 바뀌면 마커 기준으로 DB 를 재조정(reconciliation), soft-delete 자원은 휴지통(`.soft-deleted/`)으로 격리, DB/FS 불일치(ghost·orphan) 정합화.
- **사용자(Provisioning)** 가 세팅 정의서로 프로비저닝 절차(OS 설치 등 다형 단계)를 정의해 서버에 할당한다. 물리 서버가 **PXE 부팅**(`/api/pxe/v1/boot`)으로 등록되면 이 정의에 따라 자동 프로비저닝된다(실행 엔진 = **E 단계**, 구상 중).
- **스택**: Spring Boot 4 + Thymeleaf SSR 관리 UI(일부 XHR) + Spring Data JPA + MariaDB.

## 현재 상태 (어디까지 왔는가)

- 브랜치는 **`main`**(실 배포 소스, GitHub 기본) ← **`dev`**(개발 완료 도달점) ← **`feature/*`**(작업) 3계층 — 구 `renew/main` 단일 라인을 2026-07 재편(운용 규칙은 아래 '개발 흐름 > 브랜치 운용'). 구 1차 개발 구현(`archive/legacy/`)은 실행경로 외 참조용이다.
- **구현됨**: Management 자원관리(`os`/`board`/`bios`/`bmc`/`subprogram`) + Maintenance(`reconciliation` 드리프트 탐지·해결 체계/`trash`/`orphan`) + Provisioning(`setting` 세팅 정의서 CRUD 완료 = U2 / `biossetting` BIOS 세팅 템플릿) + Execution 골격(`execution` — 게스트 서버 데이터 모델 + PXE 최초 등록 = U1) + global 인프라(`marker`/`job`/`lifecycle`/`security`/`ui` 등).
- **리팩토링 캠페인 R1~R9** 로 Controller/Service 분리 + `LifecycleService` 다형 정렬 + `MarkableScanner` SPI(Service Provider Interface) 분리 + 생성자 순환 제거 + 예외 라우팅 다형화 + reconciliation 사용성 개선 진행.
- **다음 본류**: **E 단계**(프로비저닝 실행 엔진 — `ProvisioningPhase` 순서대로 E1=진단 리눅스부터) 로드맵 구상 착수. U3(정의서-서버 할당)는 E 로드맵 안에서 위치 결정.
- **단계별 상태·scope·이력은 Notion DB `Provisioning Server 개발 상세` 가 SSOT.** 현황이 궁금하면 CLAUDE.md 가 아니라 그 DB 와 코드를 본다.

## 작업 규칙 (불가침 철학)

### 코딩 스타일
- 코드 주석·커밋 메시지는 **한국어**. 최신 Java(21) 기능과 Spring Boot 4.x 관례를 적극 활용.
- WHY 가 비자명할 때만 주석. 의미 없는 Javadoc 금지. 파일 헤더에 AI 작성 마커(`// Claude` 등) 금지.
- 프론트엔드/백엔드 결합이 필요한 기능은 백엔드 구현에 집중하되 과복잡해지지 않게 적절히 분리한다.

### 중복·가독성 금지 (불가침)
- 동일 로직이 두 곳 이상 복붙되면 즉시 공통 모듈/유틸/fragment 로 추출한다. 한 함수에 검증/변환/IO/응답조립이 뒤섞이면 작은 함수로 쪼갠다.
- 매직 상수 / 무명 boolean 인자 / 중첩 5단계+ / 100줄+ 메서드는 가독성 저해 신호 — 발견 즉시 정비.
- "미래에 갈라질 수 있으니 미리 분리" 류 변명 금지 — **갈라지는 시점에 분리**한다. 반대로, 분리하려는 추상이 **도메인을 가로질러 의미를 잃으면 분리하지 않는다**(over-abstraction 도 동일한 유지보수 비용).

### 조건분기 legacy 확장 금지 → 다형성/Framework primitive (불가침)
- 신규 케이스/예외/도메인 타입이 추가될 때 분기문(try-catch/if-else/switch)에 줄을 늘리지 않는다. 분기문은 도메인 의미가 늘 때마다 같이 자라며 누락/회귀/silent 흡수 사고의 진원지다(과거 `catch(DomainException)` 가 보안 예외를 흡수해 silent 500 으로 새던 사고).
- 대신 **Java 다형성**(interface/abstract/sealed/enum method-per-constant/strategy)과 **Spring primitive**(`@ControllerAdvice`/`@RestControllerAdvice`/`HandlerExceptionResolver`/`@ResponseStatus`/`BindingResult` 자동매핑/AOP/`@EventListener`/`MessageSource`/`Converter`)로 framework 가 분기를 떠맡게 설계. 컨트롤러 try/catch 복붙은 특히 경계 — advice/resolver 로 끌어올린다.
- 신규 분기가 유일한 옵션이면 자문한다: ① 도메인 다형성으로 표현 가능한가 ② Spring primitive 가 이미 있는가 ③ 곧 새 sub-class 책임으로 흡수될 분기인가(OCP). 하나라도 yes 면 다형성으로 전환.
- 단, **도메인 invariant 의 imperative if-throw**(`if (token == null) throw ...`)는 정당하다 — 금지 대상은 "분기 줄 추가로 책임을 늘리는 패턴"이다.

### 예외 = 프로그램 예외 전용, UX 모순은 UI 1차 차단 (불가침)
- 정상 UX 흐름에서 사용자가 일으키는 논리적 모순(예: 부모 비활성인데 자식 활성화 시도)은 backend 예외로 거절하지 않는다. 예외는 **direct POST / 동시성 / stale / 외부변조 같은 진짜 비정상**에서만 발생해야 한다.
- 모순을 부를 액션은 **UI 에서 사전 차단**(버튼 `disabled` + tooltip). frontend 가 예외를 케이스별 처리할 필요가 없어지고 사용자는 이유를 즉시 안내받는다.
- **서버 가드(invariant if-throw)는 안전망으로 유지** — UI 가 정상 흐름을 막으므로 그 가드는 비정상 경로에서만 발동.
- **UI 차단 조건과 서버 가드 조건은 반드시 단일 SSOT 공유**(도메인 메서드 1개를 서버 가드 + 뷰모델 disabled 플래그가 함께 호출). 두 곳에 복붙하면 drift. (선례: `childEnableBlockReason()` / `blocksChild*()`.)

### 설명·답변·문서 작성 규칙 (불가침)
- **사실을 풀어서 설명한다 — 과장·과도한 함축 금지.** "X 가 핵심/결정적" 류 단정으로 뭉뚱그리지 말고 무엇이 어떤 이유로 그러한지 인과·전제·예외를 단계적으로 푼다. 답변·plan/report html·Notion·코드 주석 모두 적용.
- **프로그램 객체(클래스/인터페이스/메서드/패키지) 이름을 임의 약어로 줄이지 않는다.** `SoftDeleteIntentService` 를 `SDIS` 로 쓰지 않는다 — 항상 코드에 실재하는 전체 이름.
- **설계 전문 용어 약어는 처음 등장 시 풀어 쓴다**: `SPI`(Service Provider Interface — 도메인이 구현해 끼우는 확장점), `ISP`(Interface Segregation Principle), `DI`(Dependency Injection), `SSOT`(Single Source of Truth) 등.

### 네이밍
- 패키지: 전부 소문자, **feature-first**(`com.example.serverprovision.management.os`).
- 접미사: MVC `*Controller` / REST `*RestController` / `*Service` / `*Repository` / 요청 `*Request`(접미사 `DTO` 금지) / 응답 `*Response` / 엔티티 `*Entity` 또는 도메인명. Enum·Value Object 는 도메인 의미를 직접 드러내는 명칭(`OSName`/`Vendor`/`MacAddress`/`IpAddress`).

### Primitive Obsession 금지 (불가침)
- 도메인 의미가 있는 값(MAC/IP/버전/진행률/파일경로 등)은 **반드시 Value Object 또는 Enum 으로 타입화**한다. `int currentStepIndex` / `String mac` 처럼 주요 비즈니스 상태를 원시 필드로 표현·전달하지 않는다. 엔티티 필드·Service 시그니처·Request/Response 모두 동일. Value Object(`@Embeddable`/record)는 `vo/` 로 entity 와 물리 분리.

### UI 디자인
- `DESIGN.md` 명세를 엄격 준수. 기존 CSS 재사용 원칙(`static/css/global/style.css`, `miller.css`, `table-list.css`, `form-validation.css`). 인라인 스타일 금지, CSS 클래스 활용.

### 작업 전 체크리스트 진입 (불가침)
아래를 **만들기 전에** `.claude/domain-conventions/` 의 해당 문서를 먼저 연다. 규약을 몰라서가 아니라 **빠뜨려서** 사고가 났기 때문에 두는 관문이다(S10 계기).

| 만드는 것 | 여는 문서 |
|---|---|
| 새 예외 클래스 | `new-exception.md` |
| 상태 변경 폼 · 입력 폼 | `new-form.md` |

해당 문서가 없는 영역(자원 도메인·드리프트·특권 명령·phase)은 그 영역을 실제로 건드릴 때 실측해 추가한다 — 추측으로 채우지 않는다.

## 아키텍처

### 영역 분할
- **Management** (`/management/*`) — 자원 관리. `os`(OSMetadata 1:N ISO) / `board`(BoardModel) / `bios`(BoardBIOS) / `bmc`(BoardBMC) / `subprogram`(드라이버·유틸, FK nullable=공용). BoardModel 1:N {BIOS, BMC, Subprogram}.
- **Maintenance** (`/maintenance/*`) — 자가 점검·복구. `reconciliation`(경로 드리프트), trash·orphan 정합화.
- **Provisioning** (`/provisioning/*`) — 사용자 영역. `setting`(SettingDefinition + SettingProcess 다형 payload = 실행 계약 SSOT) / `biossetting`(BIOS 세팅 템플릿).
- **Execution** (`/api/pxe/v1/*`) — 실행 영역. 게스트 서버(GuestServer/GuestServerDetail/HostNicBinding/ProvisioningProgress/SetupStep, MacAddressVO/IpAddressVO) + PXE 부팅 진입점 + 실행 엔진(E 단계). 큰 단계 체크포인트는 `ProvisioningPhase`, 하위 단계는 `ProvisioningPhaseStep` enum.
- **global** — 영역 무관 인프라: `marker`(ProvisionMarkerService + `Markable` + `MarkableScanner` SPI) / `job`(BackgroundJob) / `lifecycle`(`LifecycleService`/`SoftDeleteIntentService`/`TypedNameGuard`) / `trash` / `orphan` / `registration` / `security` / `exception` / `ui` / `entity`(BaseTimeEntity) / `config`.

각 feature 하위는 `controller/`·`service/`·`repository/`·`entity/`·`vo/`·`dto/`·`enums/`·`exception/` 로 세분. **엔티티 필드·도메인 메서드의 상세는 코드가 SSOT** — 여기 나열하지 않는다.

### 핵심 설계 패턴 (코드 읽기 전 알아둘 것)
- **lifecycle 다형**: 모든 자원 도메인은 `global.lifecycle.LifecycleService`(1-arg: `toggleEnabled(id)`/`softDelete(id)`/`restore(id,cascade)`/`deprecate`/`undeprecate`/`purge`/`purgeWithTypedNameCheck`)를 구현한다. fat `*Service` 는 `*LifecycleService`/`*RegistrationService`/`*IntegrityService`/`*MarkerWriter`/잔류 `*Service`(read+update)로 5분할하는 것이 표준(R4~R6 선례).
- **마커 인프라**: 공용 엔진 `ProvisionMarkerService`(서명/기록/검증, 도메인 무관 1개) + 도메인별 thin `*MarkerWriter`(attribute 조립). 엔티티는 `Markable` 구현, 스캐너는 `MarkableScanner`(4 sub-interface 합성).
- **typed-name 검증**: 영구삭제 전 사용자가 자원명을 직접 입력 → static `TypedNameGuard.verify(Markable, String)`(의존성 0). controller 의 id→entity 조회는 `TypedNameVerifier` 빈. **service 에 `TypedNameVerifier`/scanner/`ObjectProvider` 를 주입하면 생성자 순환이 재생성되므로 금지**(R7 이 제거함).
- **forging 가드**: 부모-자식 URL(`/{boardId}/bios/{biosId}/...`)은 `*LifecycleService.assertBelongsTo*(childId, parentId)` 별도 메서드로 검증하고 controller 가 lifecycle 직전 호출(`IsoLifecycleService.assertBelongsToOs` 선례). 단 공용 FK(부모 없음)·boardId 없는 URL 은 미적용.

### 레이어 경계
- Controller ↔ Service: `*Request`/`*Response` 만. 뷰(Thymeleaf Model)에 엔티티 직접 노출 금지.
- Service ↔ Repository: 엔티티 직접 사용. `@Transactional` 은 Service 경계(Controller 금지).
- 입력 검증은 `@Valid` + `BindingResult`. 도메인 예외는 `global/exception/`(또는 `global/security/exception/`), advice 가 HTTP 응답으로 변환.

## 기술 스택
- **Spring Boot 4.x** + **Jackson 3** — 어노테이션(`@JsonCreator`/`@JsonTypeInfo` 등)은 backward-compat 로 `com.fasterxml.jackson.annotation.*` 유지되나, 런타임 클래스(`ObjectMapper` 등)는 **`tools.jackson.*`** 를 쓴다. `com.fasterxml.jackson.core/databind` 는 클래스패스에 없다.
- Spring MVC + Thymeleaf(관리자 UI = `@ModelAttribute` + `BindingResult` 폼 제출) · Spring Data JPA + MariaDB · Lombok(`@Builder`/`@Getter`/`@RequiredArgsConstructor`).

## 개발 흐름 (어떻게 일하는가)

작업은 **인벤토리 코드**(작업 단위 식별자)로 부른다: `MA*`(Manage-Application) / `MK*`(Manage-Kernel/Maintenance) / `U*`(Provisioning) / `E*`(Execution — 프로비저닝 실행 엔진, E1=진단 리눅스부터 `ProvisioningPhase` 순서 대응) / `S*`(cross-cutting infra) / `R*`(리팩토링 캠페인) / `HF*`(hotfix) / `M0`(리네임) / `CH*`(housekeeping) / `DOC*`(문서화) / `OPS*`(서버 운영 설계 — 배포 대상 OS 위에서 애플리케이션을 어떤 계정·권한·배치로 돌릴지). 코드 번호는 식별자이지 실행 순서가 아니다. **각 코드의 상태·이력은 Notion DB 가 SSOT** — CLAUDE.md 에 이력을 적지 않는다.

### 수직 슬라이스 (페이지/작업당 12 단계)
1. URL/데이터 흐름 스케치 — **plan html 산출**(아래 규약) 2. Thymeleaf 뷰(더미, 기존 CSS 재사용) 3. Controller(`@ModelAttribute`+`BindingResult`, Model 엔 Response 만) 4. Request/Response DTO(`@Valid`) 5. Service 인터페이스+시그니처(`@Transactional` 경계) 6. Repository(Spring Data 네임규칙) 7. Entity(`BaseTimeEntity` 상속, **7단계 전 `@Entity` 작성 금지**) 8. Service 본체 + 테스트(아래 규율) 9. 스키마 확인(`ddl-auto=validate` — 수동 DDL 을 `ddl/` 에 산출·적용 후 `SHOW CREATE TABLE` 검증. ALTER 권한 계정 필요 — `claude_code` 는 ALTER 불가) 10. **샌드박스 브라우저 검증**(아래 규율) 11. **report html 산출**(아래 규약) 12. 브라우저 E2E — **사용자 단독**.
- 리팩토링 슬라이스(엔티티 무변경)는 6·7·9 단계가 N/A 가 될 수 있다. 화면이 없는 슬라이스는 10 단계를 하네스 검증으로 대체한다(아래 E 단계 예외).

### 체크포인트 (CP1~CP7)
12 단계를 7 체크포인트로 묶어 진행한다. **사용자 승인 게이트는 CP1 · CP6 · CP7 이다** — CP1 승인이 CP2~CP4 의 진행 승인을 겸하므로 그 구간은 무중단으로 진행하고 경계마다 보고만 한다(CP1 승인 전 선행 구현 금지는 불변). 각 CP 의 수행 주체와 위임 분업은 아래 블록을 따른다.

| CP | 범위 | 동작 |
|---|---|---|
| **CP1** | Step 1 | Claude 가 plan html 생성·제시 → 승인 |
| **CP2** | Step 2~5 | 뷰·Controller·DTO·Service 시그니처 구현 후 보고 → 승인 |
| **CP3** | Step 6~7 | Repository·Entity(+VO/Enum) 후 보고 → 승인 |
| **CP4** | Step 8~9 | Service 본체·테스트·스키마 확인. **여기까지가 빌드·테스트 green 확인** → 승인 |
| **CP5** | Step 10 | **샌드박스 브라우저 검증** — 검증 항목 계획 후 수행, 스크린샷 채증 → 승인 |
| **CP6** | Step 11 | **report html 산출** — CP5 의 계획·수행 기록·증거를 담아 제출 → 승인 |
| **CP7** | Step 12 | 사용자 단독 브라우저 E2E → 완료 통보 대기 |

> **모든 CP 중 Claude 가 가장 많은 사고를 쏟아야 하는 단계는 CP1(계획)이다 (불가침).** 코드 구현(CP2~CP4)은 좋은 계획만 있으면 기계적이지만, 설계 결함은 CP1 에서 못 잡으면 그대로 굳는다. plan 을 쓰기 전 반드시: ① **설계 대안을 복수 생성·비교**(첫 떠오른 안 하나로 쓰지 않는다 — 2~3 접근의 trade-off 명시 후 최선 선택) ② **자기 선택을 적대적으로 비판**(통일성 깨지 않는가/churn 만들지 않는가/더 간단한 길은/숨은 순환·결함은 — 막히면 코드를 더 읽어 확인) ③ **결정에 채택안 + 비채택 대안 + 탈락 사유를 함께 기록**(사용자가 "더 나은 방안 없냐" 되묻지 않아도 최적 설계가 plan 에 담겨야 한다). 필요하면 CP1 에 workflow/Agent 를 적극 동원한다 — CP1 토큰은 CP2~CP4 재작업을 막는 투자다.

> **CP 별 수행 주체와 위임 분업 (2026-08-09 확정, 불가침).** 메인 세션의 모델이 무엇이든 — Opus 최신 모델인 경우에도 — 항상 다음을 따른다(위임 대상 모델의 티어 규칙은 사용자 전역 CLAUDE.md 가 SSOT).
> - **CP1** — plan 의 설계 사고(대안 비교 · 결정 · 진리표 류 핵심 산출물 구상)는 **메인 세션이 직접** 한다. plan html 의 파일 생성과 내용 전사는 **Opus 최신 하위 에이전트에 위임**한다 — 에이전트가 내용을 지어내지 않도록 완결된 내용 브리프를 넘기고, 산출물은 세션이 검수한다(에이전트가 발굴한 쟁점 · 정정은 세션이 판정).
> - **CP2~CP4** — 승인된 plan 범위의 구현 · 테스트 · 스키마 확인은 **메인 세션이 직접, 무중단으로** 진행한다. CP4 에서 빌드 · 전체 테스트 green 을 확인해 보고한다.
> - **CP5** — 샌드박스 검증은 **Opus 최신 하위 에이전트가 수행**하고 세션에 보고한다. 에이전트는 **결함을 발견해도 수정하지 않고** 재현 기록과 함께 보고만 한다. 메인 세션이 그 보고를 검토해 수정 · 재실시 여부를 판단하고 지시한다("발견 즉시 수정 후 재검증" 원칙의 위임판 — 수정 주체가 세션으로 옮겨질 뿐 원칙은 유지).
> - **CP6** — report 의 내용 작성(검증 기록의 구성 · 서술)은 **메인 세션이 직접** 하고, html 파일 생성과 전사는 **Opus 최신 하위 에이전트에 위임**한다(CP1 과 같은 브리프 · 검수 방식).
> - **CP7** — 종전대로 사용자 단독. 완료 통보 후 Notion 종료 경계 · 커밋 · PR 은 별도 지시를 따른다.

> **CP5 · CP7 의 E 단계 예외** (2026-07-12 합의, 2026-08-02 CP 재편 반영): 실행 엔진(E*) 슬라이스는 게스트가 화면 없이 HTTP 로만 상호작용하므로, **CP5 의 샌드박스 검증과 CP7 의 사용자 E2E 를 브라우저 대신 모의 게스트 하네스(`scripts/mock-guest/`) 실행 + 게스트 서버 상세 페이지 확인**으로 수행한다. 관리자 화면이 딸린 슬라이스(자산 대시보드 등)는 그 화면만 브라우저로 검증한다. 하네스는 게스트의 HTTP 행동(부팅→체크인→보고)을 curl 로 재연하는 git 추적 자산이며 Step 8 테스트 규율을 대체하지 않는다. 실기(T3) 검증이 유보된 항목은 `docs/T3-checklist.md` 에 적립하고 그 시점을 Notion 후속 마일스톤에 기재한다.

> **CP 체계는 코드 슬라이스에만 적용한다** (2026-08-02 확정). `OPS*` 처럼 **산출물이 코드가 아니라 결정인 설계 계열**은 위 12 단계·7 체크포인트를 따르지 않는다. 뷰·컨트롤러·서비스가 없으므로 대응시킬 자리가 없기 때문이다. 이런 단계는 **토론에서 결정이 확정되면 완료**로 닫고, 그 결정을 실제 파일·코드로 옮기는 일은 별도 단계(구현 계열)로 넘긴다. 토론 진행과 아카이브는 discussion 규약을 따른다.

### 샌드박스 브라우저 검증 (Step 10 · CP5, 불가침)
빌드와 테스트가 통과해도 화면에서만 드러나는 결함이 있다. 과거 전수 점검에서 실제로 direct POST 로 폼 마커가 빠지면 오류가 조용히 삼켜지던 결함(silent 200)을 이 방식으로 잡았다. 그래서 **CP4 의 green 은 완료가 아니라 검증 착수 조건**이다.

- **브라우저에서 사람이 조작하듯 수행한다.** API 직접 호출(curl 등)로 응답만 확인하는 것은 CP5 를 대체하지 않는다 — 화면에서만 드러나는 결함을 잡는 것이 이 단계의 존재 이유다. API 확인은 화면 없는 슬라이스(아래 E 단계 예외)에서만 대체 수단이 된다.
- **먼저 검증 항목을 계획하고 식별자를 붙인다.** 정상 흐름 `A1`·`A2`…, 비정상 조작 `B1`·`B2`…, 막는 방식 `C1`·`C2`… 로 번호를 매겨 목록을 제시하고 시작한다. 즉흥적으로 클릭하며 훑지 않는다 — 식별자가 있어야 계획과 수행이 1:1 로 대조되고 빠진 것이 보인다.
- **정상 흐름만 보지 않는다.** 의도한 동작이 되는지와 함께 **의도하지 않은 조작을 일부러 시도**해 애플리케이션이 어떻게 막는지 확인한다. 잘못된 값 입력, 순서를 건너뛴 요청, 이미 삭제된 자원 조작, 권한 없는 경로 접근, 중복 제출 같은 것들이다.
- **막는 방식까지 본다.** 거절하는 것으로 끝이 아니라 **UX 를 해치지 않고 유도하는지**를 함께 평가한다. 사용자가 왜 막혔는지 알 수 있는가, 다음에 무엇을 해야 하는지 안내되는가, 애초에 막힐 행동을 UI 가 먼저 차단했는가(불가침 원칙 "UX 모순은 UI 1차 차단" 의 실지 확인).
- **항목마다 스크린샷을 남긴다.** 식별자와 1:1 대응한다 — 항목 수보다 스크린샷이 적으면 검증이 덜 된 것이다. 이 증거가 CP6 report 의 본문이 된다.
- 검증 항목 설계 지침과 기준 구현은 **`.claude/cp5-verification-spec.md`** 를 따른다.
- **검증 중 결함을 발견하면 그 자리에서 고치고 재검증한다.** 무엇을 발견해 어떻게 처리했는지는 report 에 남긴다 — 결함을 찾은 사실 자체가 이 단계의 성과이므로 숨기지 않는다.
- 샌드박스 환경 구축 절차는 누적 메모리의 샌드박스 레시피를 따른다. 동시 세션과 포트가 겹치지 않게 스트림마다 다른 포트를 쓴다.

### plan / report html 규약 (불가침)
- **저작 SSOT**: 골격·디자인 객체(class/id)·색 토큰·반응형·JS 4 동작·🎬 데모의 단일 규약은 **`.claude/plan-report-html-spec.md`**, 기준 구현은 **`plan/plan_template.html`** 이다. **최근 파일을 복제하지 않는다**(드리프트 원인) — 명세와 템플릿을 따르고, 카탈로그에 없는 디자인 객체가 필요하면 명세에 먼저 추가한 뒤 쓴다.
- **plan** = CP1(Step 1) 산출물. `plan/YY-MM-DD_HH-MM-SS_<인벤토리코드>_plan.html`. **§2 직후 🎬 라이브 데모는 진짜 인터랙티브**(단순 텍스트/도식 금지 — 상태를 직접 바꿔 차단·전이·cascade 를 체험, 판정 JS = 서버 도메인 메서드로 드리프트 0). 섹션 체계는 명세 §1-2 의 `s0`~`s11`.
- **report** = **CP6(Step 11) 산출물**(캠페인·단계 완료 보고에도 같은 규약을 쓴다). `report/YY-MM-DD_HH-MM-SS_<단계명>_report.html`. plan 과 같은 명세·골격을 따르되 섹션 구성은 보고 대상에 맞춘다. **CP5 샌드박스 검증의 기록이 본문의 중심**이다 — ① 무엇을 확인하려 했는지(검증 항목 계획) ② 실제로 어떤 조작을 했는지(정상·비정상 시도 각각) ③ 스크린샷 증거 ④ 검증 중 발견한 결함과 그 처리. **판정 기준** — 항목 식별자(A/B/C)가 계획과 수행에 모두 나타나고, 항목 수만큼 스크린샷이 있으며, 항목마다 실제 조작과 결과가 서술돼야 한다. 구현 요약만 있거나 검증이 요약 몇 줄로 끝난 report 는 CP6 승인 대상이 아니다.
- **report 의 기준 구현 = `report/26-08-07_15-45-05_S10_report.html`**(사용자가 분량·밀도·전달력을 승인한 형태). 골격은 명세를 따르되 **섹션 구성과 서술 밀도는 이것을 본다**. 특징 — 진단(실측 근거) → 구현 → 강제 장치 → CP5 계획 → CP5 수행 기록 → **미수행 항목과 사유** → 발견한 것 → Critical Files → 남은 일. 항목 식별자 22 개(A1~A10 · B1~B8 · C1~C4)에 스크린샷 16 장이 1:1 로 붙고, 본문은 1 만 자 남짓이다. **못 한 것을 사유와 함께 적는 §7 이 이 보고서의 핵심**이다 — 빠진 것을 감추지 않아야 보고가 성립한다. 정본 952 KB. CP5 검증 항목 설계 지침은 `.claude/cp5-verification-spec.md`(선례로 `report/26-08-07_10-22-42_MK4-1_report.html` 도 참고).
- **Notion 임베드용 별도 산출물을 만들지 않는다** (2026-08-12 개정). **스크린샷을 담은 정본 `_report.html` 한 벌만 낸다** — 사진이 많아 수백 KB~수 MB 가 되어도 Notion 임베드에 문제가 없음이 실증됐다(1.8 MB 정본 업로드 성공). 종전의 "정본 + 임베드본 두 벌" 규약은 인라인 업로드가 유일 경로였을 때(한국어 html 약 70 KB 상한)의 우회책이었고, 그 전제가 깨졌으므로 **`_report-notion.html` 산출은 폐지한다**(plan 도 동일 — 파트 분할 불요).
- **Notion 에 올리는 방법 두 가지** — 편한 쪽을 쓴다. ① **사용자가 html 파일을 해당 탭에 직접 드래그 · 드롭**(가장 빠르다. 세션이 굳이 대신하지 않아도 된다). ② 세션이 처리할 때는 **커밋 · push 후 `notion-create-attachment` 의 `source_url`** 에 커밋 SHA 고정 raw URL(`https://raw.githubusercontent.com/bluewhale36/ServerProvision/<SHA>/...`)을 넘긴다 — 모델 출력 토큰과 무관해 크기 벽이 없고, 반환 `content_length` 를 원본 바이트와 대조해 검증한다. 인라인 `content` 경로는 커밋 전이거나 저장소가 비공개일 때만 쓴다.
- **공통 불가침**: 자체 완결 단일 파일(외부 CSS/JS/폰트/이미지 참조 0 — Notion 임베드가 sandboxed iframe 이라 외부 의존 시 깨짐), 색은 `:root` 토큰만, **반응형**(데스크톱 무변경 + 태블릿/휴대폰에서 헤더·ToC 비고정 + 표/스윔레인/긴 코드 처리), 시각은 **KST(Asia/Seoul)**. 작성은 전용 에이전트(`plan-docx-architect` / `stage-report-architect`)에 위임할 수 있으며, 그 경우 **내용 구상은 세션이 하고 에이전트는 명세대로 옮겨적기만** 한다.

### discussion 규약 (계열 무관, 2026-08-02 일반화)
코드 착수 전 사용자와의 토론 비중이 큰 작업에 쓴다. E 단계(핵심 비즈니스 로직)에서 시작했으나 OPS 계열(서버 운영 설계)에도 그대로 적용되므로 계열 한정을 두지 않는다.
- **진행은 Notion, 종결은 저장소(하이브리드, 2026-08-02 확정)**. 토론이 오가는 동안은 **해당 Notion 단계 페이지 본문의 `## 토론` 절**에서 한다 — Claude 가 현행 실태·열린 질문·대안 비교를 채우고 사용자가 각 질문 아래 인용문으로 답한다. 모바일에서 답할 수 있고 알림이 오는 것이 이 방식의 이유다. **토론이 끝나면 저장소로 아카이브**한다(경로는 아래). 아카이브에는 결정만이 아니라 **쟁점과 탈락 대안까지** 담는다 — git 이력·이식성·인수인계 가치가 거기서 나온다. 전면 Notion 은 채택하지 않았다(설계 토론이 저장소에서 사라지면 Notion 접근 권한 없는 인수인계자가 근거를 못 본다).
- **경로**: `discussion/YY-MM-DD_HH-MM-SS_<주제>_discussion.md`, timestamp 는 **KST(Asia/Seoul)**.
- **다른 단계에서 기인한 제약을 언급할 때는 인과를 풀어 쓴다.** 단계 코드만 인용하면 상대는 판단할 수 없다 — 그 단계가 무엇을 정했고 지금 무엇을 제약하는지 서술하고, Notion 단계 페이지가 있으면 멘션해 바로 열어볼 수 있게 한다.
- **포맷**: markdown — 사용자와 Claude 가 **서로 수정하기 편하고 가독성 좋은 텍스트**가 목적. plan html 의 인터랙티브 시뮬레이터 같은 장치 대신, 열린 질문(토론 포인트)·대안 비교·확정/미확정 구분 표기가 중심이다.
- **plan/report html 을 대체하지 않는다** — E 슬라이스도 CP1 진입 시 plan html 은 기존 규약대로 산출한다. discussion 문서는 CP1 앞의 구상·토론 자산이며, 토론 결과가 plan 의 입력이 된다.
- **시리즈 구성**: 한 주제의 **최초 문서만** 전체 그림(로드맵·단계 상세)을 포함한다 — 처음 파악하는 데는 그 편이 낫다. **후속 문서는 토론 전용** — 로드맵·단계 상세를 재기술하지 않고 쟁점·응답·파생 질문만 담는다. 토론 종결 시 **마지막 문서에서 결정 사항을 간단히 정리**하고, 본격 명세는 해당 슬라이스의 plan html 로 넘긴다.
- 문서 스타일은 "설명·답변·문서 작성 규칙" 을 그대로 따른다(사실 풀어쓰기·약어 금지·유스케이스 중심).

### 다이어그램 규약 (E 단계, 불가침)
- `diagram/` 의 인터랙티브 통신 다이어그램(`guest-provisioning-canvas.html` 등)은 Provisioning Server ↔ Guest Server 가 LAN·MGMT 두 통로로 주고받는 요청·응답·객체·스크립트를 **Provisioning Step 기준**으로 보여준다.
- **E 단계(하위 단계 포함)가 완료될 때마다 그 단계에서 새로 오가는 상호작용을 반영해 다이어그램을 갱신**한다(신규 생성 또는 기존 수정). 보고서(report html)의 시각 자산으로 재사용한다.

### 테스트 규율 (불가침)
단위 테스트만으로는 "예외→HTTP 응답" 매핑 사고나 컨트롤러 분기 누락이 안 드러난다(과거 `MissingFilenameException` 이 500 으로 새던 사고). Step 8 은 **두 레이어 모두** 작성:
1. **단위 테스트** — Service 분기별 happy 1 + 실패 1 (JUnit + Mockito).
2. **사용자 액션 통합 테스트** — 각 엔드포인트의 모든 액션을 HTTP 계층에서 실제 상태코드·바디로 검증(`@WebMvcTest` + `MockMvc` + `@MockitoBean`). 컨트롤러 단위 파일 분리(`*ControllerUploadFlowTest` 등). 4 범주 필수: **성공 2xx**(바디 필드값) / **400**(필드 메시지) / **409**(모든 `ConflictException` 하위 실제 트리거) / **404**(모든 `NotFoundException` 하위, forging 포함). Mockito mocking 은 Service 단까지만 — 컨트롤러 try/catch + advice 매핑은 실제 실행되어야 한다.
- **새 예외 클래스를 추가하면 그 예외를 발생시키는 시나리오 테스트를 반드시 함께 추가**한다. 단위만 있고 통합 시나리오가 빠진 묶음은 CP4 승인 대상이 아니다.

### 체크포인트 ↔ Notion 동기화 (background agent 위임, 불가침)
- 각 CP 보고 **직후** 1회, 해당 슬라이스가 Notion DB 에 존재하면 상태/일정 갱신을 **background agent 에 위임**(`Agent` `run_in_background:true`). main loop 은 결과를 안 기다리고 승인 대기/다음 작업을 잇는다.
- **시작 경계**(CP1/CP2 진입): 해당+상위 단계 `상태`='진행 중', `시작 일자`=당일(KST), 이미 진행 중이면 no-op. **종료 경계**(CP7 완료 통보 후): `상태`='완료', `종료 일자`=완료일(KST). **중간(CP2~CP6)**: 상태/일정 변화 없으면 생략. **`완료` 처리는 CP7 사용자 완료 통보 이후에만** — 임의 선완료 금지(CP5 샌드박스 검증을 Claude 가 통과시킨 것은 완료 근거가 아니다).
- Notion 에 단계가 없으면 위임 생략(임의 신설 금지).

### Notion 작업 규약
- 페이지 'Provisioning Server', DB 'Provisioning Server 개발 상세'. 댓글은 `[Claude]` 접두사.
- **페이지 신설/상태 갱신/scope 변경 시 본문(content)에 4 항목 필수 기재(불가침)**: ① scope 요약 ② 비 목표(out of scope + 다음 슬라이스 어디로) ③ 잔존 책임/임시 비대칭 + 해소 시점 ④ 후속 마일스톤. plan/report 는 별도 자산 — Notion 페이지는 단독으로도 슬라이스 의도를 파악할 수 있어야 한다. 본문을 빈 채로 두지 않는다.
- **단계 페이지 골격(2026-07 개편, 불가침)**: 단계 페이지는 **최상단 tabs 블록**(탭 3 — `요약`: 수행 내용 핵심 1줄 / `plan`: plan html 임베드 / `report`: report html 임베드) + **그 아래 위 4항목 본문**으로 구성한다. 골격 정본 = 'Provisioning Server > 단계 페이지 개편안'. plan/report html 은 산출 시점(plan=CP1, report=완료 보고)에 해당 탭에 임베드한다. **이 개편은 본문 골격만 바꾸며 DB 속성(스키마)은 생성·수정·삭제하지 않는다.** (tabs 는 API 로 직접 못 만들어, DB 템플릿 `(단계) : (개요)` 를 `template_id` 로 인스턴스화한 뒤 4항목 본문을 `update-page` 로 잇는다.)

### 브랜치 운용 (2026-07 재편)
- 3계층: **`main`**(실 배포 소스, GitHub 기본 브랜치) ← **`dev`**(개발 완료 도달점) ← **`<구분>/<단계>_<기능>`** = **`<type>/<인벤토리코드>_<슬러그>`**(작업). type(구분) 은 conventional 어휘(`feat`/`fix`/`refactor`/`docs`/`chore`). **단계(인벤토리 코드)에 하이픈이 들어가므로(`E1-I`, `HF4-1`) 단계와 기능의 경계는 언더스코어 `_` 로 나눈다.** 예: `feat/E1-I_boot-infra`, `feat/E1_diagnose-linux`.
- feature 브랜치는 **캠페인/phase 단위**(plan 1건마다 파지 않는다). PR 은 CP4(빌드·테스트 green)에서 열고, **CP7 사용자 완료 통보 후** `dev` 로 `--no-ff` 병합한다(CP5 샌드박스 검증과 CP6 report 는 그 PR 에 커밋을 더한다). `dev`→`main` 은 배포 시 병합.
- 커밋 메시지·코드 주석은 **한국어 유지**. 브랜치명만 영문(URL·git 인자 영역이라 ASCII).
- 학습·실험은 **`refine/<기능>`** 로컬 전용 브랜치(push 안 함 → 정규 이력·기여 그래프 미오염). 좋은 결과만 `refactor/R*` 로 cherry-pick 승격.
- 동시 작업은 **상주 스트림 워크트리**로 분리한다 — 배치·명명·신설 조건은 아래 'AI 세션 운용'. 워크트리 생성은 수동 `git worktree add`(편의 함수 `~/.zshrc: spv-wt`). 내장 `claude --worktree` 는 워크트리 이름을 브랜치명으로 만들고 base 가 main 고정이라 쓰지 않는다.

### AI 세션 운용 (2026-08 확정, 불가침)

세션 맥락은 **작업 디렉토리 절대경로**에 묶이고, 누적 메모리는 **저장소** 단위로 공유된다. 그래서 캠페인마다 워크트리를 새로 파면 디렉토리가 바뀌어 **세션 맥락이 0 에서 다시 시작**하고, 단계마다 상황 파악을 위한 전수 조사가 되풀이된다 — 2026-07 에 실제로 치른 비용이다. 워크트리를 **단계가 아니라 스트림에 고정하고 브랜치가 그 안에서 회전**하게 해 이를 없앤다.

- **상주 스트림 = 워크트리 1 개 = 세션 홈.** 디렉토리명은 `ServerProvision-<스트림>`. 캠페인이 바뀌어도 **디렉토리를 새로 만들지 않고** 그 안에서 `git switch -c <type>/<인벤토리코드>_<슬러그> origin/dev` 로 브랜치만 갈아끼운다. 세션은 그대로 이어져 직전 캠페인의 맥락 위에서 다음을 시작한다. **이 전환은 그 홈의 세션이 수행한다** — 사용자가 터미널을 여는 것은 디렉토리에 세션을 처음 열 때 한 번뿐이다(세션은 자기 작업 디렉토리를 바꿀 수 없으므로).
- **현행 스트림 둘.**
  - **`ServerProvision-dev` (앵커)** — `.git` 실체 보유라 **이동·삭제 금지**. `dev` 상주. 역할은 ① **통합**(PR 병합·`dev`→`main` 배포 병합·태깅·DDL 적용) ② **규약과 AI 사용 설계**(CLAUDE.md·에이전트 정의·문서 체계·Notion 체계) ③ **M·S·R·HF 산발 작업**(여기서 브랜치를 파고 끝나면 `dev` 로 복귀). **특정 인벤토리 계열에 묶이지 않는다** — 거버넌스는 도메인 횡단이고, 통합은 `dev` 가 상주 체크아웃이라 여기가 자연스러운 자리다.
  - **`ServerProvision-ue`** — U/E 실행 엔진 본류. E·U 캠페인이 이 홈에서 회전한다.
- **스트림 신설 3 조건 — 전부 충족할 때만 만든다.** ① 그 영역의 작업이 **여러 캠페인에 걸쳐 지속**되고 ② 다른 스트림과 **동시에 진행**되어야 하며 ③ 축적되는 **도메인 맥락이 재사용**된다. 하나라도 미달이면 만들지 않는다(DOC·학습은 ① 이 아직 없어 보류). "미리 분리 금지" 원칙의 세션 판이다.
- **핫픽스는 발견한 스트림에서 처리한다.** 작업 중 결함을 발견하면 그 홈에서 `fix/HF*_…` 로 갈아타 고치고 원 브랜치로 돌아온다 — 새 워크트리도 세션 이관도 하지 않는다. 계획을 유동적으로 바꾸는 능력이 여기서 나온다.
- **단기 워크트리는 예외.** 같은 스트림의 두 브랜치를 동시에 체크아웃해야 할 때만 파고, 그 경우에만 기존 `ServerProvision-<브랜치>` 명명을 쓰며, 끝나면 제거한다.
- **새 홈 부트스트랩**: `CLAUDE.local.md`(gitignore)는 `.worktreeinclude` 로 승계되고, 누적 메모리는 저장소 기준이라 자동 공유된다. 첫 실행 시 신뢰 승인 1 회만 거친다.

### 코드 소유권 · 커밋 경계 (불가침)
- **커밋 · push · PR 은 사용자 지시가 있을 때만.** 필요하면 제안하고 승인을 기다린다 — 절대 임의로 하지 않는다.
- **push 와 PR 은 반드시 현재 로컬 브랜치와 같은 이름의 원격 브랜치로 올린다(불가침).** `git push -u origin <현재 브랜치명>` 처럼 대상을 명시하고, PR 의 head 도 그 브랜치다. 다른 이름으로 올리거나 `dev`·`main` 등 상위 브랜치에 직접 push 하지 않는다 — 로컬과 원격의 이름이 어긋나면 추적 관계가 깨지고 PR 이 엉뚱한 브랜치를 가리킨다. push 전 `git branch --show-current` 로 대상을 확인한다.
- 커밋 메시지에 AI 참여를 명시한다(GitHub 에서 Claude Code 참여가 드러나도록). 페이지 완성 전 임시 커밋 금지(스키마 drop 선행 커밋 1개는 허용).
- 각 슬라이스의 **2~11 단계를 Claude 가 수행**(코드는 2~9, 검증은 10, 보고는 11), **12 단계(브라우저 E2E)는 사용자 단독**. plan 범위 밖 코드 이동/공통화는 사용자 명시 요청 시에만.

## 빌드 · 실행 · DB
- 빌드/실행 명령과 환경변수는 **`CLAUDE.local.md`** 참고(`SERVER_PORT`/`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 등 주입 필수).
- **로컬 테스트 DB 계정 `readonly_user`**(SELECT 전용, `server_provision`@localhost:3306, pw `readonly_claude`) — 읽기/필드검증 경로 회귀용. INSERT/UPDATE/DELETE 경로는 JPA 단계에서 `INSERT command denied` → 500 으로 끊긴다(코드 버그 아닌 권한 제약, 서버 로그 SQL 예외로 구분). 쓰기 회귀는 쓰기 권한 유저를 별도 주입.
- macOS 환경: `timeout` 명령 없음. bootRun 은 서버라 자체 종료 안 함 — detached 실행 후 로그 폴링으로 마커 확인.

## 아카이브 (`archive/legacy/`)
구 1차 개발 구현의 참조용 복사본(원본은 `v0-prototype`·`archive/dev-legacy` 태그로도 도달 가능). **실행 경로 아님.** 엔티티 필드/컨트롤러 구조/Thymeleaf 구조는 참고 가능하나 스타일·관례는 **답습하지 않고** 본 파일의 원칙을 따른다.
