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

### 네이밍 규칙
- 패키지명은 모두 소문자, **feature-first** 로 구성한다 (예: `com.example.serverprovision.maintenance.os`).
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

### 영역 분할

- **Maintenance** (`/pxe/v1/maintenance/*`) — 관리자가 제공/관리하는 자원
- **Provisioning** (`/pxe/v1/provisioning/*`) — 세팅 정의서, 서버
- **Entry** (`/pxe/v1/entry/boot`) — 물리 서버 PXE 부팅 요청 진입점 (Stage 3 도입)

### 패키지 구조 (feature-first)

```
com.example.serverprovision/
├── maintenance/
│   ├── os/           # OSImage + ISO (1:N)
│   ├── board/        # BoardModel
│   ├── bios/         # BoardBIOS
│   ├── bmc/          # BoardBMC
│   └── driver/       # DriverPackage
├── provisioning/
│   ├── setting/      # SettingDefinition + SettingProcess 다형성
│   ├── server/       # Server + 세팅 할당
│   └── pxe/          # (Stage 3) PXEBootRestController, ProvisioningStrategy, NodeStepExecution, ProvisioningProgressService
└── global/
    ├── exception/    # 도메인 예외 + @ControllerAdvice
    ├── entity/       # BaseTimeEntity (JPA Auditing)
    └── config/       # AsyncConfig 등
```

각 feature 하위는 `controller/`, `service/`, `repository/`, `entity/`, `dto/` 레이어로 세분화.

### 핵심 도메인 모델

#### Maintenance
- **OSImage** — `id`, `osName`(`OSName` Enum), `osVersion`, `repoPath`, `description`, `isEnabled`. 1:N `ISO`.
- **ISO** — `id`, `osImage` FK, `isoPath`, `description`, `isEnabled`. 한 OS 버전이 여러 ISO 를 가질 수 있다.
- **BoardModel** — `id`, `vendor`(`Vendor` Enum), `model`, `description`, `isEnabled`. 1:N `BoardBIOS` / `BoardBMC` / `DriverPackage`.
- **BoardBIOS** / **BoardBMC** — `id`, `name`, `version`, `fileUrl`, `description`, `isEnabled`, `boardModel` FK.
- **DriverPackage** — `id`, `name`, `version`, `boardModel` FK (**nullable**; null 일 때 "공용"), `filePath`, `description`, `isEnabled`.

#### Provisioning
- **SettingDefinition** — `id`, `name`, `process`(JSON, `SettingProcessConverter` 담당), `status`(`PENDING`/`IN_PROGRESS`/`COMPLETED`/`FAILED`).
- **SettingProcess 다형성** — `AbstractSettingProcess` + `BasicUpdate` + `OSInstallation` (Jackson `@JsonTypeInfo`, `@JsonSubTypes`). MVP 범위 외 `BasicSetting` / `OSSetting` 은 스텁으로 두고 Stage 3 에서 채운다.
- **Server** — `macAddress`(`MacAddress` VO), `ipmiIp`(`IpAddress` VO), `ipmiUser`, `ipmiPassword`, `hostname`, `assignedIp`(`IpAddress` VO), `boardModel` FK, `settingDefinition` FK(nullable), `status`(`IDLE`/`ASSIGNED`/`IN_PROGRESS`/`DONE`/`FAILED`), `assignedAt`, `completedAt`. **`currentStepIndex` 없음** (Primitive Obsession 금지 원칙 적용).
- **NodeStepExecution** (Stage 3) — `Server` 의 단계 실행 이력.
- **ProvisioningProgress** (VO, Stage 3) — "다음 수행 단계" 를 타입화해 표현. `ProvisioningProgressService` 가 `NodeStepExecution` 이력을 바탕으로 계산해 반환.

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
- **Stage 1** — Maintenance (A1 OS 이미지 → A2 메인보드 모델 → A3 BIOS → A4 BMC → A5 Driver)
- **Stage 2** — Provisioning (U1 세팅 정의서 → U2 서버)
- **Stage 3** — PXE Boot 연동 (PXEBootRestController, ProvisioningStrategy, Kickstart, NodeStepExecution, ProvisioningProgressService)

### 수직 슬라이스 (페이지당 10 단계)
1. URL / 데이터 흐름 스케치 (대화로 합의, 파일 생성 X)
2. Thymeleaf 뷰 (더미 데이터, `fragments/layout.html` + 기존 CSS 재사용)
3. Controller (`@ModelAttribute` + `BindingResult`, Model 에 Response 만)
4. Request / Response DTO (`@Valid`)
5. Service 인터페이스 + 시그니처 (`@Transactional` 경계)
6. Repository (Spring Data JPA 메서드 네임 규칙 위주)
7. Entity (`BaseTimeEntity` 상속) — **7 단계 이전 `@Entity` 작성 금지**
8. Service 본체 + 단위 테스트 (happy 1 + 실패 1)
9. 스키마 확인 (`ddl-auto=update` 로컬 반영, `SHOW CREATE TABLE` 로그 확인)
10. 브라우저 end-to-end — **사용자 단독 수행**

### 커밋 경계
- 10 단계 완료 후 단일 커밋: `feat(<area>/<page>): <페이지명> 1차 구현`
- 스키마 drop 필요 시 선행 커밋 `chore(schema): drop <table>` 1개 허용
- 페이지 완성 전 임시 커밋 금지

### Gate (리뷰 정지 지점) — 총 9개
- **G0** — Stage 0 완료 후
- **G1~G5** — A1~A5 각 페이지 완료 후 (G5 = Maintenance 구간 종료)
- **G6** — U1 완료 후
- **G7** — U2 완료 후
- **G8** — Stage 2 종료 후 (Stage 3 진입 전)

각 Gate 에서 Claude 는 작성 범위 / 남은 부분 / 다음 할 일을 1~2 문단으로 정리해 제시하고 사용자 승인 신호를 기다린다.

## 아카이브 (`archive/legacy/`)

이전 `dev` 브랜치 구현의 참조용 복사본. **실행 경로에 포함되지 않는다.**

- 엔티티 필드 구성 / 컨트롤러 구조 / Thymeleaf 템플릿 구조는 참고 가능.
- 스타일이나 관례는 **답습하지 않는다** — 본 파일의 네이밍/아키텍처 원칙을 따른다.
- 주요 참조 대상: `archive/legacy/domain/board/**` (BoardModel/BIOS/BMC 엔티티), `archive/legacy/templates/admin/os/os-list.html` (Miller Columns 패턴).
