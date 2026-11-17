# S23 지원 대상 카탈로그 고정: 토론 아카이브

- 단계: S23 (Notion: https://app.notion.com/p/41bef0e868f98124afa3dd54b644260f)
- 토론 기간: 2026-11-17 (Notion 단계 페이지의 `## 토론` 절에서 진행, 같은 날 종결)
- 출발점 패치 항목: "OSFamily 에 따른 OSName 의 메이저버전 상세화"(높음), "신규 메인보드 등록에 필요한 자원 자동 수집 기능"(중간)
- 관련 단계: E4-1-b-2 정의서 RHEL 설치 요청 활성화(UE 세션), R17 RAID 칩 판별 목록 데이터화(선례), E3-3 BIOS 레지스트리 채집

## 1. 문제 제기

사용자 제기(2026-11-17, 앵커 세션)를 옮긴다.

- 지금까지는 모든 부분을 확장 가능하게 설계하는 것이 옳다고 보았다. 그런데 사용 가능한 선택지만 사용자에게 보이고 그렇지 않은 것은 원천 차단하는 편이 낫다고 판단이 바뀌었다(클라우드 서비스 학습이 계기).
- OSName 은 OS 종류만 담는다. RHEL 계열은 메이저 8, 9, 10 에서 kickstart 옵션이 달라, E4-1-b-2 가 `RhelRelease` 로 메이저를 판정하기 시작했다. OS 를 메이저 단위로 고정하면 자동화가 지원되는 OS 가 분명해진다.
- 같은 방식이면 메인보드도 닫아야 한다. 지금은 GIGABYTE 몇 종만 자동화되지만 메인보드 이름은 언제든 등록된다.
- 관리 포인트는 늘어난다(새 메인보드, 새 OS 릴리스 때 코드 갱신, 개발 인력 필요). 그러나 드물다. 지금까지는 될지 안 될지 모르는 유지보수를 인수인계로 넘겼다면, 앞으로는 관리 포인트를 늘리는 대신 일반 사용자를 막는 요소를 없앤다.
- 대안으로 메인보드 모델과 OS 릴리스를 super admin 이 관리할 수도 있다. 이때도 자원 도메인 개편은 크고, super admin 에게 개발 지식이 필요하다.
- 어느 방향이든 매뉴얼에 '개발 및 유지보수' 절이 필요하다.

UE 세션에서 같은 날 나온 사용자 원문(UE 인수인계)도 같은 흐름이다. Rocky 11 이 나왔는데 패치하지 않은 상태에서 누군가 ISO 를 등록하면 어떻게 되는지, ISO 등록 때 추출 작업에서 미리 걸러낼 수 없는지, OS 를 'Rocky Linux' 로만 넣지 말고 지원 메이저를 명시하자는 내용이다.

## 2. 현행 실태 (2026-11-17 코드 실측)

| 대상 | 지금 방식 | 결과 |
|---|---|---|
| OS 종류 | `OSName` = UBUNTU, CENTOS, ROCKY_LINUX, WINDOWS, WINDOWS_SERVER. 버전은 `OSMetadata` 의 자유 문자열. DB `os_metadata.os_name` 은 MariaDB ENUM 컬럼, 고유 키 (os_name, os_version) | 자동화가 안 되는 OS 도 등록되고 설치 단계에서야 막힌다 |
| 제조사 | `Vendor` enum(GIGABYTE, ASUS, FUJITSU)은 이미 닫혀 있다("런타임 등록은 지원하지 않는다") | 제조사는 이미 폐쇄형 |
| 메인보드 모델 | DB 자유 등록. 코드에 모델 이름 분기는 없다. 모델 간 차이는 BIOS 자료(레지스트리 JSON, SetupData XML)뿐이고 `application.properties` 의 `provisioning.bios.boards[0..4]` 에 5 개가 하드코딩돼 있다 | 자료 없는 모델도 등록되고 BIOS 설정에서 막힌다 |
| 선례 | `RaidCard`(DB 행)가 `RaidChipFamily`(닫힌 enum)를 참조한다 | 실물은 DB, 능력은 enum |
| PXE 최초 등록 | `/boot` 의 modelName 이 등록되지 않은 값이면 그 요청에서 실패한다(사용자 실측) | 미등록 보드는 이미 진단 전에 막힌다 |

## 3. 판정 규칙 (채택)

대상끼리 다른 것이 코드(동작)이면 enum 으로 닫고, 자료뿐이면 자료와 준비도 관문으로 닫는다.

근거: 능력은 어차피 코드에 있다(RHEL 메이저별 kickstart 옵션, 제조사별 BMC 동작). 등록을 열어도 능력은 생기지 않는다. 반대로 자료만 다른 대상(같은 제조사의 새 보드 모델)까지 enum 으로 닫으면 자료 파일 두 개 때문에 코드 배포가 필요해진다.

탈락 대안:
- 모두 코드 상수: 새 보드 모델마다 개발자와 배포가 필요하다.
- 모두 super admin 카탈로그(DB): kickstart 차이 같은 코드 능력은 DB 로 옮겨지지 않아, 코드와 DB 가 같은 목록을 이중으로 갖는다.

## 4. 결정

| 주제 | 결정 | 하위 단계 |
|---|---|---|
| OS 상수 | `OSName` 은 배포판과 메이저까지 담는다: ROCKY_LINUX_8, 9, 10, RHEL_8, 9, 10, WINDOWS_SERVER_2025. UBUNTU 는 남긴다(RHEL 계열 직후 구현). CENTOS 와 WINDOWS(클라이언트)는 지운다. Debian 계열 계약은 남긴다. 지원 밖 기존 행은 지우지 않는다 | S23-1 |
| 메이저 능력 | `RhelRelease` 는 메이저 단위 상수이고 `OSName` 을 Set 으로 1:N 으로 갖는다(예: R9 는 ROCKY_LINUX_9 와 RHEL_9). kickstart 옵션 같은 메이저 능력은 `RhelRelease` 한 곳에 둔다 | S23-1 |
| Rocky 버전 입력 | 등록 화면에서 메이저를 고르고 '메이저.세부' 를 입력하는 경험은 유지한다 | S23-1 |
| ISO 메이저 대조 | ISO 추출(배경 작업)에서 메이저 불일치나 .treeinfo 없음이면, 새 등록은 ISO 를 '등록 실패' 상태로 남기고 사유를 보인다(행과 파일 유지, 운영자가 지움). [추출] 재실행에서 불일치가 나오면 기존 설치 환경을 남긴다. 다만 추출이 끝난 ISO 는 `CompsExtractionLauncher` 가 재실행을 막아(AlreadyExtractedException) 이 경우는 생기지 않는다(UE 실측) | E4-1-b-2 D-20 에 지금 반영(UE, CP5 뒤) |
| Windows Server 버전 | OS 버전은 'Windows Server 2025' 하나다. 세부 빌드는 ISO 의 install.wim 에서 읽어 ISO 마다 보인다. 2025 계열 빌드가 아니면 ISO 메이저 대조와 같이 처리한다 | S23-1 |
| 추출 버튼 | 상수의 능력 메서드 하나로 RHEL 계열에만 [추출] 을 띄우고, 같은 메서드를 서버 가드가 쓴다 | S23-1 |
| 메인보드 | 제조사는 enum. 모델은 BIOS 자료 준비와 현장 검증을 거친 뒤 super admin 이 '지원' 으로 켠다. BIOS 자료를 보드 자원 도메인으로 옮긴다(패치 항목 "신규 메인보드 자원 자동 수집" 과 합류) | S23-2 |
| 지원 미승인 보드의 PXE | 진단까지는 허용하고(새 보드 자료를 모으는 경로가 진단이다) 프로비저닝 개시는 막는다. S23-2 plan 에서 실측과 함께 확정 | S23-2 |
| 원장 | 관리 포인트 원장(계기, 클래스 또는 설정, 고칠 내용, 확인할 테스트, 생기는 단계)과 배포 대기 원장(점검 SQL, DDL, 사용자 결정)을 저장소에 두고, 그런 자리를 만든 슬라이스가 병합 때 등록하는 규칙을 CLAUDE.md 에 더한다 | S23-3 |
| 순서 | S23-1 은 E4-1-b-2 dev 병합 뒤 그 위에서 시작한다. 이어서 S23-2, S23-3 | |

## 5. 쟁점별 대안과 탈락 사유

### OSName 과 RhelRelease 의 관계
- 합친다(`OSName` 상수가 메이저 능력을 직접 보유): Rocky 9 와 RHEL 9 이 같은 kickstart 능력을 상수마다 중복해 갖게 되어 탈락.
- `RhelRelease` 를 배포판별 상수로 두고 `OSName` 과 1:1: 같은 이유로 탈락(사용자 P2 답).
- 채택: `RhelRelease` 는 메이저 단위, `OSName` 을 Set 으로 1:N. 능력은 메이저 한 곳에 모이고, 배포판 차이(.treeinfo 이름 등)는 `OSName` 이 맡는다. 각 `OSName` 은 정확히 한 `RhelRelease` 에 속한다는 불변식을 S23-1 테스트로 지킨다.

### os_version 에 담을 것
- 세부만 담기: 화면 표기, .treeinfo 대조, 기존 행 이행이 복잡해져 탈락.
- 채택: '메이저.세부' 전체를 유지하고 상수 메이저와 일치를 검증한다.

### 지우는 상수와 Debian 계열 계약
- 앵커 첫 권고는 UBUNTU 와 Debian 계열 계약(14 파일)을 함께 걷는 것이었다. 사용자가 Ubuntu 를 RHEL 계열 직후 구현하기로 해 탈락. 지우는 것은 CENTOS 와 WINDOWS(클라이언트)뿐이다.

### 지원 밖 기존 행
- 지우기: 참조 중인 ISO 와 정의서가 있을 수 있어 탈락.
- 채택: 남기고 등록과 정의서 단계에서 막으며 화면에 미지원으로 보인다.

### ISO 메이저 불일치의 처리
- 추출만 실패하고 ISO 는 정상 등록으로 남기기(E4-1-b-2 D-20 원안): 정의서에서는 못 쓰지만 등록 자체는 성공처럼 보여 탈락(사용자 Q3 답 "등록 취소").
- 즉시 삭제 또는 휴지통: 배경 작업이 조용히 지우면 운영자가 원인을 알 수 없어 탈락.
- 채택: 새 등록은 '등록 실패' 상태로 남기고 사유를 보인다. [추출] 재실행은 기존 환경을 남겨 기존 정의서의 참조를 끊지 않는다.

### Windows Server 의 버전
- 2022 상수 추가: 자동화해 본 적이 없어 메이저 간 차이를 알 수 없으므로 탈락. Subprogram 도메인이 사실상 Windows Server 2025 설치 하나에 묶여 있다는 사정도 있다.
- 사람이 버전을 입력: 무엇을 적어야 할지 알 수 없어 탈락.
- 채택: ISO 의 install.wim 이 스스로 말하는 빌드를 읽는다(Rocky 의 .treeinfo 대조와 같은 원칙). 설치 이미지를 읽는 코드(`WindowsImageCatalog`)는 이미 있다.

### 메인보드
- 모델 enum 하드코딩: 자료만 다른 모델에도 배포가 필요해 탈락.
- 전부 super admin 카탈로그: 현장에서만 드러나는 특성(예: MD72-HB3 의 모델명 보고 접미사)을 반영할 자리가 없어 탈락.
- 채택: 제조사 enum, 모델은 자료 관문과 super admin 의 '지원' 승인.

## 6. 하위 단계에 넘기는 조건

- S23-1:
  - `os_metadata.os_name` ENUM 변경은 DDL(MODIFY)과 기존 행 변환 DML 이 필요하다. 지울 상수를 쓰는 행, 메이저를 읽을 수 없는 버전 행, 지원 밖 메이저 행을 배포 전 점검 SQL 로 먼저 센다.
  - 고유 키 uk_os_metadata_name_version 의 의미를 새 상수에 맞춰 확인한다.
  - 바꿀 곳: `OsRegistrationOption`, `OSMetadataCreateRequest`, `TreeInfo.matches`(RHEL 은 .treeinfo 배포판 이름이 Rocky 와 다르다), `RHELInstallationFamilyInspector.installBlockReason`, `OSMetadataController.MVP_OS_NAMES`, `RHELCompsExtractor.SUPPORTED`.
  - RHEL 은 지금 `OSName` 에 없는 배포판이라 실제 RHEL ISO 로 트리 배치, comps, .treeinfo 표기를 확인한다.
  - E4-1-b-2 는 `supportsRootSshFlag` 를 이미 메이저 번호 기준으로 바꿨다. 다른 메이저 능력도 같은 방식으로 옮긴다.
- S23-2:
  - BIOS 자료(레지스트리, SetupData)를 보드 자원 도메인으로 옮기고, BIOS FW 버전과의 위계를 정한다(패치 항목 2 적요).
  - '지원' 승인 전 보드의 `/boot` 동작을 실측한 뒤 확정한다.
  - `Vendor` 의 ASUS 와 FUJITSU 가 실제로 자동화되는지 확인하고, 아니면 미지원 표시를 둔다.
- S23-3:
  - 원장 두 개의 위치와 형식, CLAUDE.md 규칙 문구를 정하고, 지금까지 흩어진 항목(HF26, HF30-1, MA8 DDL, HF32 점검 SQL, E4-1-b-2 점검 SQL과 DDL)을 옮긴다.
  - 매뉴얼 '개발 및 유지보수' 절의 원료를 docs 세션에 넘긴다.
