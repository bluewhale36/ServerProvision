# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 작업 규칙

### 코딩 스타일
- 코드 주석은 한국어로 작성한다.
- 최신 Java (17+) 기능과 Spring Boot 4.x 관례를 적극 활용한다.
- Git 커밋 메시지는 한국어로 작성한다.
- 프론트엔드와 백엔드가 적절히 결합되어야 하거나, 백엔드를 활용하여 구현 가능한 프론트엔드의 기능은 백엔드에 기능 구현을 더 집중하되, 지나치게 복잡해지지 않도록 적절히 분리한다.

### 네이밍 규칙
- 패키지명은 모두 소문자, 도메인 기반으로 구성한다 (예: `com.example.serverprovision`).
- 일반 Controller 클래스는 `*Controller`, RestController 클래스는 `*RestController`, 서비스 클래스는 `*Service`, 레포지토리는 `*Repository`로 명명한다.
- DTO 클래스는 `*DTO`, 엔티티 클래스는 `*Entity`, 열거형은 그 도메인에서 사용되는 목적을 직접 드러내는 명칭으로 명명한다.

### UI 디자인
- DESIGN.md 파일에 명세된 UI 디자인을 엄격히 준수한다.

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행 (환경변수 필수)
SERVER_PORT=7779 DB_URL=jdbc:mariadb://localhost:3306/server_provision DB_USERNAME=readonly_user DB_PASSWORD=readonly_claude ./gradlew bootRun

# 전체 테스트
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.example.serverprovision.SomeTest"
```

DB 및 포트 설정은 모두 환경변수(`SERVER_PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`)로 주입한다. 기본값이 없으므로 환경변수 없이는 앱이 시작되지 않는다.

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
- **Spring MVC + Thymeleaf** — 관리자 UI는 `@ModelAttribute` + `BindingResult` 폼 제출 방식
- **Spring Data JPA** + MariaDB
- **Lombok** — `@Builder`, `@Getter`, `@RequiredArgsConstructor` 등 전반적으로 사용

## 아키텍처

### 패키지 구조

```
com.example.serverprovision/
├── application/
│   ├── admin/controller/        # MVC 컨트롤러: AdminController, OSAdminController
│   └── setting/                 # 세팅 주문서 기능 (핵심 유스케이스)
│       ├── controller/          # SettingController (MVC + REST 혼합)
│       ├── service/             # SettingService
│       ├── repository/          # SettingRepository
│       ├── converter/           # SettingProcessConverter (JPA AttributeConverter → JSON)
│       ├── domain/entity/       # ServerSetting 엔티티
│       └── model/               # AbstractSettingProcess + 4개 구현체 + 열거형
├── domain/
│   ├── board/                   # BoardModel, BoardBIOS, BoardBMC (엔티티/레포/서비스/DTO)
│   ├── node/                    # ServerNode (엔티티/레포/서비스/DTO + 열거형)
│   ├── os/                      # OSMetadata, OSEnvironment, OSPackageGroup + 설치 모델
│   └── provisioning/            # PXEBootRestController + ProvisioningScriptService + 전략
└── global/entity/               # BaseTimeEntity (@MappedSuperclass, JPA Auditing)
```

### 핵심 데이터 모델

**ServerNode** — 물리 서버를 표현하는 중심 엔티티.
- MAC 주소 (PXE 부팅 요청의 기준 식별자)
- IPMI 원격 제어 정보
- `BoardModel` FK (다대일)
- `ServerSetting` FK (다대일) — 할당된 "작업 지시서"
- `currentStepIndex` — 세팅 프로세스 리스트의 0-기반 배열 인덱스. 단계 완료 시 증가.

**ServerSetting** — `SettingProcess`를 DB 컬럼 `process`에 JSON 문자열로 저장한다. `SettingProcessConverter`가 직렬화/역직렬화를 담당한다. `SettingProcess`는 `List<AbstractSettingProcess>`를 감싸며, `processStep.getOrder()` 기준으로 정렬된다.

### 다형성 세팅 프로세스 (`application/setting/model/`)

`AbstractSettingProcess`는 `@JsonTypeInfo` + `@JsonSubTypes`로 다형성 JSON 역직렬화를 구현한다. 구현체 4종:

| 클래스 | `type` 판별자 | 역할 |
|---|---|---|
| `BasicUpdate` | `BASIC_UPDATES` | `BoardModelDTO`, `BoardBIOSDTO`, `BoardBMCDTO` 포함 |
| `BasicSetting` | `BASIC_SETTING` | BIOS 설정 (미구현 스텁) |
| `OSInstallation` | `OS_INSTALLATION` | `OSMetadataDTO` + 도메인 `OSInstallation` 모델 래핑 |
| `OSSetting` | `OS_SETTING` | OS 후처리 설정 (미구현 스텁) |

`SettingProcessStep` 열거형이 `.getOrder()`로 실행 순서를 정의한다. order 값은 `1, 2, 4, 5` (3번은 예약/주석 처리).

### OS 설치 모델 계층 (`domain/os/model/installation/`)

`OSInstallation`(추상) → `LinuxInstallation`(추상) → `RockyLinuxInstallation`(구현체)

`LinuxInstallation` 생성 시 검증:
- `partitions`에 필수 마운트포인트 `/`, `/boot`, `/boot/efi`, `swap` 포함 여부
- `users`에 root 사용자 1개 이상 포함 여부

`Partition`, `User`, `Environment`, `Timezone` 각각 `InstallScriptable`을 구현하며 `getRHELScript()`로 Kickstart(`.ks`) 스크립트 조각을 생성한다. `RockyLinuxInstallation.getKickstartScript()`에서 이 조각들을 조합해 완성된 스크립트를 반환해야 한다.

`OSTemplate` 기반 클래스의 `isCompatible(OSName, String version)`은 `application/setting/model/OSInstallation`이 선택된 OS 메타데이터와 설치 설정의 호환성을 교차 검증할 때 사용한다.

### PXE 부팅 흐름

1. 물리 서버 부팅 → `GET /pxe/v1/api/boot?mac=<mac>&vendor=<v>&board-model=<m>` 호출
2. `PXEBootRestController` → `ServerNodeService.getOrRegisterNode()` (미등록 MAC은 자동 등록)
3. `ProvisioningScriptService.generateIPXEScript()` → `node.getCurrentStepIndex()`로 현재 단계 확인
4. 일치하는 `ProvisioningStrategy` 구현체에 위임 (Strategy 패턴, Spring이 `List<ProvisioningStrategy>` 주입)
5. 세팅 미할당 또는 모든 단계 완료 시 로컬 디스크 부팅 스크립트 반환

### 프론트엔드 패턴

- 관리자 OS CRUD (`/pxe/v1/admin/os/*`): `@ModelAttribute` + `BindingResult` 폼 제출 방식
- 세팅 생성 (`/pxe/v1/setting/new`): JS에서 JSON 조립 후 Fetch API로 `POST /pxe/v1/setting/api/new` 전송
- 보드 모델별 BIOS/BMC 옵션은 Thymeleaf로 `data-*` 속성에 JSON 직렬화 (`th:data-bios="${#json.serialize(view.boardBIOSList)}"`)하여, JS에서 셀렉트 변경 이벤트 시 파싱

## 미구현 영역

- `SettingService` 가 비어 있음 — `ServerSetting` 저장 로직 미연결
- `BasicSetting`, `OSSetting` (application 계층) — 필드 없는 스텁
- `ProvisioningStrategy` 구현체 없음 — 로컬 부팅 외 모든 프로비저닝 케이스에서 런타임 예외 발생
- `RockyLinuxSetting` — 생성자 스텁만 존재
