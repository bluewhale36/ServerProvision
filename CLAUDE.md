# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**현재 상태**: `renew/main` 브랜치에서 Top-down 재설계 진행 중. 기존 `dev` 브랜치 구현은 `archive/legacy/` 에 참조용으로 보존. 상세 플랜은 `.claude/plans/server-provision-peppy-lightning.md` 또는 `plan/*.docx` 에 있다.

## 작업 규칙

### 코딩 스타일
- 코드 주석은 한국어로 작성한다.
- 최신 Java (17+) 기능과 Spring Boot 4.x 관례를 적극 활용한다.
- Git 커밋 메시지는 한국어로 작성한다.
- 프론트엔드와 백엔드가 적절히 결합되어야 하거나, 백엔드를 활용하여 구현 가능한 프론트엔드의 기능은 백엔드에 기능 구현을 더 집중하되, 지나치게 복잡해지지 않도록 적절히 분리한다.
- WHY 가 비자명할 때만 주석을 남긴다. 의미 없는 Javadoc 금지.
- **중복된 코드와 가독성이 떨어지는 코드는 유지보수에 매우 까다로우므로 절대 지양한다 (불가침)**.
  - 동일 로직이 두 곳 이상에 복붙으로 존재하면 즉시 공통 모듈/유틸/fragment 로 추출한다 (예: `path-browser.js` / `bundle-upload-shell.js` / `directory-browse-panel.html` 처럼).
  - 한 함수 안에 검증 / 변환 / IO / 응답 조립이 뒤섞이면 작은 함수로 쪼갠다.
  - 매직 상수 / 무명 boolean 인자 / 중첩 5단계 이상 / 100줄 초과 메서드는 모두 가독성 저해 신호 — 발견 즉시 정비한다.
  - 중복은 "지금은 비슷해 보이지만 미래에 갈라질 수 있어 분리해두는 게 안전" 류의 변명으로 정당화하지 않는다 — 갈라지는 시점에 분리하면 된다.
  - 단, 분리하려는 추상이 도메인을 가로질러 의미를 잃게 하면 분리하지 않는다 (over-abstraction 도 동일하게 유지보수 비용).

- **조건 분기문 (try-catch / if-else / switch case) 의 legacy 무분별 확장 절대 지양 (불가침)**.
  - 신규 케이스 / 신규 예외 / 신규 도메인 타입이 추가될 때 분기문에 줄을 늘리는 방식으로 처리하지 않는다. 분기문은 도메인 의미가 추가될 때마다 거의 항상 같이 자라며, 누락 / 회귀 / silent 흡수 사고의 진원지가 된다 (S3 + S4 사이 `catch (DomainException)` 가 보안 예외 흡수해 silent 500 으로 새던 사고가 대표 사례).
  - 대신 **Java 의 다형성** (interface / abstract class / sealed type / enum 의 method-per-constant / strategy pattern) 과 **Spring Framework primitive** (`@ControllerAdvice` / `@RestControllerAdvice` / `HandlerExceptionResolver` / `@ResponseStatus` / `BindingResult` 자동 매핑 / AOP / `@EventListener` / `MessageSource` / `Converter` / `HandlerMethodArgumentResolver`) 를 적극 활용해 framework 측이 분기를 떠맡도록 설계한다.
  - 신규 분기문 추가가 **유일한 옵션** 이라면 직전에 다음 질문을 던져본다 :
    1. 이 분기는 도메인 다형성으로 표현 가능한가? (예: 7 보안 예외별 HTTP status → enum 의 abstract method 또는 `@ResponseStatus` 어노테이션)
    2. Spring 이 이미 제공하는 framework primitive 가 있는가? (예: SSR/XHR 응답 분기 → `@ControllerAdvice` + `@RestControllerAdvice` 분리, 컨트롤러별 try/catch 대신 `HandlerExceptionResolver` 단일 진입점)
    3. 분기 조건이 곧 새 sub-class 의 책임으로 흡수될 수 있는가? (Open/Closed Principle 준수)
  - 위 세 질문 중 하나라도 yes 라면 분기문 추가 대신 다형성 / framework 활용으로 전환한다.
  - 컨트롤러의 try/catch 블록은 특히 경계해야 한다. 신규 컨트롤러 추가 시 같은 catch 블록을 또 복붙하는 회귀 사고가 본 프로젝트에서 반복적으로 발생했다 (S3.3 → S3.4 의 4 컨트롤러 multi-catch 누락 사고). framework 단의 advice / resolver 로 끌어올린다.
  - 단, **도메인 invariant 검증의 imperative if-throw** 는 정당한 사용처다 (예: `if (token == null) throw InvalidTokenException`) — 이 항목은 분기문 자체를 금지하는 것이 아니라 **분기문 줄 추가로 책임을 늘리는 패턴** 을 금지한다.

### 아키텍처 설계 시 주의점
- 서버의 리소스를 효율적으로 사용하도록 할 것. 예를 들어, 대용량 파일 업로드 시 스트리밍 처리, 불필요한 데이터 로딩, 비교 방지 등.
- 보안 고려: 민감한 정보(예: IPMI 자격 증명)는 안전하게 저장하고 전송한다. 예를 들어, 데이터베이스에 암호화하여 저장하거나, 환경 변수로 관리한다.
- 확장성 고려: 향후 새로운 기능이나 자원 유형이 추가될 수 있으므로, 유연한 설계를 지향한다. 예를 들어, 세팅 정의서의 프로세스 단계가 다양해질 수 있으므로, 다형성을 활용하여 확장 가능한 구조로 설계한다.
- 에러 처리: 예상 가능한 예외 상황에 대한 명확한 에러 메시지와 HTTP 상태 코드를 반환하도록 한다. 예를 들어, 권한 부족 시 403, 잘못된 입력 시 400, 서버 오류 시 500 등을 적절히 사용한다.

### 네이밍 규칙
- 패키지명은 모두 소문자, **feature-first** 로 구성한다 (예: `com.example.serverprovision.management.os`).
- 클래스 접미사:
  - MVC Controller: `*Controller`
  - REST Controller: `*RestController`
  - Service: `*Service`
  - Repository: `*Repository`
  - 요청 DTO: `*Request` (접미사 `DTO` 사용하지 않음)
  - 응답 DTO: `*Response`
  - 엔티티: `*Entity`
  - 열거형: 도메인 목적을 직접 드러내는 명칭 (예: `OSName`, `Vendor`, `ProvisioningStatus`)
  - Value Object: 도메인 개념 기반 (예: `MacAddress`, `IpAddress`, `ProvisioningProgress`)

### Primitive Obsession 금지 (불가침 원칙)
- 도메인 의미가 있는 값(MAC, IP, 버전, 진행률, 파일 경로 등)은 **반드시 Value Object 또는 Enum 으로 타입화** 한다.
- `int currentStepIndex`, `String mac` 같이 **주요 비즈니스 상태를 원시 필드로 표현/전달하지 않는다**.
- 엔티티 필드, Service 메서드 시그니처, Request/Response 필드 모두에 동일하게 적용.
- 매 코드 작성 시 "이 원시값이 도메인 의미를 가지는가" 를 점검한다.

### UI 디자인
- `DESIGN.md` 파일에 명세된 UI 디자인을 엄격히 준수한다.
- 기존 CSS 파일 재사용이 원칙: `static/css/global/style.css`, `miller.css`, `table-list.css`, `form-validation.css`.
- 신규 CSS 파일 추가는 Gate 승인 후에만.

### 코드 소유권
- **커밋은 사용자 지시가 있을 때만 수행**. Claude 는 `git commit` 을 자동 호출하지 않는다.
- 각 페이지 수직 슬라이스의 **2~9 단계까지만 Claude 가 작성**한다. 10 단계(브라우저 end-to-end) 는 사용자 단독 수행.
- 파일 헤더에 AI 작성 마커 주석(`// Claude`, `// AI-generated` 등) 금지.
- 플랜 범위 밖의 코드 이동/공통화는 사용자 명시 요청 시에만.

## 빌드 및 실행

`CLAUDE.local.md` 파일 참고.

### 로컬 테스트 DB 계정

#### `readonly_user` — SELECT 전용
- **용도**: Claude Code 의 로컬 자체 테스트에서 읽기 경로 + 필드 검증 경로 회귀.
- **접근 가능 DB**: `server_provision` (localhost:3306) 에만 권한 있음.
- **환경변수**:
  ```
  DB_URL=jdbc:mariadb://localhost:3306/server_provision
  DB_USERNAME=readonly_user
  DB_PASSWORD=readonly_claude
  ```
- **한계**: INSERT/UPDATE/DELETE 가 필요한 경로는 JPA 단계에서 `INSERT command denied` 로 실패하여 500 `INTERNAL_ERROR` 로 끊긴다. 이 실패는 코드 버그가 **아니라** DB 권한 제약이며, 서버 로그의 SQL 예외로 구분 가능하다. 성공 경로의 end-to-end 영속화 회귀가 필요하면 쓰기 권한 유저를 별도로 주입해야 한다.

## 기술 스택

- **Spring Boot 4.x** + **Jackson 3** — 어노테이션(`@JsonCreator`, `@JsonProperty`, `@JsonTypeInfo`, `@JsonSubTypes` 등)은 backward-compat 용도로 `com.fasterxml.jackson.annotation.*` 에 유지되지만, 런타임 클래스(`ObjectMapper`, `JacksonException` 등 `core`/`databind` 패키지)는 **`tools.jackson.*`** 를 사용한다. `com.fasterxml.jackson.core/databind` 는 클래스패스에 없다.
- **Spring MVC + Thymeleaf** — 관리자 UI 는 `@ModelAttribute` + `BindingResult` 폼 제출 방식
- **Spring Data JPA** + MariaDB
- **Lombok** — `@Builder`, `@Getter`, `@RequiredArgsConstructor` 등 전반적으로 사용

## 아키텍처

### 영역 분할 (3 도메인 영역 + 진입점 + 글로벌)

- **Management** (`/management/*`) — 어플리케이션 자원 관리 (Manage-Application). OS / Board / BIOS / BMC / Driver
- **Maintenance** (`/maintenance/*`) — 어플리케이션-서버 간 운영 (Manage-Kernel). 경로 재조정 등 자가 점검·복구
- **Provisioning** (`/provisioning/*`) — 사용자 영역. 세팅 정의서, 서버
- **Entry** (`/pxe/v1/entry/boot`) — 물리 서버 PXE 부팅 요청 진입점 (Stage 4 도입). **다른 영역과 달리 `/pxe/v1` prefix 유지** — PXE 클라이언트 호환성
- **Jobs** (`/jobs/*`) — BackgroundJob 글로벌 인프라 (S1, root 직속)

### 패키지 구조 (feature-first)

```
com.example.serverprovision/
├── management/                    # 어플리케이션 자원 관리 (Manage-Application)
│   ├── os/           # OSImage + ISO (1:N) + OSEnvironment + OSPackageGroup
│   ├── board/        # BoardModel
│   ├── bios/         # BoardBIOS (.provision.json 마커 + HMAC)
│   ├── bmc/          # BoardBMC                  (MA4)
│   └── driver/       # DriverPackage             (MA5)
├── maintenance/                   # 어플리케이션-서버 운영 (Manage-Kernel)
│   └── reconciliation/            # PathReconciliationService (MK1)
├── provisioning/                  # 사용자 영역
│   ├── setting/      # SettingDefinition + SettingProcess 다형성
│   ├── server/       # Server + 세팅 할당
│   └── pxe/          # (Stage 4) PXEBootRestController, ProvisioningStrategy, NodeStepExecution, ProvisioningProgressService
├── execution/                     # (예약, Stage 4 진입 시점에 결정)
└── global/
    ├── marker/       # ProvisionMarkerService, MarkerContent, Markable, MarkableScanner (MK1 후 추가)
    ├── job/          # BackgroundJob 인프라 (S1)
    ├── exception/    # 도메인 예외 + @ControllerAdvice + ApiErrorResponse
    ├── entity/       # BaseTimeEntity (JPA Auditing)
    └── config/       # AsyncConfig 등
```

각 feature 하위는 `controller/`, `service/`, `repository/`, `entity/`, `vo/`, `dto/`, `enums/`, `exception/` 레이어로 세분화. Value Object(`@Embeddable` / record) 는 `vo/` 로 **entity 와 물리적으로 분리**한다 — 엔티티 파일이 쏟아질수록 VO 와의 혼재가 가독성을 떨어뜨리기 때문.

### 핵심 도메인 모델

#### Management (어플리케이션 자원)
- **OSImage** — `id`, `osName`(`OSName` Enum), `osVersion`, `description`, `isEnabled`. 1:N `ISO`.
- **ISO** — `id`, `osImage` FK, `isoPath`, `description`, `isEnabled`. 한 OS 버전이 여러 ISO 를 가질 수 있다. (MK1 후 `markerSignature` / `manifestHash` 추가)
- **BoardModel** — `id`, `vendor`(`Vendor` Enum), `model`, `description`, `isEnabled`. 1:N `BoardBIOS` / `BoardBMC` / `DriverPackage`.
- **BoardBIOS** — `id`, `name`, `version`, `treeRootPath`, `entrypointRelativePath`, `manifestHash`, `markerSignature`(nullable, 2-phase save), `description`, `isEnabled`, `isDeleted`, `boardModel` FK. v3 번들 + `.provision.json` 마커.
- **BoardBMC** (MA4) — `id`, `name`, `version`, `fileUrl`, `description`, `isEnabled`, `boardModel` FK + Markable 구현 (sidecar 마커).
- **DriverPackage** (MA5) — `id`, `name`, `version`, `boardModel` FK (**nullable**; null 일 때 "공용"), `filePath`, `description`, `isEnabled` + Markable 구현.

#### Maintenance (어플리케이션-서버 운영)
- **DriftReport** (record, MK1) — `Instant scannedAt`, `Duration scanDuration`, `boolean deep`, `int totalChecked`, `List<Drift> drifts`. 메모리 보관, 영속화 안 함.
- **Drift** (record, MK1) — 1건 드리프트. `driftId`, `resourceType`, `resourceId`, `oldPath`, `newPath`, `kind`, `detectedAt`, `detail`.

#### Provisioning (사용자 영역)
- **SettingDefinition** — `id`, `name`, `process`(JSON, `SettingProcessConverter` 담당), `status`(`PENDING`/`IN_PROGRESS`/`COMPLETED`/`FAILED`).
- **SettingProcess 다형성** — `AbstractSettingProcess` + `BasicUpdate` + `OSInstallation` (Jackson `@JsonTypeInfo`, `@JsonSubTypes`). MVP 범위 외 `BasicSetting` / `OSSetting` 은 스텁으로 두고 Stage 4 에서 채운다.
- **Server** — `macAddress`(`MacAddress` VO), `ipmiIp`(`IpAddress` VO), `ipmiUser`, `ipmiPassword`, `hostname`, `assignedIp`(`IpAddress` VO), `boardModel` FK, `settingDefinition` FK(nullable), `status`(`IDLE`/`ASSIGNED`/`IN_PROGRESS`/`DONE`/`FAILED`), `assignedAt`, `completedAt`. **`currentStepIndex` 없음** (Primitive Obsession 금지 원칙 적용).
- **NodeStepExecution** (Stage 4) — `Server` 의 단계 실행 이력.
- **ProvisioningProgress** (VO, Stage 4) — "다음 수행 단계" 를 타입화해 표현. `ProvisioningProgressService` 가 `NodeStepExecution` 이력을 바탕으로 계산해 반환.

#### 마커 인프라 (`global/marker/`, MK1 후)
- **MarkerContent** (record) — `resourceType`, `resourceId`, `attributes(Map)`, `createdAt`, `manifestHash`, `signature`. 도메인 무관.
- **ResourceType** (Enum) — `BIOS_BUNDLE(IN_TREE)`, `OS_ISO(SIDECAR)`, `BMC_FIRMWARE(SIDECAR)`, `DRIVER(?)`. 각 상수가 default `MarkerLayout` 보유.
- **Markable** (interface) — DB 엔티티 어댑터. `getResourceId / getResourceType / getResourcePath / getMarkerLayout / getManifestHash / getMarkerSignature / reissueMarker`.

### 레이어 경계
- Controller ↔ Service: `*Request` / `*Response` 만 주고받는다.
- Service ↔ Repository: 엔티티 직접 사용.
- 뷰(Thymeleaf Model) 에 엔티티를 직접 노출하지 않는다.
- `@Transactional` 은 Service 메서드 경계. Controller 에 `@Transactional` 금지.

### 예외 / 입력 검증
- 도메인 예외는 `global/exception/` 에 정의.
- `@ControllerAdvice` 하나로 예외 → HTTP 응답 변환 통합.
- 입력 검증은 `@Valid` + `BindingResult` 조합.

## 개발 흐름

### Stage
- **Stage 0** — 초기화 (archive/legacy/ 이동 + 빈 스켈레톤 + `./gradlew bootRun` smoke)
- **Stage 1** — Management (MA1 OS 이미지 → MA1-1 환경/그룹 추출 → [S1 Background Job] → MA2 메인보드 모델 → MA3 BIOS → [M0 리네임] → [Stage 2 / MK1] → MA4 BMC → MA5 Driver)
- **Stage 2** — Maintenance (MK1 경로 재조정 — Stage 1 의 MA3 직후 진입. 어플리케이션-서버 운영)
- **Stage 3** — Provisioning (U1 세팅 정의서 → U2 서버)
- **Stage 4** — PXE Boot 연동 (PXEBootRestController, ProvisioningStrategy, Kickstart, NodeStepExecution, ProvisioningProgressService)

> **참고**: Stage 1 과 Stage 2 의 진행은 직선이 아니다. MA3 가 끝나면 M0 리네임 → MK1 (Stage 2) 가 먼저 들어가고, 이후 Stage 1 의 MA4 / MA5 가 마저 진행된다. Stage 번호는 도메인 묶음 식별자이지 실행 순서가 아니다.

### 인벤토리 코드 어휘
- **MA1 ~ MA5** — Manage-Application (Stage 1 Management)
- **MK1 ~ MK3** — Manage-Kernel (Stage 2 Maintenance)
  - **MK1** — 경로 재조정 (완료)
  - **MK2** — Soft-delete / Restore / Purge 정합화 (사용자 명시 결정 — 예정 / 미진입). BIOS/BMC/Subprogram 의 자동 hard-delete 정책 vs ISO 의 sidecar 충돌 사고를 일관 정책으로 통합 + UI "영구 삭제" 액션 도입
  - **MK3** — ApplicationFileMover (예정 / 미진입). UI 에서 파일을 옮기면 마커 + DB 가 동시 갱신. MK1 의 `applyDriftedPath` 를 역방향으로 호출
  - 그 외 후보 (마커 재발급 마이그레이션 / 분산 스캔 / Stage 4 자원 강제 검증) 는 인벤토리 외 — `plan/26-04-28_21-33-00_MK_expected_plan.docx` 에 archived
- **U1 ~ U2** — User (Stage 3 Provisioning)
- **S1** — Cross-cutting infrastructure (Background Job)
- **S3 / S3.1 / S3.2** — Cross-cutting Security Hardening
- **M0** — Cross-cutting 1회성 리네임 슬라이스 (영역 재구성)
- **CH1 ~ CH4** — Cross-cutting Housekeeping (정리 / 정합화)

### 수직 슬라이스 (페이지당 10 단계)
1. URL / 데이터 흐름 스케치 — **`plan/YY-MM-DD_HH-MM-SS_<페이지키>_plan.docx` 산출** (아래 §Step 1 — plan docx 규약 참조)
2. Thymeleaf 뷰 (더미 데이터, `fragments/layout.html` + 기존 CSS 재사용)
3. Controller (`@ModelAttribute` + `BindingResult`, Model 에 Response 만)
4. Request / Response DTO (`@Valid`)
5. Service 인터페이스 + 시그니처 (`@Transactional` 경계)
6. Repository (Spring Data JPA 메서드 네임 규칙 위주)
7. Entity (`BaseTimeEntity` 상속) — **7 단계 이전 `@Entity` 작성 금지**
8. Service 본체 + 단위 테스트 (happy 1 + 실패 1) + **사용자 액션 기반 통합 테스트** (아래 규칙 참조)
9. 스키마 확인 (`ddl-auto=update` 로컬 반영, `SHOW CREATE TABLE` 로그 확인)
10. 브라우저 end-to-end — **사용자 단독 수행**

### 테스트 규율 (불가침)

단위 테스트만으로는 "예외 → HTTP 응답" 매핑 사고나 컨트롤러 분기 누락 같은 문제가 드러나지 않는다.
Stage S1 에서 `MissingFilenameException` 이 500 으로 새는 사고가 대표적 예시. 단위 테스트는 통과했지만
실제 사용자 액션을 따라가는 테스트가 없어서 프로덕션 경로의 상태 코드를 놓쳤다.

Step 8 의 테스트는 **두 레이어를 모두** 작성한다:

1. **단위 테스트** — Service 본체의 분기별 happy 1 + 실패 1. JUnit + Mockito.
2. **사용자 액션 기반 통합 테스트** — 각 페이지의 사용자가 수행할 수 있는 **모든 액션**을 HTTP 계층에서
   실제 상태 코드·응답 바디로 검증한다. Spring `@WebMvcTest` + `MockMvc` + `@MockitoBean` 조합.

#### 사용자 액션 기반 통합 테스트 작성 규칙

- **컨트롤러 단위** 로 파일 분리 : `*ControllerUploadFlowTest`, `*ControllerExtractFlowTest` 등 플로우별.
- **시나리오 커버리지** : 각 엔드포인트에 대해 아래 네 범주를 모두 포함한다.
  - **성공 경로** — 2xx + 응답 바디 필드 값 검증
  - **입력 검증 실패** — 400 + 필드 메시지
  - **도메인 충돌** — 409 (모든 `ConflictException` 하위 클래스를 실제로 트리거)
  - **리소스 없음** — 404 (모든 `NotFoundException` 하위 클래스)
- **Mockito mocking** 은 Service 단까지만. Controller 내부의 try/catch + GlobalExceptionHandler 매핑은
  실제로 실행되어야 한다.
- 새 예외 클래스를 추가하면 **해당 예외를 발생시키는 시나리오 테스트를 반드시 함께 추가**한다.
  추가하지 않으면 "개발자의 예상" 과 "실제 HTTP 응답" 이 어긋난 채 머문다.
- 참고 선례 : `OSImageControllerUploadFlowTest` (Intent 6 / Upload 6 / Extract 3 = 15 시나리오).

이 규율을 어긴 테스트 묶음은 CP4 승인 대상이 아니다. 단위 테스트만 있고 통합 시나리오가 누락됐다면
사용자는 해당 CP 를 거절한다.

### Step 1 — plan docx 규약 (불가침)

Step 1 은 "대화로 스케치" 가 아니라 **공식 산출물을 문서화** 하는 단계다. 매 페이지(수직 슬라이스) 진입 시 Claude 는
아래 경로에 plan docx 를 생성하고 사용자의 CP1 승인을 받아야 한다. 이 문서 없이 Step 2 이후를 선행 구현하지 않는다.

- **경로** : `plan/YY-MM-DD_HH:MM:SS_<페이지키>_plan.docx` (예 : `plan/26-04-25_10:19:09_MK1_plan.docx`)
- **생성 방식** : `/tmp/<slug>.md` 에 markdown 작성 → `pandoc /tmp/<slug>.md -o plan/<filename>.docx` 로 변환. 기존 plan 파일명 규약을 그대로 따른다.
- **페이지키** : 인벤토리 코드 (`MA1`, `MA1-1`, `MA2`, `MA3`, `MA4`, `MA5`, `MK1`, `S1`, `U1`, `U2` 등). M0 이전 산출물은 구 코드 (`A1`, `A3` 등) 그대로 보존.

**필수 섹션** (참고 선례 : `plan/26-04-25_10:19:09_MK1_plan.docx`) :

1. **현재 상태 요약** — 선행 슬라이스 완료 / 미완 여부, 본 슬라이스 진입 전제 조건
2. **페이지 요구사항 (확정)** — UI 기능 / 제약 / 중복 방지 등 규칙
3. **URL / 데이터 흐름 스케치** — URL 표 (Method · URL · 동작 · 응답) + 브라우저↔서버 흐름도 + Miller / 응답 구조
4. **도메인 모델** — Entity 필드 표, VO / Enum, 도메인 메서드
5. **수직 슬라이스 10 단계** — 각 Step 의 산출물 + CP 대응
6. **Step 8 통합 테스트 시나리오** — 사용자 액션 범주별 (성공 · 400 · 404 · 409 · 500) 시나리오 목록과 시나리오 수
7. **예외 계층** — 신규 · 재사용 구분
8. **예상 부산물 / 주의** — 스코프 경계, 미루는 리팩터, CLAUDE.md 수정사항, 관련 페이지 동반 수정
9. **Verification 체크리스트** — 빌드 / 기능 / 회귀 3분할
10. **Critical Files** — 신규 / 수정 / 유지 3분할
11. **다음 마일스톤** — 후속 Gate 와 슬라이스 예고

이 구조를 따르면 CP1 에서 사용자가 "승인/수정/거절" 을 판별하기 쉽고, Step 8 테스트 시나리오가 초기에 합의되어
CP4 시점의 분쟁이 줄어든다. Claude 는 plan docx 를 만드는 방법을 묻지 않는다 — 기존 `plan/*.docx` 선례를 직접 읽고 따른다.

### 승인 흐름 (슬라이스 내부 체크포인트)

페이지별 10 단계는 아래 5 개의 체크포인트로 묶어 사용자 승인을 받은 뒤 다음 묶음으로 진행한다. 승인 없이 다음 묶음을 선행 구현하지 않는다.

| 체크포인트 | 범위 | 주체 / 동작 |
|---|---|---|
| **CP1** | Step 1 | Claude 가 `plan/YY-MM-DD_HH:MM:SS_<페이지키>_plan.docx` 를 생성하여 제시 → 사용자 승인 (§Step 1 — plan docx 규약 준수) |
| **CP2** | Step 2 ~ 5 | Claude 가 4 단계(뷰 · Controller · DTO · Service 시그니처)를 구현한 뒤 핵심 변경 사항을 요약 보고 → 사용자 승인 |
| **CP3** | Step 6 ~ 7 | Claude 가 Repository · Entity(+VO/Enum)를 구현한 뒤 보고 → 사용자 승인 |
| **CP4** | Step 8 ~ 9 | Claude 가 Service 본체 · 단위 테스트 · 스키마 확인까지 마친 뒤 보고 → 사용자 승인 |
| **CP5** | Step 10 | 사용자 단독으로 브라우저 E2E 수행 → 사용자 완료 통보 대기 |

체크포인트 승인이 떨어지기 전까지 다음 묶음 작업을 시작하지 않는다. 사용자가 수정 지시를 내리면 해당 체크포인트 범위 안에서 재작업 후 재승인을 받는다.

### 커밋 경계
- 10 단계 완료 후 단일 커밋: `feat(<area>/<page>): <페이지명> 1차 구현`
- 스키마 drop 필요 시 선행 커밋 `chore(schema): drop <table>` 1개 허용
- 페이지 완성 전 임시 커밋 금지

### Gate (리뷰 정지 지점)
- **G0** — Stage 0 완료 후
- **G-MA1** ~ **G-MA5** — MA1~MA5 각 페이지 완료 후 (G-MA5 = Stage 1 Management 종료)
- **G-MA1-1** — MA1-1 (환경/그룹 추출) 완료 후
- **G-S1** — Stage S1 Background Job 완료 후 (S1 명칭 유지)
- **G-M0** — M0 리네임 슬라이스 완료 후 (G-MA3 직후)
- **G-MK1** — Stage 2 / MK1 완료 후 (G-M0 직후)
- **G-U1** — U1 완료 후
- **G-U2** — U2 완료 후
- **G-Stage3-end** — Stage 3 Provisioning 종료 후 (Stage 4 PXE 진입 전)

각 Gate 에서 Claude 는 작성 범위 / 남은 부분 / 다음 할 일을 1~2 문단으로 정리해 제시하고 사용자 승인 신호를 기다린다.

## 아카이브 (`archive/legacy/`)

이전 `dev` 브랜치 구현의 참조용 복사본. **실행 경로에 포함되지 않는다.**

- 엔티티 필드 구성 / 컨트롤러 구조 / Thymeleaf 템플릿 구조는 참고 가능.
- 스타일이나 관례는 **답습하지 않는다** — 본 파일의 네이밍/아키텍처 원칙을 따른다.
- 주요 참조 대상: `archive/legacy/domain/board/**` (BoardModel/BIOS/BMC 엔티티), `archive/legacy/templates/admin/os/os-list.html` (Miller Columns 패턴).
