# ServerProvision

ServerProvision은 데이터센터의 베어메탈 서버에 운영체제, 펌웨어, 드라이버를 자동으로 설치하고 설정하는 프로비저닝 관리 시스템이다. 관리 화면은 Thymeleaf 서버 사이드 렌더링으로 제공하고, 물리 서버와는 네트워크 부팅(PXE, Preboot eXecution Environment)과 HTTP API로 통신한다.

기능은 네 영역으로 나뉘고, 영역은 URL의 첫 경로 구간과 일치한다. 공용 인프라가 노출하는 `/ui/*`(확인 모달)와 `/jobs`(백그라운드 작업 폴링)만 예외다. 로그인과 권한 계층은 없으며 localhost 바인딩을 전제로 한다. 관리자, 운영자, 사용자라는 구분은 한 사람이 겸할 수 있는 관점의 구분이다.

| 영역 | URL | 역할 |
|---|---|---|
| Management | `/management/*` | 프로비저닝에 쓸 자원을 등록하고 관리한다. OS 이미지와 ISO, 메인보드 모델, BIOS와 BMC 펌웨어, Subprogram(드라이버와 유틸리티)을 다룬다. |
| Maintenance | `/maintenance/*` | 시스템을 점검하고 복구한다. DB와 파일시스템의 어긋남 재조정, 업로드 실패 복구, 휴지통과 영구삭제 감사를 다룬다. |
| Provisioning | `/provisioning/*` | 세팅 정의서와 BIOS 세팅 템플릿을 작성하고, 게스트 서버를 확인하고 프로비저닝을 개시한다. |
| Execution | `/api/pxe/v1/*` | 물리 서버가 부팅 시 자동 등록하고 진행을 보고하는 API다. 실행 엔진은 구현이 진행 중이다. |

기술 스택은 Spring Boot 4(Java 21), Thymeleaf, Spring Data JPA, MariaDB다.

## 문서

문서는 [docs/README.md](docs/README.md)에서 시작한다. 개발자 매뉴얼은 `docs/dev/`, 사용자 가이드는 `docs/user-guide/` 아래에 순차로 작성되고 있다.

| 문서 | 내용 |
|---|---|
| [docs/README.md](docs/README.md) | 문서 안내. 문서 지도, 정보의 기준 출처, 문서 작성 규칙을 담는다. |
| [CLAUDE.md](CLAUDE.md) | 개발 프로세스와 아키텍처 원칙 |
| [DESIGN.md](DESIGN.md) | UI 디자인 시스템 명세 |
| [diagram/](diagram/) | 게스트와 서버의 통신 다이어그램, PXE 실험 기록. 진행에 따라 같은 파일을 갱신한다. |

## 빌드와 실행

```bash
./gradlew build   # 컴파일, 전체 테스트, 패키징
./gradlew test    # 전체 테스트
```

애플리케이션은 환경변수 없이는 기동하지 않는다. 다음 변수는 기본값이 없어 반드시 주입해야 한다.

| 변수 | 의미 |
|---|---|
| `SERVER_PORT` | 서비스 포트 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MariaDB 접속 정보 |
| `PROVISION_ALLOWED_ROOTS` | 자원 파일 접근을 허용할 루트 디렉터리 목록(쉼표 구분 절대 경로). 비어 있으면 부팅에 실패한다. |
| `RECONCILIATION_SCAN_EXTRA_ROOTS` | 추가 스캔 루트. 기본값이 없는 자리 표시자라서 빈 값이라도 주입해야 한다. |

```bash
SERVER_PORT=7779 \
DB_URL=jdbc:mariadb://localhost:3306/server_provision \
DB_USERNAME=<계정> DB_PASSWORD=<비밀번호> \
PROVISION_ALLOWED_ROOTS=/opt/provisioning/storage \
RECONCILIATION_SCAN_EXTRA_ROOTS= \
./gradlew bootRun
```

전체 설정 항목의 의미와 주의 사항은 `src/main/resources/application.properties`의 주석에 정리되어 있다. 자원 마커의 서명 키 `PROVISION_MARKER_SECRET`은 기본값이 있지만 운영 환경에서는 반드시 교체해야 한다.

스키마는 `ddl-auto=validate`로 검증만 한다. Hibernate는 스키마를 생성하지 않으며, 변경은 수동 DDL(Data Definition Language)을 `ddl/`에 축적해 반영한다. 초기 이력 1건은 `sql/`에 있다.

## 저장소 구조

| 경로 | 내용 |
|---|---|
| `src/main/java/com/example/serverprovision/` | 소스. 최상위 패키지는 `management`, `maintenance`, `provisioning`, `execution`, `global`이다. |
| `src/main/resources/templates/`, `static/` | Thymeleaf 뷰와 CSS, JavaScript |
| `src/test/java/` | 단위 테스트와 HTTP 통합 테스트 |
| `docs/` | 개발자 매뉴얼과 사용자 가이드 |
| `plan/`, `report/` | 작업 단위별 계획과 구현 보고. 작성 시점의 기록이라 갱신하지 않는다. 초기에는 docx, 이후에는 html 형식이다. |
| `discussion/` | 실행 엔진 설계 토론 기록. 확정 결정은 DEC 번호가 붙는다. |
| `diagram/` | 통신 다이어그램과 실험 기록. 진행에 따라 같은 파일을 갱신한다. |
| `ddl/`, `sql/` | 수동 DDL 이력 |
| `scripts/` | 모의 게스트 하네스(`mock-guest/`), 진단 리눅스 자산 조립(`diag-image/`), QEMU 부팅 실험(`pxe-lab/`). 사용법은 각 스크립트의 헤더 주석에 있다. |
| `redfish_materials/` | 보드별 BIOS 레지스트리와 셋업 데이터 원자료. 기본 로드 경로이며 `PROVISION_BIOS_MATERIALS_DIR`로 바꿀 수 있다. |
| `archive/legacy/` | 이전 구현의 참조용 사본. 실행 경로가 아니다. |
