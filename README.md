# ServerProvision

![CI](https://github.com/bluewhale36/ServerProvision/actions/workflows/ci.yml/badge.svg)

ServerProvision은 데이터센터의 베어메탈 서버에 운영체제, 펌웨어, 드라이버를 자동으로 설치하고 설정하는 프로비저닝 관리 시스템이다. 운영자는 관리 화면에서 프로비저닝에 쓸 자원을 등록하고 세팅 정의서를 작성한다. 물리 서버는 전원을 켜면 네트워크 부팅(PXE, Preboot eXecution Environment)으로 시스템에 자동 등록되고, 세팅 정의서에 따라 프로비저닝된다. 관리 화면은 Thymeleaf 서버 사이드 렌더링으로 제공하고, 물리 서버와는 HTTP API로 통신한다. 스택은 Spring Boot 4(Java 21), Thymeleaf, Spring Data JPA, MariaDB다.

자원 등록부터 프로비저닝 개시까지는 동작한다. 개시 이후 서버 위에서 프로비저닝 단계를 실제로 실행하는 실행 엔진은 구현이 진행 중이다(아래 Execution 영역).

## 기능 영역

기능은 네 영역으로 나뉜다. 앞의 세 영역은 URL의 첫 경로 구간이 영역 이름과 일치하고, Execution은 물리 서버가 호출하는 기계 대상 API라 `/api/pxe/v1/*` 아래에 둔다. 영역에 속하지 않는 공용 인프라로 확인 모달 조각을 내리는 `/ui/*`와 백그라운드 작업 폴링용 `/jobs`가 있다. 관리자, 운영자, 사용자라는 구분은 화면 영역의 구분이지 계정의 구분이 아니다. 로그인과 권한 계층은 없으며 localhost 바인딩을 전제로 한다.

| 영역 | URL | 역할 |
|---|---|---|
| Management | `/management/*` | 프로비저닝에 쓸 자원을 등록하고 관리한다. OS 이미지와 ISO, 메인보드 모델, BIOS와 BMC 펌웨어, Subprogram(드라이버와 유틸리티)을 다룬다. |
| Maintenance | `/maintenance/*` | 시스템을 점검하고 복구한다. DB와 파일시스템의 어긋남 재조정, 업로드 실패 복구, 휴지통과 영구삭제 감사를 다룬다. |
| Provisioning | `/provisioning/*` | 세팅 정의서와 BIOS 세팅 템플릿을 작성하고, 게스트 서버를 확인하고 프로비저닝을 개시한다. |
| Execution | `/api/pxe/v1/*` | 물리 서버가 부팅 시 자동 등록하고 진행을 보고하는 API다. 실행 엔진은 구현이 진행 중이다. |

## 설계에서 눈여겨볼 점

코드만 봐서는 의도가 드러나지 않는 설계 판단을 먼저 정리한다. 근거의 전문은 [CLAUDE.md](CLAUDE.md)와 [docs/dev/architecture.md](docs/dev/architecture.md)에 있다.

### 테스트가 DB 없이 통과한다

테스트는 1,345건이다. 컨트롤러는 `@WebMvcTest` 40건으로 HTTP 계층을 실제로 태워 상태코드와 응답 바디를 검증하고, 리포지토리는 `@DataJpaTest` 1건으로 검증한다. `@SpringBootTest`는 쓰지 않는다(0건). 그래서 실행 중인 데이터베이스 없이 `./gradlew test`가 통과하고, GitHub Actions CI도 DB 서비스 없이 JDK 21에서 테스트만 돌린다.

HTTP 통합 테스트는 각 엔드포인트에서 성공(2xx), 검증 실패(400), 충돌(409), 없음(404)의 네 범주를 모두 채운다. 단위 테스트만으로는 도메인 예외가 어떤 HTTP 응답으로 매핑되는지, 컨트롤러 분기가 빠졌는지 드러나지 않기 때문이다. 새 예외 클래스를 추가하면 그 예외를 실제로 발생시키는 시나리오 테스트를 함께 넣는다.

### 예외를 분기문 대신 다형성과 advice로 라우팅한다

새 도메인 타입이나 예외가 늘어도 컨트롤러의 try-catch나 if-else에 줄을 더하지 않는다. 예외를 HTTP 응답으로 바꾸는 일은 어드바이스(`@RestControllerAdvice`)와 `HandlerExceptionResolver`가 맡고(`ConflictException`은 409, `NotFoundException`은 404), 도메인 분기는 Java 다형성(sealed 계층, enum 상수별 메서드, 전략)으로 표현한다. 분기문은 도메인 의미가 늘 때마다 함께 자라며 누락과 회귀의 원인이 되고, 실제로 과거에 도메인 예외를 잡던 catch가 보안 예외까지 삼켜 조용히 500으로 새던 사고가 있었다. 도메인 불변 조건을 지키는 if-throw는 예외로 둔다.

정상 흐름에서 사용자가 일으키는 논리적 모순은 예외로 거절하지 않고 화면에서 먼저 막는다(버튼 비활성화와 tooltip). 서버의 invariant 가드는 직접 POST나 동시성 같은 진짜 비정상 경로를 위한 안전망으로 남기되, UI 차단 조건과 서버 가드 조건은 같은 도메인 메서드 하나를 공유해 서로 어긋나지 않게 한다. 부팅 채널만은 예외인데, iPXE가 2xx 응답의 본문만 실행하므로 실패도 200 응답의 재시도 스크립트로 바꾼다.

### 자원 무결성을 HMAC 마커로 추적한다

등록한 자원은 디스크 파일과 함께 HMAC(Hash-based Message Authentication Code) 서명 마커(`.provision.json`)로 무결성을 추적한다. 파일 경로가 시스템 밖에서 바뀌어도 마커를 기준으로 DB를 재조정하고, DB와 파일시스템의 불일치(ghost, orphan)를 정합화한다. 파일의 위치는 파일시스템이, 자원의 수명주기 상태는 DB가 기준이다. 서명과 검증은 도메인과 무관한 공용 엔진 하나(`ProvisionMarkerService`)가 맡고, 도메인마다 얇은 writer가 속성만 조립한다.

### 활성 상태를 own과 effective로 나눈다

자원의 활성 여부와 deprecated 여부는 자기 값(own)과 부모에서 전파된 실효 값(effective)을 나눠 계산한다. 부모를 비활성화하면 자식의 effective가 따라 내려가되, 자식이 명시적으로 지정한 own 값은 보존된다. 부모를 껐다 켜도 자식이 스스로 정한 상태가 살아남게 하기 위한 구분이다. deprecated와 disabled 두 축은 서로 독립이라 한 비트로 합치지 않는다.

### 원시 타입을 피한다

MAC 주소, IP 주소, 토큰 같은 도메인 값은 값 객체(Value Object)와 JPA Converter로 다루고, 원시 필드로 엔티티나 서비스 시그니처에 흘리지 않는다. 파일 경로와 버전이 아직 String으로 남은 것은 인정한 부채다.

## 클론에서 기동까지

빌드와 테스트만 확인하려면 데이터베이스가 필요 없다. `@SpringBootTest`를 쓰는 클래스가 하나도 없어서 `./gradlew test`는 DB 없이 통과한다. 클론 직후 가장 빠른 확인은 이것이다.

```bash
git clone https://github.com/bluewhale36/ServerProvision.git
cd ServerProvision
./gradlew test    # DB 없이 전체 테스트 통과
./gradlew build   # 컴파일, 전체 테스트, 패키징
```

애플리케이션을 실제로 기동하려면 MariaDB가 있어야 한다. 애플리케이션은 스키마를 스스로 만들지 않는다(`ddl-auto=validate`). 스키마가 엔티티 매핑과 정확히 일치하지 않으면 기동이 실패하므로, 데이터베이스를 먼저 세우고 스키마를 적용한 다음 애플리케이션을 기동한다.

가장 간단한 길은 저장소에 포함된 `docker-compose.yml`을 쓰는 것이다. 이 파일은 MariaDB 컨테이너를 띄우고, 최초 기동 시 `ddl/schema.sql`을 초기화 스크립트로 자동 적재한다. `ddl/schema.sql`은 현재 스키마 전체를 한 파일로 뜬 스냅샷이다.

```bash
docker compose up -d    # MariaDB 기동 + ddl/schema.sql 자동 적재
```

컨테이너 없이 기존 MariaDB에 붙이려면 그 DB에 `ddl/schema.sql`을 직접 적용한다.

```bash
mariadb -h <호스트> -u <계정> -p server_provision < ddl/schema.sql
```

스키마가 준비되면 애플리케이션을 기동한다. 다음 환경변수는 기본값이 없어 반드시 주입해야 하며, 하나라도 비면 기동에 실패한다. 아래 예시의 접속 정보는 `docker-compose.yml`이 정의하는 값(계정 `provision`, DB `server_provision`)과 맞춰져 있다.

```bash
SERVER_PORT=7779 \
DB_URL=jdbc:mariadb://localhost:3306/server_provision \
DB_USERNAME=provision DB_PASSWORD=provision \
PROVISION_ALLOWED_ROOTS=/opt/provisioning/storage \
RECONCILIATION_SCAN_EXTRA_ROOTS= \
./gradlew bootRun
```

| 변수 | 의미 |
|---|---|
| `SERVER_PORT` | 서비스 포트 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MariaDB 접속 정보. `docker-compose.yml`에 정의된 값과 맞춘다. |
| `PROVISION_ALLOWED_ROOTS` | 자원 파일 접근을 허용할 루트 디렉터리 목록(쉼표로 구분한 절대 경로). 비어 있으면 기동 검증에서 실패한다. |
| `RECONCILIATION_SCAN_EXTRA_ROOTS` | 재조정 추가 스캔 루트. 기본값이 없는 자리 표시자라서 빈 값이라도 주입해야 한다. |

자원 마커의 서명 키 `PROVISION_MARKER_SECRET`은 기본값이 있어 로컬에서는 주입하지 않아도 되지만, 운영 환경에서는 반드시 교체한다. BIOS 셋업 원자료 경로 `PROVISION_BIOS_MATERIALS_DIR`은 기본값이 프로젝트 루트의 `redfish_materials/`이며, 이 디렉터리는 저장소에 포함되지 않는다(아래 저장소 구조 참고). 전체 설정 항목의 의미와 주의 사항은 `src/main/resources/application.properties`의 주석에 정리되어 있다.

## 저장소 구조

| 경로 | 내용 |
|---|---|
| `src/main/java/com/example/serverprovision/` | 소스. 최상위 패키지는 `management`, `maintenance`, `provisioning`, `execution`, `global`이다. |
| `src/main/resources/templates/`, `static/` | Thymeleaf 뷰와 CSS, JavaScript |
| `src/test/java/` | 단위 테스트와 HTTP 통합 테스트 |
| `docs/` | 개발자 매뉴얼. 시작점은 [docs/README.md](docs/README.md)다. |
| `plan/`, `report/` | 작업 단위별 계획과 구현 보고. 작성 시점의 기록이라 갱신하지 않는다. 인벤토리 코드별 색인은 각 디렉터리의 README에 있다. 초기에는 docx, 이후에는 html 형식이다. |
| `discussion/` | 실행 엔진(E 단계) 설계 토론 기록. 확정 결정은 DEC 번호가 붙는다. |
| `diagram/` | 게스트와 서버의 통신 다이어그램, PXE 실험 기록. 진행에 따라 같은 파일을 갱신한다. |
| `ddl/`, `sql/` | 수동 DDL(Data Definition Language) 이력. `ddl/schema.sql`은 현재 스키마 전체 스냅샷이고, 나머지는 변경 단위별 이력이다. |
| `scripts/` | 모의 게스트 하네스(`mock-guest/`), 진단 리눅스 자산 조립(`diag-image/`), QEMU 부팅 실험(`pxe-lab/`). 사용법은 각 스크립트의 헤더 주석에 있다. |
| `archive/legacy/` | 이전 구현의 참조용 사본. 실행 경로가 아니다. 필드와 구조는 참고하되 스타일과 관례는 따르지 않는다. |

보드별 BIOS 레지스트리와 셋업 데이터 원자료를 담는 `redfish_materials/`는 런타임에 외부에서 제공하는 디렉터리이며 `.gitignore` 대상이라 저장소에는 포함되지 않는다(추적 파일 0건). 기본 로드 경로는 설정 `provisioning.bios.resource-base`의 기본값이고, 환경변수 `PROVISION_BIOS_MATERIALS_DIR`로 바꾼다.

## 확장 지점

새 케이스가 생겨도 분기문(if-else, switch)을 늘리지 않고 확장점의 구현을 추가하는 것이 이 코드베이스의 기본 규칙이다. 새 자원 도메인과 새 실행 단계를 붙이는 자리는 다음과 같다. 절차 문서는 아직 작성되지 않았고(`docs/dev/`, 예정), 그 전까지는 아래 확장점과 가장 최근 선례 도메인을 함께 읽는다.

새 자원 도메인(예: 새 펌웨어 종류)을 추가할 때는 다음을 구현한다. 가장 가까운 선례는 표준 5분할을 그대로 따르는 `bios`, `bmc` 도메인이다.

- 수명주기 계약 `global.lifecycle.LifecycleService`. 모든 자원 도메인이 구현한다(toggleEnabled, softDelete, restore, deprecate, undeprecate, purge, purgeWithTypedNameCheck).
- 마커 연결 `global.marker.Markable`과, 자원 종류를 정의하는 `ResourceType` enum 항목. 파일 자원이면 속성을 조립하는 얇은 `*MarkerWriter`를 두고 서명 로직은 공용 엔진 `ProvisionMarkerService`에만 둔다.
- 재조정 노출 `MarkableScanner`. 재조정 엔진이 이 구현들을 모아 디스크와 DB를 대조한다.
- 커진 서비스는 `*LifecycleService`, `*RegistrationService`, `*IntegrityService`, `*MarkerWriter`, 잔류 `*Service`(조회와 수정)의 다섯으로 나누는 것이 표준이다.
- 등록 실패 파일의 복구가 필요하면 `global.orphan.OrphanRecoverySpi`를 구현한다(현재 구현은 ISO 하나).

새 실행 단계(프로비저닝 진행의 큰 단계)를 추가할 때는 다음을 구현한다. 첫 구현은 진단 리눅스를 부팅시키는 `DiagnoseLinuxExecutor`다.

- 단계 실행기 `execution.ProvisioningPhaseExecutor`. 빈으로 등록하면 기동 시 `PhaseExecutorRegistry`가 수집한다. 같은 단계의 실행기가 둘이면 기동에 실패하고, 실행기가 없는 단계는 게스트에 대기 스크립트로 응답한다.
- 단계 enum 항목 `ProvisioningPhase`(큰 단계)와 `ProvisioningPhaseStep`(세부 단계).

재조정이 다루는 새 어긋남 종류를 추가할 때는 `DriftKind` enum 항목과, 그 종류를 해결하는 `DriftResolution` 구현(하나가 하나의 종류를 담당) 하나를 추가한다.

## 테스트와 CI

테스트는 두 레이어로 작성한다. 단위 테스트로는 예외가 HTTP 응답으로 매핑되는 과정이나 컨트롤러 분기 누락이 드러나지 않기 때문이다.

- 단위 테스트는 Service의 분기마다 성공 경로와 실패 경로를 JUnit과 Mockito로 검증한다.
- 사용자 액션 통합 테스트는 각 엔드포인트의 모든 액션을 HTTP 계층에서 실제 상태코드와 응답 바디로 검증한다(`@WebMvcTest`와 `MockMvc`). 성공 2xx, 검증 실패 400, 충돌 409, 없음 404의 네 범주를 모두 다룬다. Mockito 대역은 Service 단까지만 두고, 컨트롤러의 try-catch와 어드바이스 매핑은 실제로 실행되게 한다.

현재 테스트는 1,345건이다. `@WebMvcTest` 40건과 `@DataJpaTest` 1건으로 구성되고 `@SpringBootTest`는 0건이라, `./gradlew test`가 데이터베이스 없이 통과한다. GitHub Actions 워크플로가 push와 pull request마다 JDK 21에서 `./gradlew test`를 돌리며, DB 서비스 없이 테스트만 실행한다.

## 문서

문서는 [docs/README.md](docs/README.md)에서 시작한다. 그 페이지가 문서 지도와 정보의 기준 출처(SSOT, Single Source of Truth)를 정리한다. 개발자 매뉴얼은 `docs/dev/` 아래에 있다.

| 문서 | 내용 | 상태 |
|---|---|---|
| [docs/README.md](docs/README.md) | 문서 안내. 문서 지도, 기준 출처, 작성 규칙 | 작성됨 |
| [docs/dev/architecture.md](docs/dev/architecture.md) | 전체 구조, 패키지 구성, 설계 규칙의 개요 | 작성됨 |
| [docs/dev/global.md](docs/dev/global.md) | global 인프라 상세. 컴포넌트별 역할, 협력 관계, 흐름, 커스텀 지점 | 작성됨 |
| `docs/dev/` 나머지, 웹 화면 사용자 가이드 | 도메인별 상세, 확장 절차, 사용자 튜토리얼 | 예정 |
| [CLAUDE.md](CLAUDE.md) | 개발 프로세스와 아키텍처 원칙 | 작성됨 |
| [DESIGN.md](DESIGN.md) | UI 디자인 시스템 명세 | 작성됨 |
| [diagram/](diagram/) | 게스트와 서버의 실행 프로토콜(메시지 순서의 기준 출처), PXE 실험 기록 | 진행에 따라 갱신 |
| [DB_FS_CONSISTENCY.md](DB_FS_CONSISTENCY.md) | 파일시스템과 DB의 역할 분담 설계 배경. 2026년 5월 초 기준이라 이후 변경은 코드와 대조한다. | 이력 |

`plan/`과 `report/`는 인벤토리 코드별 색인을 각 디렉터리의 [plan/README.md](plan/README.md)와 [report/README.md](report/README.md)에서 제공한다.

문서는 자주 변하지 않는 것(경계, 설계 규칙, 구조 지도)만 담고, 자주 변하는 사실은 기준 출처에 맡긴다. 엔티티 필드와 메서드 시그니처는 코드가, 작업 단위별 상태와 일정은 Notion DB가 기준 출처다.

## AI 협업

이 저장소는 사람과 Claude Code(Claude Code CLI)가 역할을 나누어 개발했다. 설계 검토, 체크포인트 승인, 브라우저 E2E(End-to-End) 검증은 저장소 소유자가 직접 하고, 구현 코드 작성은 Claude Code가 한다. 커밋 145개 중 99개에 `Co-Authored-By: Claude` 표기가 공개되어 있다.

작업은 페이지 또는 기능 단위의 수직 슬라이스로 진행한다. URL 스케치부터 뷰, 컨트롤러, DTO, 서비스, 리포지토리, 엔티티, 테스트, 스키마 확인, 브라우저 검증까지 열 단계로 나누고, 이를 다섯 체크포인트(CP1~CP5)로 묶어 각 단계마다 소유자 승인 뒤에 다음으로 넘어간다. CP1(계획)에서 설계 대안을 복수로 비교하고, 구현(CP2~CP4)은 그 계획을 따르며, CP5(브라우저 검증)는 소유자가 단독으로 한다. 계획 산출물은 `plan/`에, 구현 보고는 `report/`에 남는다.

구현 단계에서는 병렬 에이전트가 코드를 나눠 작성하고, 별도 검수 에이전트가 결함을 반려하는 자동 검토를 쓴다. 이 검토를 사람의 상호 검토처럼 표기하지 않는다. 테스트 규율(단위 테스트와 `@WebMvcTest` HTTP 통합, 2xx/400/409/404 네 범주)은 위 "테스트와 CI"에 적은 그대로이며, 이 협업 흐름의 결과물에도 동일하게 적용한다.

기록은 각색하지 않는다. 병합은 소유자 혼자 하는 self-merge이고, 검토 주체가 별도 Claude 세션이었던 사실은 커밋 본문과 설계 문서에 원문으로 남아 있다. AI 검토를 인간 검토로 옮겨 적지 않는다. 개발 프로세스와 아키텍처 원칙의 전체 규약은 [CLAUDE.md](CLAUDE.md)에 있다.

## 브랜치 모델과 작업 단위

브랜치는 세 계층이다(2026년 7월 재편).

| 브랜치 | 역할 |
|---|---|
| `main` | 실 배포 소스. GitHub 기본 브랜치다. |
| `dev` | 개발 완료 도달점. `dev`에서 `main`으로의 병합만 배포로 본다. |
| `<type>/<인벤토리코드>_<슬러그>` | 작업 브랜치. 캠페인 또는 phase 단위로 PR을 연다. 인벤토리 코드에 하이픈이 들어가므로(`E1-I`, `HF4-1`) 코드와 슬러그의 경계는 언더스코어로 나눈다. `type`은 conventional 어휘(feat, fix, refactor, docs, chore)를 쓰고, 브랜치명만 영문이며 커밋 메시지와 주석은 한국어다. 예: `feat/E1-I_boot-infra`, `docs/DOC-1_dev-reference`. |
| `refine/<기능>` | Java와 Spring 학습을 위한 재구성 브랜치. 로컬 전용이라 push하지 않아 정규 이력과 기여 그래프를 오염시키지 않는다. |

작업은 인벤토리 코드로 부른다. 코드 번호는 식별자이지 실행 순서가 아니다. 각 코드의 상태와 이력은 Notion DB 'Provisioning Server 개발 상세'가 기준 출처다.

| 코드 | 영역 |
|---|---|
| `MA` | Management 자원 관리 기능 |
| `MK` | Maintenance 점검과 복구 |
| `U` | Provisioning 사용자 기능 |
| `E` | Execution 실행 엔진. E1은 진단 리눅스이고, `ProvisioningPhase` 순서에 대응한다. |
| `S` | 영역을 가로지르는 인프라 |
| `R` | 리팩토링 캠페인 |
| `HF` | 핫픽스 |
| `DOC` | 문서화 |

이 밖에 저장소 정돈은 `CH`, 리네임은 `M0`으로 부른다.
