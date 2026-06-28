# CLAUDE.md

이 파일은 Claude Code(claude.ai/code)가 이 저장소에서 작업할 때의 가이드다. **"무엇을 만드는가"(목적·현황)와 "어떻게 일하는가"(불가침 프로세스·철학)만 담는다.** 단계별 상태·일정의 SSOT(Single Source of Truth, 단일 진실 출처)는 Notion DB, 엔티티 필드·메서드 상세의 SSOT는 코드다 — 둘을 여기 중복 나열하면 drift(불일치 표류)가 생기므로 하지 않는다.

## 프로젝트 개요 (무엇을 만드는가)

**ServerProvision = 물리 서버 프로비저닝 자동화 시스템.** 데이터센터 운영자가 베어메탈 서버에 OS·펌웨어·드라이버를 일관되게 자동 설치·설정하기 위한 관리 시스템이다.

- **관리자(Management)** 가 프로비저닝에 쓸 자원을 등록·관리한다 — OS 이미지/ISO, 메인보드 모델, BIOS·BMC 펌웨어, Subprogram(드라이버·유틸리티). 각 자원은 디스크 파일 + **HMAC 서명 마커**(`.provision.json` in-tree 또는 sidecar)로 무결성을 추적한다.
- **운영자(Maintenance)** 영역은 자가 점검·복구 — 파일 경로가 바뀌면 마커 기준으로 DB 를 재조정(reconciliation), soft-delete 자원은 휴지통(`.soft-deleted/`)으로 격리, DB/FS 불일치(ghost·orphan) 정합화.
- **사용자(Provisioning)** 가 세팅 정의서로 프로비저닝 절차(OS 설치 등 다형 단계)를 정의해 서버에 할당한다. 물리 서버가 **PXE 부팅**(`/pxe/v1/entry/boot`, Stage 4)하면 이 정의에 따라 자동 프로비저닝된다.
- **스택**: Spring Boot 4 + Thymeleaf SSR 관리 UI(일부 XHR) + Spring Data JPA + MariaDB.

## 현재 상태 (어디까지 왔는가)

- 브랜치 **`renew/main`** — 구 `dev` 구현(`archive/legacy/`, 실행경로 외 참조용)을 Top-down 재설계 중.
- **구현됨**: Management 자원관리(`os`/`board`/`bios`/`bmc`/`subprogram`) + Maintenance(`reconciliation`/`trash`/`orphan`) + Provisioning(`setting`/`server`) 골격 + global 인프라(`marker`/`job`/`lifecycle`/`security`/`ui` 등).
- **리팩토링 캠페인 R1~R7** 로 6 도메인(OS/Iso/Board/BIOS/BMC/Subprogram)의 Controller/Service 분리 + `LifecycleService` 다형 정렬 + `MarkableScanner` SPI(Service Provider Interface) 분리 + `ObjectProvider`/생성자 순환 제거 완료.
- **잔여**: R2(예외 처리 모델 개편) 의 R2-3(advice 통합)/R2-4(ProblemDetail)/R2-5(errorCode)/R2-6(frontend alert 정리). Stage 4(PXE Boot 연동) 미착수.
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

## 아키텍처

### 영역 분할
- **Management** (`/management/*`) — 자원 관리. `os`(OSMetadata 1:N ISO) / `board`(BoardModel) / `bios`(BoardBIOS) / `bmc`(BoardBMC) / `subprogram`(드라이버·유틸, FK nullable=공용). BoardModel 1:N {BIOS, BMC, Subprogram}.
- **Maintenance** (`/maintenance/*`) — 자가 점검·복구. `reconciliation`(경로 드리프트), trash·orphan 정합화.
- **Provisioning** (`/provisioning/*`) — 사용자 영역. `setting`(SettingDefinition + SettingProcess 다형) / `server`(Server, MacAddress/IpAddress VO).
- **Entry** (`/pxe/v1/entry/boot`) — PXE 부팅 진입점(Stage 4). 호환성 위해 `/pxe/v1` prefix 유지.
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

작업은 **인벤토리 코드**(작업 단위 식별자)로 부른다: `MA*`(Manage-Application) / `MK*`(Manage-Kernel/Maintenance) / `U*`(Provisioning) / `S*`(cross-cutting infra) / `R*`(리팩토링 캠페인) / `HF*`(hotfix) / `M0`(리네임) / `CH*`(housekeeping). 코드 번호는 식별자이지 실행 순서가 아니다. **각 코드의 상태·이력은 Notion DB 가 SSOT** — CLAUDE.md 에 이력을 적지 않는다.

### 수직 슬라이스 (페이지/작업당 10 단계)
1. URL/데이터 흐름 스케치 — **plan html 산출**(아래 규약) 2. Thymeleaf 뷰(더미, 기존 CSS 재사용) 3. Controller(`@ModelAttribute`+`BindingResult`, Model 엔 Response 만) 4. Request/Response DTO(`@Valid`) 5. Service 인터페이스+시그니처(`@Transactional` 경계) 6. Repository(Spring Data 네임규칙) 7. Entity(`BaseTimeEntity` 상속, **7단계 전 `@Entity` 작성 금지**) 8. Service 본체 + 테스트(아래 규율) 9. 스키마 확인(`ddl-auto=update`, `SHOW CREATE TABLE`) 10. 브라우저 E2E — **사용자 단독**.
- 리팩토링 슬라이스(엔티티 무변경)는 6·7·9 단계가 N/A 가 될 수 있다.

### 체크포인트 (CP1~CP5)
10 단계를 5 체크포인트로 묶어 **단계별 사용자 승인 후 다음으로** 진행한다(승인 전 선행 구현 금지).

| CP | 범위 | 동작 |
|---|---|---|
| **CP1** | Step 1 | Claude 가 plan html 생성·제시 → 승인 |
| **CP2** | Step 2~5 | 뷰·Controller·DTO·Service 시그니처 구현 후 보고 → 승인 |
| **CP3** | Step 6~7 | Repository·Entity(+VO/Enum) 후 보고 → 승인 |
| **CP4** | Step 8~9 | Service 본체·테스트·스키마 확인 후 보고 → 승인 |
| **CP5** | Step 10 | 사용자 단독 브라우저 E2E → 완료 통보 대기 |

> **모든 CP 중 Claude 가 가장 많은 사고를 쏟아야 하는 단계는 CP1(계획)이다 (불가침).** 코드 구현(CP2~CP4)은 좋은 계획만 있으면 기계적이지만, 설계 결함은 CP1 에서 못 잡으면 그대로 굳는다. plan 을 쓰기 전 반드시: ① **설계 대안을 복수 생성·비교**(첫 떠오른 안 하나로 쓰지 않는다 — 2~3 접근의 trade-off 명시 후 최선 선택) ② **자기 선택을 적대적으로 비판**(통일성 깨지 않는가/churn 만들지 않는가/더 간단한 길은/숨은 순환·결함은 — 막히면 코드를 더 읽어 확인) ③ **결정에 채택안 + 비채택 대안 + 탈락 사유를 함께 기록**(사용자가 "더 나은 방안 없냐" 되묻지 않아도 최적 설계가 plan 에 담겨야 한다). 필요하면 CP1 에 workflow/Agent 를 적극 동원한다 — CP1 토큰은 CP2~CP4 재작업을 막는 투자다.

### Step 1 — plan html 규약 (불가침)
- **경로**: `plan/YY-MM-DD_HH-MM-SS_<페이지키>_plan.html`, timestamp 는 **KST(Asia/Seoul)**. 페이지키 = 인벤토리 코드.
- **방식**: html 을 직접 Write. html 인 본질은 **인터랙티브** — ① 🎬 라이브 데모로 설계 동작을 클릭·체험 검증, ② 검색/접기 ToC, ③ localStorage 체크리스트. **골격·CSS·JS 는 묻지 말고 최근 `plan/*.html` 을 직접 읽어 복제**한다.
- 골격: sticky `header.page-header` + sticky `nav.toc`(top:108px) + `<main>`. 섹션 = `<details class="section" id="sN">`. `<input class="filter-box">` 검색. 말미 `<script>` 4 로직(불가침): ToC 클릭→open+smooth scroll(offset −108) / 전부 펴기·접기 / filter→매칭 섹션만 / `check-list[data-storage]` localStorage.
- **🎬 미리보기 = 진짜 인터랙티브(불가침, §2 직후)**: 단순 텍스트/도식 박스 금지. 위젯에서 **상태를 직접 바꿔 결과(상태전이/차단/cascade/순환)를 체험**할 수 있어야 한다 — `state` 객체 + `render()` + `action()` 으로 그 슬라이스의 도메인 규칙을 JS 로 시뮬레이션(서버 판정을 재현). 선례: `plan/26-05-30_10-55-11_R2-2_plan.html`(부모상태→자식버튼 disable+tooltip), `plan/26-06-28_03-33-59_R4-3_R5-3_R6-3_plan.html`(분해+순환 가드).
- **필수 섹션(11)**: ①현재 상태 ②요구사항 ③URL/데이터 흐름 ④도메인 모델 ⑤10단계 매핑 ⑥Step 8 테스트 시나리오 ⑦예외 계층(신규·재사용) ⑧부산물/주의(scope 경계·미루는 리팩터·CLAUDE.md 수정·동반 수정) ⑨Verification(빌드·기능·회귀) ⑩Critical Files(신규·수정·유지) ⑪다음 마일스톤.

### 테스트 규율 (불가침)
단위 테스트만으로는 "예외→HTTP 응답" 매핑 사고나 컨트롤러 분기 누락이 안 드러난다(과거 `MissingFilenameException` 이 500 으로 새던 사고). Step 8 은 **두 레이어 모두** 작성:
1. **단위 테스트** — Service 분기별 happy 1 + 실패 1 (JUnit + Mockito).
2. **사용자 액션 통합 테스트** — 각 엔드포인트의 모든 액션을 HTTP 계층에서 실제 상태코드·바디로 검증(`@WebMvcTest` + `MockMvc` + `@MockitoBean`). 컨트롤러 단위 파일 분리(`*ControllerUploadFlowTest` 등). 4 범주 필수: **성공 2xx**(바디 필드값) / **400**(필드 메시지) / **409**(모든 `ConflictException` 하위 실제 트리거) / **404**(모든 `NotFoundException` 하위, forging 포함). Mockito mocking 은 Service 단까지만 — 컨트롤러 try/catch + advice 매핑은 실제 실행되어야 한다.
- **새 예외 클래스를 추가하면 그 예외를 발생시키는 시나리오 테스트를 반드시 함께 추가**한다. 단위만 있고 통합 시나리오가 빠진 묶음은 CP4 승인 대상이 아니다.

### 체크포인트 ↔ Notion 동기화 (background agent 위임, 불가침)
- 각 CP 보고 **직후** 1회, 해당 슬라이스가 Notion DB 에 존재하면 상태/일정 갱신을 **background agent 에 위임**(`Agent` `run_in_background:true`). main loop 은 결과를 안 기다리고 승인 대기/다음 작업을 잇는다.
- **시작 경계**(CP1/CP2 진입): 해당+상위 단계 `상태`='진행 중', `시작 일자`=당일(KST), 이미 진행 중이면 no-op. **종료 경계**(CP5 완료 통보 후): `상태`='완료', `종료 일자`=완료일(KST). **중간(CP2~CP4)**: 상태/일정 변화 없으면 생략. **`완료` 처리는 CP5 사용자 완료 통보 이후에만** — 임의 선완료 금지.
- Notion 에 단계가 없으면 위임 생략(임의 신설 금지).

### Notion 작업 규약
- 페이지 'Provisioning Server', DB 'Provisioning Server 개발 상세'. 댓글은 `[Claude]` 접두사.
- **페이지 신설/상태 갱신/scope 변경 시 본문(content)에 4 항목 필수 기재(불가침)**: ① scope 요약 ② 비 목표(out of scope + 다음 슬라이스 어디로) ③ 잔존 책임/임시 비대칭 + 해소 시점 ④ 후속 마일스톤. plan/report 는 별도 자산 — Notion 페이지는 단독으로도 슬라이스 의도를 파악할 수 있어야 한다. 본문을 빈 채로 두지 않는다.

### 코드 소유권 · 커밋 경계 (불가침)
- **커밋은 사용자 지시가 있을 때만.** 필요하면 제안하고 승인을 기다린다 — 절대 임의 커밋하지 않는다.
- 커밋 메시지에 AI 참여를 명시한다(GitHub 에서 Claude Code 참여가 드러나도록). 페이지 완성 전 임시 커밋 금지(스키마 drop 선행 커밋 1개는 허용).
- 각 슬라이스의 **2~9 단계만 Claude 가 작성**, 10 단계(브라우저 E2E)는 사용자 단독. plan 범위 밖 코드 이동/공통화는 사용자 명시 요청 시에만.

## 빌드 · 실행 · DB
- 빌드/실행 명령과 환경변수는 **`CLAUDE.local.md`** 참고(`SERVER_PORT`/`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 등 주입 필수).
- **로컬 테스트 DB 계정 `readonly_user`**(SELECT 전용, `server_provision`@localhost:3306, pw `readonly_claude`) — 읽기/필드검증 경로 회귀용. INSERT/UPDATE/DELETE 경로는 JPA 단계에서 `INSERT command denied` → 500 으로 끊긴다(코드 버그 아닌 권한 제약, 서버 로그 SQL 예외로 구분). 쓰기 회귀는 쓰기 권한 유저를 별도 주입.
- macOS 환경: `timeout` 명령 없음. bootRun 은 서버라 자체 종료 안 함 — detached 실행 후 로그 폴링으로 마커 확인.

## 아카이브 (`archive/legacy/`)
구 `dev` 브랜치 구현의 참조용 복사본. **실행 경로 아님.** 엔티티 필드/컨트롤러 구조/Thymeleaf 구조는 참고 가능하나 스타일·관례는 **답습하지 않고** 본 파일의 원칙을 따른다.
