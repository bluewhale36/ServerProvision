# 디자인 규약 점검 원장

디자인 규약(DESIGN.md)이 화면에서 지켜지는지 점검한 기록이다. 규약 자체는 DESIGN.md 가 SSOT 이고, 이 문서는 점검의 기준선, 발견, 처리 상태, 재개 방법만 담는다. 점검은 디자인 스트림(`ServerProvision-design-experiment`)이 맡는다.

## 강제 장치

| 장치 | 무엇을 | 언제 |
|---|---|---|
| `DesignConventionTest` | 표 구조(5절, 9절), 정의 없는 `n-` 클래스(8절), 인라인 스타일과 `<style>` 블록(8절), 템플릿의 정적 메서드 호출(8절), 같은 단독 선택자의 중복 정의(8절), 템플릿과 메시지의 대시, 가운뎃점, 하십시오체(7절) | 전체 테스트(CP4) |
| `scripts/design-audit/copy-scan.mjs` | 렌더 화면 글자의 표기 위반(7절) | 화면을 바꾼 슬라이스의 CP5 |
| `scripts/design-audit/line-scan.mjs` | 겹친 구분선과 빈 구분선(5절) | 같음 |
| `scripts/design-audit/overflow-scan.mjs` | 폭별 페이지 가로 넘침과 칸 밖으로 나간 표 칸, 배지, 버튼 | 글자 크기, 문구, 열 폭을 바꾼 슬라이스의 CP5 |
| CLAUDE.md 'UI 디자인' 절 | 세션이 자동으로 읽는 핵심 규칙과 DESIGN.md 로 가는 길 | 세션 시작 |

`DesignConventionTest` 의 허용 목록(`LEGACY_*`)은 기존 부채다. 줄이기만 하고 늘리지 않는다. 부채를 갚으면 목록에서 지운다.

## 2026-11-16 점검 (S22-4)

기준선: 샌드박스 92 화면(`scripts/design-audit/urls.sample.txt`).

| 번호 | 발견 | 처리 |
|---|---|---|
| D-1 | 판 밖 `n-form-actions` 가 판 테두리 아래 32px 에 선을 하나 더 그음(정의서 작성, 정의서 상세, 드리프트 설정) | 해소. `style.css` 에 판 뒤 `n-form-actions` 의 선을 지우는 규칙 |
| D-2 | BIOS 세팅 상세에서 코드 상자 바로 밑 점선 | 해소. `bios-setting.css` 점선 제거 |
| D-3 | 라벨과 값의 표에 목록 표 클래스(`n-table`, `thead` 없음): 휴지통 설정, 시스템 자산 설정의 '현재 적용된 값'. 다른 스트림(U3-7)이 이 화면을 베껴 같은 오류를 냈다 | 해소. `n-detail-table` 로 교체 |
| D-4 | 정의 없는 클래스 10 개(`n-asset-table`, `n-center`, `n-lookup-disabled`, `n-process-card`, `n-row-warning`, `n-subprogram-section-actions`, `n-subprogram-section-header`, `n-subprogram-section-title`, `n-table-compact`, `n-table-wrap`). 있겠거니 하고 쓴 이름이라 아무 효과가 없었다 | 해소. 이름 삭제, `n-center` 는 정의된 `n-td-center` 로, 드라이버 목록의 템플릿 안 `<style>` 은 `subprogram-list.css` 로 옮김(`<style>` 블록도 검사 대상에 더함) |
| D-5 | 스크립트 표지를 `n-` 이름으로 단 것 8 개 | 부채. `LEGACY_JS_HOOKS` 허용 목록 |
| D-6 | 같은 뜻 두 이름: `n-section-divider`(os-page.css)와 `n-divider`(style.css), 값이 같다 | 해소. `n-divider` 로 통일, os-page.css 정의 삭제 |
| D-7 | 같은 단독 선택자가 두 시트에 있는지 | 검사 결과 0 건(괄호 안 선택자를 오탐하던 검사를 고친 뒤). `LEGACY_DOUBLE_DEFINITIONS` 는 빈 목록 |
| D-8 | `n-tbl-list` 는 정의서 작성 화면에만 남은 세 번째 표 종류 | 부채. 새로 쓰지 않음(9절). 정의서 작성 화면을 고칠 때 `n-table` 로 옮긴다 |
| D-9 | 탭 줄 아래 판(시스템 자산), 묶음 머리 선 아래 상자 항목(게스트 서버 묶음 보기)이 `빈 구분선` 으로 잡힘 | 정상 간격으로 판정. 도구 README 에 판정 기준으로 적음 |
| D-10 | 화면 문구의 하십시오체. 하십시오체를 정본으로 둔 옛 문구 규약(new-user-copy.md)과, 그것을 따른 S22-3 과 S22-4 정리가 퍼뜨렸다. 2026-11-16 사용자 지시로 콘솔 문구는 매뉴얼(MANUAL.md) 문체, 합니다체 평서형만 쓴다 | 해소. 규약 정정(DESIGN.md 7절, new-user-copy.md) 뒤 7 갈래 병렬 정리로 화면 문구 전체에서 걷어냄. 원장 옛 문장은 `UserFacingMessage.punctuate` 가 화면 경계에서 평서형으로 바꿈 |
| D-11 | BIOS, BMC, OS 목록의 렌더가 중간에 끊김. 템플릿이 `T(IntegrityStatusView).badgeClass(...)` 를 부르는데, devtools 샌드박스에서 `T(...)` 의 클래스와 화면 데이터의 enum 이 다른 클래스로더에서 와서 메서드를 찾지 못했다(무결성 상태가 null 이 아니면 발생, 테스트 데이터는 null 이라 통과) | 해소. 배지 색을 `IntegrityStatus.badgeClass()` 인스턴스 메서드로 옮기고 템플릿의 정적 메서드 호출을 없앰. 규칙(8절)과 검사(`noStaticProjectMethodCallInTemplates`) 추가 |
| D-12 | 접촉 배지 '끊어짐' 이 '~짐' 꼴 | 해소. '연결 단절' |
| D-13 | 넓은 입력 아래 힌트가 가운데쯤에서 꺾이고 한국어가 음절 사이에서 끊김('있 / 습니다'). 원인은 `.n-hint { max-width: 68ch }` 와 본문의 기본 줄바꿈(`word-break: normal`) | 해소. 힌트 폭 제한 삭제, `body` 에 `word-break: keep-all` 과 `overflow-wrap: break-word`. 단계 패널의 폭 제한 해제 규칙은 쓸모가 없어 삭제 |
| D-14 | 본문과 표의 보조 글자(12px 층)가 지나치게 작음(사용자 지적) | 해소. 14px 미만 글자 층을 한 단(1px)씩 올림(137 곳). 배지, 상태 태그, 단계 번호, 개수 표지, 사이드바는 그대로. `n-label` 과 `n-label strong` 의 차이를 지키려고 strong 을 15px 로 |
| D-15 | 게스트 서버 표의 명시도 결함 두 개: 1024 폭의 머리 줄바꿈 규칙과 셀 여백 규칙이 (0,1,1) 이라 전역 `.n-table thead th` (0,1,2) 에 한 번도 이기지 못했다. 글자를 키우자 'SMBIOS UUID' 머리와 배지 열이 넘쳐 드러남 | 해소. 같은 깊이 선택자로 고치고, 10 열 표라 모든 폭에서 셀 여백 10px. 열 폭을 실측해 다시 배분(넘침 0) |

### 정리 뒤 기준선 (2026-11-17)

| 항목 | 정리 전 | 정리 뒤 |
|---|---|---|
| `DesignConventionTest` | 없음 | 7 항목 통과(전체 테스트 통과) |
| `copy-scan` 위반(107 화면) | 130 종 | 38 종, 전부 사용자 입력 데이터(정의서, 템플릿 이름, 서버 메모, ISO 설명) |
| `copy-scan` 받침 ㅁ 검토 | - | 17 종. 화면 문구에서 고칠 것은 '끊어짐'(D-12) 하나였고, 나머지는 명사(점검, 포함, 볼륨 등)와 사용자 입력. 명사는 도구의 목록에 더함 |
| `line-scan` 겹침 | 6 패턴 | 0 |
| `line-scan` 빈 구분선 | 판 밖 `n-form-actions` 3 화면 외 | D-9 의 정상 간격만 남음 |
| `overflow-scan` 넘침(107 화면, 1440 과 1024) | - | 0(글자 크기 변경 뒤 열 폭 재배분까지 반영) |

## 재개

1. 샌드박스를 띄운다(누적 메모리의 샌드박스 레시피, 디자인 스트림은 7797).
2. `./gradlew test --tests 'com.example.serverprovision.design.*'` 로 정적 규약을 본다.
3. `scripts/design-audit/README.md` 대로 두 도구를 돌리고 이 원장의 기준선과 비교한다.
4. 새 발견은 위 표에 번호를 이어 적고, 규약을 바꿨다면 DESIGN.md 와 `DesignConventionTest` 를 같은 커밋에서 고친다.
