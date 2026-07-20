# 문서 안내

ServerProvision의 문서는 두 갈래다. 개발자 매뉴얼은 `dev/` 아래에, 웹 화면 사용자 가이드는 `user-guide/` 아래에 있다. 모두 저장소 안의 markdown이라 코드 변경과 같은 커밋에서 문서 변경을 함께 확인할 수 있다.

## 문서 지도

### 개발자 매뉴얼

| 문서 | 내용 | 상태 |
|---|---|---|
| `dev/architecture.md` | 전체 구조, 패키지 구성, 설계 규칙의 개요 | 초안(DOC-1) |
| `dev/global.md` | global 인프라 상세. 컴포넌트별 역할, 협력 관계, 흐름, 커스텀 지점 | 초안(DOC-1-1) |
| `dev/management/os.md` | os 도메인 상세. 데이터 모델, 클래스 관계, 흐름별 전달 값, 신규 OS 추가 절차 | 예정(DOC-1-2) |
| `dev/management/board-and-firmware.md` | board, bios, bmc, subprogram, common 상세 | 예정(DOC-1-3) |
| `dev/provisioning/setting.md` | 세팅 정의서, biossetting, BIOS 셋업 파싱 상세 | 예정(DOC-1-4) |
| `dev/maintenance/reconciliation.md` | 재조정, 휴지통, 격리 상세 | 예정(DOC-1-5) |
| `dev/guides/add-resource-domain.md` | 새 자원 도메인 추가 절차 | 예정(DOC-4) |
| `dev/guides/add-provisioning-phase.md` | 새 실행 단계 추가 절차 | 예정(DOC-4) |
| `dev/glossary.md` | 용어집 | 예정(DOC-4) |
| `dev/adr/` | 설계 결정 기록(ADR, Architecture Decision Record) | 예정(DOC-5) |

### 사용자 가이드

| 문서 | 내용 | 상태 |
|---|---|---|
| `user-guide/01-overview.md` | 시스템 개요와 자원 모델 | 예정(DOC-2) |
| `user-guide/02-getting-started.md` | 처음부터 끝까지 따라 하는 튜토리얼 | 예정(DOC-2) |
| `user-guide/03-management.md` | 자원 관리 절차 | 예정(DOC-3) |
| `user-guide/04-provisioning.md` | 세팅 정의서와 게스트 서버 절차 | 예정(DOC-6) |
| `user-guide/05-maintenance.md` | 점검과 복구 절차 | 예정(DOC-6) |
| `user-guide/06-reference.md` | 상태 전이 표, 마커 파일, 네트워크 부팅 요구조건 | 예정(DOC-6) |
| `user-guide/07-troubleshooting.md` | 문제 해결 | 예정(DOC-6) |

`T3-checklist.md`는 물리 서버를 확보했을 때 몰아서 검증할 항목의 목록이다.

## 정보의 기준 출처

같은 사실을 두 곳에 적으면 시간이 지나며 어긋난다. 정보마다 기준 출처(SSOT, Single Source of Truth)를 하나로 정하고, 문서는 그 범위를 넘지 않는다.

| 정보 | 기준 출처 | 문서가 하는 일 |
|---|---|---|
| 엔티티 필드, 메서드, 시그니처 | 코드 | 심볼 이름만 언급한다 |
| 작업 단위별 상태와 일정 | Notion DB 'Provisioning Server 개발 상세' | 언급만 한다 |
| 개발 프로세스와 규약 | `CLAUDE.md` | 참조한다 |
| 설정 항목과 기본값 | `src/main/resources/application.properties` 주석 | 필수 변수만 발췌한다 |
| 화면 디자인 규약 | `DESIGN.md` | 참조한다 |
| 게스트와 서버의 실행 프로토콜 | `diagram/guest-provisioning-protocol.html` | 링크한다 |
| DB 스키마 | `ddl/`의 수동 DDL(Data Definition Language)과 실제 DB | 개별 컬럼을 서술하지 않는다 |

## 문서 작성 규칙

1. 자주 변하지 않는 것만 담는다. 경계, 설계 규칙, 구조 지도, 사용 절차는 문서에 적고, 자주 변하는 사실(필드 목록, 시그니처, 작업 상태)은 위 표의 기준 출처에 맡긴다.
2. 코드는 심볼 이름으로만 참조한다. 파일 경로와 행 번호는 이름이 바뀔 때 조용히 낡기 때문에 적지 않는다. 문서와 설정 파일은 상대 경로로 링크해도 된다.
3. 구조를 바꾸는 커밋에는 문서 갱신을 함께 담는다. 대상은 패키지 이동과 신설, 확장점 계약 변경(`Markable`, `MarkableScanner`, `LifecycleService`, `ProvisioningPhaseExecutor`, `DriftResolution`), 설계 규칙의 신설과 폐기, URL 첫 구간 변경, 필수 환경변수 변경이다.
4. 사용자 검증 전 기능은 절 머리에 표시한다. 구현이 끝났어도 브라우저 검증과 승인 전이면 인용 블록으로 "참고: 이 기능은 아직 사용자 검증 전이다. 세부가 바뀔 수 있다."를 달고, 승인되면 지운다.
5. 문서 말미에 `last-reviewed` 날짜를 적고 분기마다 다시 본다. 변경마다 동기화하려 하지 않고, 3번의 대상에 해당할 때만 즉시 고친다.
6. 문체는 공식 레퍼런스 문서의 관례를 따른다.
   - 개발자 매뉴얼은 한다체, 사용자 가이드는 합니다체로 통일한다.
   - 나열은 쉼표나 목록으로 한다. 가운뎃점(·)은 쓰지 않는다.
   - 보충 설명은 괄호나 별도 문장으로 한다. 대시(—)는 쓰지 않는다.
   - 제목은 한국어 명사구로 쓰고 영어를 병기하지 않는다.
   - 용어는 첫 등장에 한 번만 원어를 병기하고 이후에는 한글로만 쓴다. 예: 재조정(reconciliation). 코드에 실재하는 이름은 번역하지 않고 코드 스팬으로 쓴다.
   - 본문은 서술 문단이 중심이고, 목록은 도입 문장 뒤에 보조로 쓴다. 볼드는 정의 목록의 머리와 꼭 필요한 경고에만 쓴다. 이모지와 장식 기호는 쓰지 않는다.
   - 문서 자체에 대한 설명이나 작성 방법론 이름은 적지 않고 내용으로 바로 들어간다.

## 이전 산출물

문서 트리보다 먼저 쌓인 산출물이 있다. 인용할 때 다음을 주의한다.

| 자산 | 성격 | 주의 |
|---|---|---|
| `plan/`(약 150건) | 작업 단위별 계획. 초기에는 docx, 이후에는 html | 작성 시점의 설계라서 현재 코드와 다를 수 있다 |
| `report/`(약 75건) | 구현 보고와 조사 기록 | 위와 같다 |
| `discussion/` | 실행 엔진 설계 토론. 확정 결정에 DEC 번호가 붙는다 | 확정 결정은 `dev/adr/`로 옮겨 적을 예정이다 |
| `DB_FS_CONSISTENCY.md` | DB와 파일시스템 정합성 설계 | 2026년 5월 기준이라 이후 변경이 빠져 있다. 코드와 대조해 읽는다 |
| `MK3-2_SOFTDELETE_REJECT_DIRECTION_1.md` | soft-delete 정책 설계 | 위와 같다 |
| `report/26-07-11_17-57-19_drift-simulator_guide.html` | 드리프트 점검 체험 가이드 | 재조정 리팩토링 이전 산출물이다 |

last-reviewed: 2026-07-19
