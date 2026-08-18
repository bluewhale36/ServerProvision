# S16-1 어휘 불일치 전수 진단 브리핑

| 항목 | 내용 |
|---|---|
| 문서 종류 | S16-1 진단 브리핑 (읽기 전용 조사 결과) |
| 작성 시각 | 2026-08-18 KST |
| 대상 브랜치 | `dev` (커밋 `888172f` 시점 작업 트리) |
| 소스 변경 | 없음 (조사 중 코드 · 템플릿 · CSS 를 수정하지 않았다) |

## 조사 범위

측정 대상은 실행 경로에 있는 소스 네 묶음이다.

- `src/main/resources/templates/` 의 html 76 개 (전부 한국어 문자열을 가진다)
- `src/main/java/` 의 java 870 개 중 한국어 문자열 리터럴을 가진 405 개
- `src/main/resources/static/` 의 js 52 개 (전부 한국어 문자열을 가진다)
- `src/main/resources/messages.properties` 38 줄
- `src/main/resources/static/` 의 css 15 개 (축 4 전용)

측정은 파이썬 스크립트로 코퍼스를 뽑아 세었다. 템플릿은 html 주석을 제거한 뒤 텍스트 노드와 속성값을 분리해 추출했고, java 는 주석 줄을 제외한 문자열 리터럴만, js 는 주석 줄을 제외한 문자열 · 템플릿 리터럴만 담았다. 이렇게 모은 사용자 노출 문자열 레코드는 4205 건이고, 이를 문장 단위로 쪼개면 4897 개다.

## 한계

이 진단이 보장하지 못하는 것을 먼저 적는다.

- **추출 정확도의 한계.** 정규식 기반 추출이라 Thymeleaf 표현식 안에서 조립되는 문구(`${...} + '...'` 형태)나 java 텍스트 블록에 걸친 여러 줄 문자열은 일부 조각으로 잡히거나 누락된다. 특히 `th:text` 의 삼항 분기 양쪽 문구는 둘 다 잡히지만 그것이 한 자리의 두 상태라는 사실은 자동으로 판별되지 않는다.
- **개발자용 문자열 혼입.** `data-native-submit` 처럼 사용자에게 보이지 않고 개발자 주석 대용으로 쓰이는 속성이 있다. 축 1 최종 집계에서는 이 속성(18 곳)을 제외했으나, java 쪽 로그 · 내부 예외 메시지 중 화면까지 도달하지 않는 것은 완전히 걸러내지 못했다. 축 1 의 java 수치는 이 만큼 과대 집계일 수 있다.
- **판정의 한계.** 축 2 의 "같은 액션인가 다른 액션인가" 는 엔드포인트와 서비스 메서드를 확인해 판정했으나, 확인 비용이 큰 일부 쌍은 "판단 보류" 로 두었다.
- **축 4 는 참고용이다.** CSS 클래스 규칙 확정은 디자인 실험 종료 후로 이미 결정되어 있으므로, 여기서는 현황 측정과 결손 탐지만 한다.

---

## 0. 요약

| 축 | 핵심 수치 |
|---|---|
| 축 1 문체 | 분류된 문장 1761 · 합니다체 1021 (58.0%) · 요청형 361 (20.5%) · 해요체 41 |
| 축 2 동사 | 버튼 · 버튼형 링크 라벨 287 개 중 고유 문구 128 종 · 불일치 판정 9 쌍 · 정당 구분 6 쌍 · 판단 보류 3 쌍 |
| 축 3 용어 | 한국어 라벨을 가진 enum 37 개 · 정의 문서 0 건 · 정리 대상 용어 후보 42 개 |
| 축 4 CSS | 정의된 클래스 491 개 (공용 281 · 도메인 237) · 정의 없이 쓰이는 클래스 36 개 · 사용처 없는 정의 82 개 |

축별로 가장 강한 근거를 하나씩만 먼저 든다.

- 축 1: `templates/error.html` 의 "요청을 처리하지 못했습니다" 와 `static/global/error-modal.js` 의 "요청을 처리하지 못했어요." 는 같은 뜻의 같은 문장이며, 전달 경로가 서버 렌더인지 클라이언트 모달인지에 따라서만 문체가 갈린다.
- 축 2: 같은 `restore` 엔드포인트를 호출하는 버튼이 관리 목록에서는 "복구", 휴지통 목록에서는 "복원" 이다. 휴지통 목록 한 파일 안에서 버튼은 "복원", 같은 행의 tooltip 은 "부모부터 복구해 주세요" 로 두 낱말이 함께 쓰인다.
- 축 3: "회수" 는 서버 decommission · 고아 마커의 격리 구역 이송 · 이탈한 삭제 자원의 휴지통 재이송, 세 가지 서로 다른 동작에 모두 쓰인다.
- 축 4: `n-form-banner` 는 27 개 파일에서 29 회 쓰이지만 어느 css 파일에도 정의가 없다.

---

## 1. 축 1 — 종결 어미 · 문체

### 1.1 전체 분포

문장 단위로 종결 형태를 분류한 결과다. 개발자용 속성 `data-native-submit` 은 제외했고, 종결 어미가 없는 라벨 · 명사구는 분류 대상에서 빠졌다.

| 유형 | 건수 | 비율 | 소스 분포 |
|---|---|---|---|
| 합니다체 (문장 끝) | 1021 | 58.0% | Java 492 · 템플릿 354 · JS 164 · messages 11 |
| 합니다체 (문중에만) | 243 | 13.8% | Java 145 · 템플릿 78 · JS 20 |
| 요청형 · 해주세요 | 191 | 10.8% | Java 127 · JS 38 · 템플릿 26 |
| 요청형 · 하세요 | 133 | 7.6% | Java 73 · 템플릿 44 · JS 16 |
| 명사형 종결 | 86 | 4.9% | Java 41 · JS 23 · 템플릿 22 |
| 해요체 | 41 | 2.3% | JS 25 · 템플릿 11 · Java 5 |
| 요청형 · 기타 세요 / 주세요 | 23 | 1.3% | 템플릿 12 · Java 5 · JS 5 · messages 1 |
| 요청형 · 해 주세요 | 13 | 0.7% | 템플릿 9 · Java 3 · JS 1 |
| 평서체 (~한다) | 9 | 0.5% | Java 9 |
| 요청형 · 하십시오 | 1 | 0.1% | Java 1 |

"합니다체 (문중에만)" 은 문장 중간에 `~합니다` 가 있으나 추출된 조각이 문장 끝에서 잘린 경우다. 대부분 실제로는 합니다체 문장이며, 둘을 합치면 1264 건으로 전체의 71.8% 다.

요청형은 네 표기를 합쳐 361 건이다. 사용자가 표본으로 제시한 템플릿 한정 수치(하세요 43 · 해주세요 14 · 해 주세요 8)는 템플릿 파일의 원시 출현 횟수로 재현되며, 전수로 넓히면 java 와 js 쪽이 템플릿보다 많다.

### 1.2 영역별 분포

같은 유형이 어느 영역에 몰리는지를 본다. `tpl` 은 템플릿 디렉토리, `java` 는 최상위 패키지, `js` 는 static 하위 디렉토리다.

| 유형 | 상위 영역 |
|---|---|
| 합니다체 | java/management 162 · java/provisioning 121 · java/global 116 · tpl/provisioning 112 · tpl/management 109 |
| 요청형 · 해주세요 | java/management 95 · js/management 28 · java/global 21 · tpl/management 19 |
| 요청형 · 하세요 | java/management 46 · tpl/management 20 · tpl/fragments 15 · java/maintenance 12 |
| 요청형 · 해 주세요 | tpl/provisioning 5 · tpl/fragments 2 · tpl/maintenance 2 · java/global 2 |
| 해요체 | js/maintenance 12 · js/global 7 · tpl/maintenance 5 · tpl/fragments 4 |
| 명사형 종결 | java/global 13 · java/maintenance 11 · tpl/provisioning 10 · java/execution 10 |

요청형은 management 자원 도메인에 크게 몰려 있다. 자원 등록 · 업로드 화면이 사용자에게 무언가를 시키는 문장을 많이 담기 때문으로 보인다. 해요체는 maintenance 와 global 의 js 에 몰려 있다(1.5 절에서 따로 본다).

### 1.3 요청형 세 표기의 공존

"해주세요" · "해 주세요" · "하세요" 는 같은 요청을 서로 다르게 적은 것이다. 가장 뚜렷한 증거는 부모 자원이 삭제 상태여서 자식을 단독 복구할 수 없다는 **동일한 안내 문장**이 두 철자로 존재한다는 사실이다.

- 붙여 쓴 쪽 (5 곳): `templates/management/bios/list.html:283` · `templates/management/bmc/list.html:240` · `templates/management/os/list.html:394` · `templates/fragments/management/subprogram/miller.html:224` 의 tooltip "부모 메인보드가 삭제 상태입니다. 부모부터 복구해주세요." 와 `global/exception/ChildLifecycleBlockedByParentException.java:67` 의 `case "DELETED" -> "부모부터 복구해주세요."`
- 띄어 쓴 쪽 (2 곳): `templates/maintenance/trash/list.html:102` 의 "자식만 단독 복구할 수 없으니 부모부터 복구해 주세요." 와 `global/trash/RestoreBlockReason.java:27` 의 "부모 자원이 삭제 상태라 자식 단독 복구가 불가능합니다. 부모부터 복구해 주세요."

같은 차단 사유를 설명하는 문장이며, 하나는 자원 목록 화면의 tooltip 이고 다른 하나는 휴지통 목록의 차단 사유다. 어느 쪽이 정본인지 정한 적이 없어 두 표기가 나란히 유지되고 있다.

"하십시오" 는 단 1 건이며 사용자 화면이 아니라 기동 로그 경고다 — `global/marker/service/ProvisionMarkerService.java:83` 의 "운영 배포 전 PROVISION_MARKER_SECRET 환경변수로 반드시 override 하십시오."

### 1.4 같은 화면 요소 부류인데 문체가 갈리는 자리

화면 요소 부류별로 문체를 세었다. 부류는 템플릿의 class 와 속성으로 식별했다.

| 요소 부류 | 총 건수 | 문체 분포 |
|---|---|---|
| 도움말 (`n-hint`) | 94 | 합니다체 54 · 무종결 22 · 요청형 14 · 해요체 4 |
| 오류 · 알림 배너 | 42 | 합니다체 19 · 무종결 19 · 요청형 4 |
| 빈 목록 안내 (`n-empty*`) | 41 | 합니다체 24 · 무종결 8 · 요청형 8 · 해요체 1 |
| placeholder | 54 | 전부 무종결 |

**빈 목록 안내**가 가장 짧고 정형적인 자리인데도 갈린다. `maintenance/quarantine/list.html:45` 은 "복구 대기 중인 격리 항목이 없습니다.", `maintenance/reconciliation/snoozed-list.html:30` 은 "보관 중인 드리프트가 없습니다.", `maintenance/trash/list.html:28` 은 "휴지통이 비어있습니다." 로 합니다체인데, 바로 옆 화면인 `maintenance/trash/purge-log.html:80` 은 "감사 로그가 없어요." 다. 같은 maintenance 영역, 같은 "목록이 비었다" 라는 자리에서 문체가 바뀐다.

**오류 안내**는 전달 경로에 따라 갈린다. 서버가 렌더하는 오류 페이지 `templates/error.html:25` 은 "요청을 처리하지 못했습니다" 이고, 클라이언트가 띄우는 오류 모달 `static/global/error-modal.js:65` 과 `:91` 은 "요청을 처리하지 못했어요." 다. 문장이 사실상 같으므로 이것은 표현 선택의 차이가 아니라 표준 부재의 결과로 읽힌다. 같은 대비가 검증 실패 문구에서도 나타난다 — `messages.properties` 의 "입력값의 형식이 올바르지 않습니다." 와 `static/maintenance/trash/trash-action.js:63` 의 "요청 형식이 올바르지 않아요." 다.

**placeholder** 는 네 부류 중 유일하게 종결 어미가 전부 없어 표면적으로는 일관돼 보이나, 내부 패턴이 넷으로 갈린다.

| 패턴 | 건수 | 예 |
|---|---|---|
| `예: ` 로 시작하는 예시형 | 42 | `management/board/new.html` "예: MS03-CE0-000" |
| 명사구 · 설명형 | 6 | `provisioning/server-detail.html` "운영자 식별 이름 (미지정 가능)" |
| `~ 입력` 명령형 | 5 | `fragments/management/confirm-purge.html` "자원명을 정확히 입력..." |
| `비워두면 ~` 조건형 | 1 | `provisioning/setting-new.html` "비워두면 root 계정 잠금" |

명령형 5 건 중에서도 `fragments/management/nudge-modal.html` 은 "자원명 입력", `fragments/management/confirm-purge.html` 은 "자원명을 정확히 입력..." 으로, 같은 typed-name 입력 칸인데 말줄임표와 수식어 유무가 다르다.

### 1.5 해요체가 몰린 자리

해요체 41 문장(문장 분류 기준)의 실제 출현 지점을 원문 기준으로 다시 세면 60 문장이며 25 개 파일에 흩어져 있다. 무작위로 흩어진 것이 아니라 두 덩어리를 이룬다.

**첫째 덩어리 — 클라이언트 오류 · 확인 모달의 js.** `static/global/error-modal.js` 5 · `static/maintenance/trash/trash-action.js` 7 · `static/maintenance/reconciliation/list.js` 5 · `static/global/form-submit.js` 2 · `static/management/common/confirm-modal-base.js` 2 · `static/provisioning/setting-lifecycle.js` 2 · `static/provisioning/server-group-detail.js` 2. "서버와 통신할 수 없어요.", "요청이 거절되었어요." 같은 문구가 여러 파일에 복제돼 있다.

**둘째 덩어리 — 휴지통 운영 화면.** `templates/maintenance/trash/settings.html` 6 · `templates/maintenance/trash/list.html` 3 · `templates/maintenance/trash/purge-log.html` 2 와 `global/trash/dto/request/TrashSettingsRequest.java` 3 · `TrashSettingsRequestValidator.java` 2. "휴지통에서 자동 영구삭제까지의 보관 기간이에요." 처럼 도움말과 검증 메시지가 모두 해요체다.

같은 성격의 운영 설정 화면인 `templates/maintenance/reconciliation/settings.html` 은 합니다체다. 두 화면은 나란히 놓인 자매 화면인데 문체가 다르다.

그 밖에 산발적으로 남은 해요체가 있다. `templates/management/bios/bios-new.html:56` 과 `templates/management/bmc/bmc-new.html:38` 의 "보드를 다시 바꿀 수 있어요.", `templates/management/os/list.html` 의 "포함된 ISO 도 함께 삭제돼요." 처럼 삭제 · 복구 확인 모달의 보조 설명에서 나타난다. 이들은 `data-resource-extra` 로 전달되는 모달 부가 설명이라는 공통점이 있다.

### 1.6 명사형 종결

명사형 종결 86 건은 대부분 상태 배지와 enum 라벨이다. `DriftStatus.OPEN` 의 "조치 필요", `RestoreBlockReason.GHOST` 의 "복구 불가", `IntegrityStatus.NOT_VERIFIED` 의 "미검증", 목록 셀의 "없음" 등이다. 배지는 짧아야 하므로 명사형 자체는 자연스럽지만, 같은 배지 자리에서 "조치 필요"(명사형)와 "해결됨"(용언 종결)이 섞이는 지점이 있다 — `DriftStatus` 세 상수의 라벨이 각각 "조치 필요" · "해결됨" · "보관" 으로 세 가지 품사 형태다.

---

## 2. 축 2 — 액션 동사 변형

### 2.1 수집 방법

버튼 · 버튼형 링크 · 폼 제출 input · 제목(h1 · h2 · h3) · 모달 데이터 속성 · js 가 대입하는 라벨을 따로 뽑아 747 개 라벨 레코드를 만들었다. 이 중 실제로 액션을 일으키는 버튼과 버튼형 링크는 287 개이고 고유 문구는 128 종이다.

가장 많이 쓰이는 버튼 라벨은 취소 39 · 복구 14 · 삭제 14 · Deprecated 해제 13 · 영구 삭제 10 · 수정 10 · 저장 10 순이다.

### 2.2 판정 표

| 쌍 | 판정 | 요지 |
|---|---|---|
| 영구 삭제 / 영구삭제 | 불일치 | 띄어쓰기만 다른 동일 표기 |
| 복구 / 복원 | 불일치 | 같은 `restore` 엔드포인트 |
| 사용 중단 / Deprecated 표시 / 지원 중단 | 불일치 | 같은 `deprecate` 엔드포인트 |
| Deprecated 해제 / 권고 해제 | 불일치 | 같은 `undeprecate` 엔드포인트 |
| 등록 / 추가 (ISO) | 불일치 | 같은 화면 전이의 두 이름 |
| 신규 X 등록 / X 등록 | 불일치 | 한 페이지 안 두 라벨 |
| 생성 / 작성 / 만들기 | 불일치 | 정의서 · 템플릿 · 그룹에 제각각 |
| 취소 / 닫기 / 나중에 | 불일치 | 모달 이탈 버튼의 세 어휘 |
| 수정 / 편집 | 불일치 | 자원 편집 제목에서 하나만 이탈 |
| 삭제 / 폐기 | 정당 구분 | 대상이 등록 자원 · 격리 파일로 다름 |
| 삭제 / 정리 | 정당 구분 | 엔드포인트가 다름 (`clear-ghost`) |
| 삭제 / 제거 | 정당 구분 | 미저장 폼 요소 제거 |
| 비활성화 / 사용 중단 | 정당 구분 | 서로 다른 축 |
| 할당 / 지정 | 정당 구분 | 서버 할당 vs 그룹 표준 지정 |
| 회수 / 복구 | 정당 구분 | 서버 decommission vs 자원 restore |
| 점검 / 검증 | 판단 보류 | 범위가 다르나 경계가 흐림 |
| 재시도 / 다시 시도 | 판단 보류 | 라벨과 산문의 분업일 수 있음 |
| 적용 / 반영 / 저장 | 판단 보류 | 폼 제출과 구성 반영의 경계 |

### 2.3 불일치로 판정한 쌍의 근거

**영구 삭제 / 영구삭제.** 띄어 쓴 쪽은 `fragments/management/confirm-purge.html` 의 제목 "자원 영구 삭제" 와 버튼 "영구 삭제", 관리 목록 다섯 화면의 버튼 "영구 삭제", `static/provisioning/setting-lifecycle.js` 의 "정의서 영구 삭제" 다. 붙여 쓴 쪽은 `maintenance/trash/list.html:152` 의 버튼 "영구삭제", `maintenance/trash/purge-log.html` 의 제목 "영구삭제 감사 로그", `maintenance/trash/settings.html` 의 라벨 "자동 영구삭제" · "영구삭제 점검 주기", `JobType.TRASH_AUTO_PURGE` 의 "자동 영구삭제" 다.

가장 좁은 범위의 충돌은 두 군데다. 첫째, `fragments/management/nudge-modal.html` 한 파일 안에서 본문은 "영구삭제를 확정하려면 아래 자원명을 정확히 입력하세요" 인데 버튼은 "기존 영구 삭제 후 등록" 이다. 둘째, `global/trash/enums/PurgeOrigin.java` 의 `jobTitle` 이 만드는 작업 이름은 "자원 영구삭제 — {이름}" 인데 그 작업을 일으키는 확인 모달의 제목은 "자원 영구 삭제" 다. 같은 어구가 한 화면 흐름 안에서 두 철자로 나타난다. 전체 출현은 "영구 삭제" 48 · "영구삭제" 30 이다.

**복구 / 복원.** 관리 자원 목록(bios · bmc · board · os · raidcard)의 버튼은 "복구" 이고 공용 확인 모달 `fragments/management/confirm-restore.html` 의 제목도 "자원 복구" 다. 반면 `maintenance/trash/list.html:122` 의 버튼은 "복원" 이고 `provisioning/setting-detail.html:63` 의 버튼도 "복원" 이다. 둘 다 `POST /…/restore` 를 호출하고 서비스는 `LifecycleService.restore(id, cascade)` 하나다.

같은 파일 안 혼용이 결정적이다. `maintenance/trash/list.html` 은 버튼을 "복원" 으로 적고, 같은 행의 부모 차단 tooltip 에서는 "부모부터 복구해 주세요", cascade 라디오 제목에서는 "하위 자원도 함께 복구", 모달 부가 설명에서는 "원래 경로로 복원돼요." 를 쓴다. 한 행 안에서 복원과 복구가 번갈아 나온다. `DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL` 의 해결 라벨도 "자원 복원" 이다.

**사용 중단 / Deprecated 표시 / 지원 중단.** `provisioning/setting-detail.html:46` 의 버튼은 "사용 중단" 이고 `POST /provisioning/setting/{id}/deprecate` 를 호출한다. 관리 자원 다섯 목록의 같은 성격 버튼은 "Deprecated 표시" 이며 공용 모달 `fragments/management/confirm-deprecate.html` 의 제목도 "Deprecated 표시" 다. 세 번째 표기가 `static/provisioning/setting-form.js:655` 의 배지 "지원 중단" 과 `:721` 의 "지원 중단(Deprecated) 자원" 이다. 한국어 라벨을 쓸 것인지 영문 용어를 그대로 노출할 것인지가 화면마다 다르고, 한국어를 쓰기로 한 화면 안에서도 "사용 중단" 과 "지원 중단" 이 갈린다. 전체 출현은 "Deprecated" 60 · "사용 중단" 16 · "지원 중단" 3 이다.

**Deprecated 해제 / 권고 해제.** 위 쌍의 역동작이다. 관리 목록과 `fragments/management/confirm-undeprecate.html` 은 "Deprecated 해제", `provisioning/setting-detail.html` 은 "권고 해제" 다. 두 화면 모두 `undeprecate` 를 호출한다.

**등록 / 추가 (ISO).** `management/os/list.html:432` 의 링크는 "ISO 추가" 인데, 그 링크가 여는 페이지 `management/os/iso-new.html:13` 의 제목은 "ISO 등록" 이고 그 페이지의 제출 버튼도 "등록" 이다. 사용자가 "추가" 를 눌러 "등록" 화면에 도착한다.

**신규 X 등록 / X 등록.** 관리 목록 다섯 화면은 모두 상단 헤더에 "+ 신규 X 등록", 목록이 비었을 때의 안내 자리에 "+ X 등록" 을 함께 둔다. 같은 페이지에서 같은 목적지로 가는 두 버튼의 라벨이 다르다.

| 파일 | 헤더 라벨 | 빈 목록 라벨 |
|---|---|---|
| `management/os/list.html` | + 신규 OS 버전 등록 (34행) | + OS 버전 등록 (92행) |
| `management/bios/list.html` | + 신규 BIOS 등록 (41행) | + BIOS 등록 (123행) |
| `management/bmc/list.html` | + 신규 BMC 등록 (23행) | + BMC 등록 (96행) |
| `management/board/list.html` | + 신규 메인보드 모델 등록 (35행) | + 메인보드 모델 등록 (94행) |
| `management/raidcard/list.html` | + 신규 RAID 카드 등록 (34행) | + RAID 카드 등록 (93행) |

제목 쪽도 갈린다. `management/board/new.html` 과 `management/raidcard/new.html` 의 제목은 "신규 … 등록" 인데 `management/os/new.html` 은 "OS 버전 등록", `management/os/iso-new.html` 은 "ISO 등록", `management/bios/bios-new.html` 은 "BIOS 번들 등록" 으로 "신규" 가 없다.

**생성 / 작성 / 만들기.** 같은 "새로 만든다" 액션에 세 어휘가 쓰인다.

| 대상 | 목록 화면 링크 | 폼 제목 | 제출 버튼 |
|---|---|---|---|
| 세팅 정의서 | 새 정의서 작성 | 신규 세팅 정의서 작성 | 정의서 생성 |
| BIOS 세팅 템플릿 | 새 템플릿 작성 | BIOS 세팅 템플릿 작성 | F4 템플릿 저장 |
| 서버 그룹 | 새 그룹 | 그룹 만들기 | 만들기 |

세팅 정의서는 진입 링크와 제목이 "작성" 인데 제출 버튼만 "생성" 이다. 서버 그룹은 진입 링크가 "새 그룹" 이라 동사가 아예 없고, 서버 목록에서 들어가는 다른 경로는 "이 묶음으로 그룹 만들기" 다.

**취소 / 닫기 / 나중에.** 모달을 그냥 빠져나가는 버튼이 세 어휘를 쓴다. 확인 모달 계열 여섯(`confirm-deprecate` · `confirm-extend` · `confirm-purge` · `confirm-restore` · `confirm-soft-delete` · `confirm-undeprecate`)과 `nudge-modal` · `delete-reject-modal` · `reconciliation-modals` 는 "취소" 다. `fragments/management/directory-browse-panel.html:27` 과 `static/global/background-jobs.js:303` 은 "닫기" 다. `fragments/management/orphan-recovery-modal.html:68` 은 "나중에" 다.

"닫기" 두 곳은 파괴적 액션이 없는 조회 패널이라 "취소" 가 어색하다는 설명이 가능하다. "나중에" 는 격리 상태를 유지한 채 나중에 다시 처리한다는 의미를 담은 의도적 선택으로 보인다 — 같은 파일의 주석이 "modal 만 닫음. 격리는 PENDING 으로 유지" 라고 적고 있다. 그러나 세 어휘를 나누는 기준이 문서화된 적은 없다.

**수정 / 편집.** 자원 편집 페이지 제목은 "BIOS 메타 수정" · "BMC 메타 수정" · "메인보드 모델 수정" · "OS 버전 수정" · "ISO 수정" · "RAID 카드 수정" · "세팅 정의서 수정" 인데 `management/subprogram/subprogram-edit.html:11` 만 "드라이버 편집" 이다. 그 페이지로 들어가는 목록 버튼은 다른 자원과 똑같이 "수정" 이다. BIOS 와 BMC 만 "메타" 라는 수식어를 붙이는 것도 별개의 갈림이다.

### 2.4 정당한 구분으로 판정한 쌍의 근거

**삭제 / 폐기.** "폐기" 는 `fragments/management/orphan-recovery-modal.html` 과 `static/global/orphan-recovery.js` 에서만 쓰이며 대상이 등록에 실패해 격리된 업로드 파일이다. 등록 자원이 아니어서 휴지통을 거치지 않고 바로 없앤다. 같은 모달의 주석도 "격리 파일 영구 삭제. 파괴적이므로 파일명 typed-name 일치 후에만 확정" 이라고 적어 자원 영구 삭제와 절차가 같음을 밝히면서 낱말만 구분한다. 대상이 다르므로 구분 자체는 성립하나, 아래 5 장에 "구분 유지 여부를 정할 항목" 으로 올린다.

**삭제 / 정리.** `maintenance/trash/list.html:106` 의 "정리" 버튼은 `POST /maintenance/trash/{rt}/{rid}/clear-ghost` 로, 복구할 파일이 없는 유령 DB 기록만 지운다. 일반 삭제와 엔드포인트가 다르고 `RestoreBlockReason.GHOST` 의 안내도 "이 행의 [정리] 로 기록을 지울 수 있습니다." 로 라벨과 일치한다.

**삭제 / 제거.** "제거" 는 `provisioning/setting-new.html` 의 단계 카드 접기 요약("펌웨어 업데이트 단계 제거" 등 4 건)에서만 쓰인다. 저장되지 않은 폼 안의 블록을 빼는 동작이라 영속 자원 삭제와 구분된다. 다만 같은 화면의 파티션 · 사용자 · 서비스 행 삭제 버튼은 "삭제" 다.

**비활성화 / 사용 중단.** `provisioning/setting-detail.html` 은 두 버튼을 나란히 둔다. "비활성화" 는 `toggle` 로 신규 할당을 막고, "사용 중단" 은 `deprecate` 로 할당은 허용하되 경고만 붙인다. 코드 주석이 "deprecated ≠ disabled" 를 명시하고 배지도 "비활성" 과 "사용 중단" 으로 나뉜다. 서로 다른 축이므로 통합 대상이 아니다.

**할당 / 지정.** "할당" 은 서버 또는 그룹 멤버에게 정의서를 붙이는 동작이고, "지정" 은 그룹의 표준 정의서를 정하는 동작이다(`static/provisioning/group-definition-picker.js` 의 "표준 정의서 지정" · "표준으로 지정"). 표준을 정하는 것과 실제로 붙이는 것이 다른 단계이므로 구분이 성립한다. 같은 화면에 "표준 적용" 버튼이 따로 있어 지정과 적용이 분리돼 있음을 보여준다.

**회수 / 복구.** `provisioning/server-detail.html:570` 의 "이 서버 회수" 는 `decommission` 이고 `GuestServerStatus.DECOMMISSIONED` 의 라벨이 "회수됨" 이다. 자원 복구와는 전혀 다른 동작이다. 다만 "회수" 라는 낱말 자체가 다른 뜻으로도 쓰이는 문제는 축 3 에서 따로 다룬다.

### 2.5 판단 보류

**점검 / 검증.** "점검" 은 reconciliation 도메인 전반의 이름이다 — "자원 무결성 점검"(메뉴 · 페이지 제목 · `JobType.PATH_RECONCILIATION`), "지금 점검" · "정밀 점검" · "다시 점검"(스캔 실행), "점검 이력", "점검 운영 설정". "검증" 은 개별 자원 행의 버튼(`management/bios/list.html:188` 등)과 `JobType.INTEGRITY_VERIFICATION` 의 "무결성 검증", `system/asset/dashboard.html:18` 의 "재검증" 이다. 전자는 디스크 전수 스캔, 후자는 지정한 자원 하나의 마커 서명 · 해시 확인으로 범위가 다르다.

경계가 흐린 지점이 있다. `ScanDepth.QUICK` 의 설명이 "자원 무결성 점검 · 마커 서명 검증" 으로 두 낱말을 한 문장에 함께 쓰고, `IntegrityVerificationStage` 의 단계 이름은 "서명 검증" · "해시 재계산" 이다. 사용자 입장에서 "점검" 과 "검증" 이 서로 다른 일이라는 것을 화면만 보고 알 수 있는지는 확인하지 못했다. 판정은 표준 제정 단계로 넘긴다.

**재시도 / 다시 시도.** 버튼 라벨은 "재시도" 3 곳(`orphan-recovery-modal` · `server-detail` · `purge-log` 의 "실패 자원 재시도")이고, 안내 산문에서는 "다시 시도" 가 쓰인다("점검 운영 설정에서 켠 뒤 다시 시도하세요."). 전체 코퍼스 출현은 "재시도" 28 · "다시 시도" 28 로 균형이 맞는다. 라벨은 한자어 축약형, 산문은 풀어쓴 형태라는 분업일 가능성이 있어 보류한다.

**적용 / 반영 / 저장.** 폼 제출 버튼은 대부분 "저장" 10 건이다. 예외가 넷이다 — `system/pxe-infra/network-form.html:144` 의 "저장 및 적용", `provisioning/bmc-password.html:47` 의 "전송", `fragments/management/directory-browse-panel.html:36` 의 "이 경로로 적용", `maintenance/trash/purge-log.html:61` 의 "필터 적용". "저장 및 적용" 은 DB 저장과 dhcpd 조각 재적용이 별개 단계라는 사실을 담고 있고, "이 경로로 적용" 과 "필터 적용" 은 서버 저장이 아니라 화면 상태 반영이다. "전송" 은 BMC 로 비밀번호를 내보내는 동작이라 저장과 다르다. 각각 이유가 있어 보이나 "적용" 이 서로 다른 두 뜻(재적용 · 화면 반영)에 쓰이는 것은 남는 문제다. "반영" 은 산문에서 4 회 쓰이고 버튼 라벨로는 쓰이지 않는다.

---

## 3. 축 3 — 도메인 용어 후보

### 3.1 현황

용어를 정의한 문서는 저장소에 없다. `docs/` 최상위는 `README.md` · `T3-checklist.md` · `staging-vm-bootstrap.md` · `26-07-29_01-03-05_pxe-network-config-setup.md` · `dev/` 이고 `docs/dev/` 는 `architecture.md` · `global.md` · `adr/` 다. 용어집에 해당하는 파일이 없다. `discussion/` 의 문서 28 개(이 문서 제외)에는 용어 설명이 산문으로 흩어져 있으나 목록화돼 있지 않다.

한국어 라벨을 필드로 들고 있는 enum 은 72 개 중 37 개다. 이 라벨들이 사실상 용어 정의의 유일한 원천이며, 코드를 읽어야만 도달할 수 있다.

### 3.2 용어 표

정의 없이는 뜻을 알기 어려운 항목을 모았다. "출현" 은 사용자 노출 문자열 코퍼스에서 센 횟수다.

| 용어 | 화면 표기 | 코드 실재 지점 | 출현 |
|---|---|---|---|
| 자원 | 자원 | `global/marker/ResourceType` · `Markable` | 265 |
| 점검 | 자원 무결성 점검 | `JobType.PATH_RECONCILIATION` · `PathReconciliationService` | 157 |
| 정의서 | 세팅 정의서 | `provisioning/setting/entity/SettingDefinition` | 155 |
| 마커 | 마커 | `global/marker/ProvisionMarkerService` · `.provision.json` | 89 |
| 휴지통 | 휴지통 | `global/trash/*` · `.soft-deleted/` | 82 |
| 할당 | 할당 | `provisioning/assignment/entity/SettingAssignment` | 82 |
| 보관 | 보관 · 보관 목록 | `DriftStatus.SNOOZED` · `SnoozeWindow` | 73 |
| Deprecated | Deprecated 표시 · 사용 중단 | `global/lifecycle/LifecycleStage.DEPRECATED` | 60 |
| 드리프트 | 드리프트 | `global/marker/DriftKind` (12 종) | 48 |
| 무결성 | 무결성 봉인 · 무결성 점검 | `IntegrityStatus` · `SealedFileCondition` | 41 |
| 회수 | 회수됨 · 격리 구역으로 회수 | `GuestServerStatus.DECOMMISSIONED` 외 2 곳 | 29 |
| 격리 | 격리 · 업로드 실패 복구 | `global/orphan/entity/OrphanQuarantine` | 28 |
| 표준 | 표준 정의서 | `provisioning/group` 그룹 기본 정의서 | 27 |
| 지문 | 지문 재계산 | `HashAcceptStage.ACCEPTING` | 21 |
| 개시 | 프로비저닝 개시 · 개시 전 | `AssignmentState.ACTIVE_CONSUMED` | 19 |
| 정본 | 정본 갱신 · 정본으로 수용 | `HashAcceptService` | 18 |
| 봉인 | 봉인 | `execution/asset/spi/SealedFileCondition` | 18 |
| 수용 | 내용 수용 | `JobType.HASH_ACCEPT` | 16 |
| 스냅샷 | 할당 스냅샷 | `provisioning/assignment` 소프트 참조 사본 | 11 |
| 진단 리눅스 | 진단 리눅스 | `ProvisioningPhase.DIAGNOSE_LINUX` | 9 |
| 미아 마커 | 미아 마커 | `DriftKind.SOFTDEL_MARKER_STRAY` | 5 |
| 유령 | 유령 DB 기록 | `DriftKind.GHOST_DB_ROW` | 3 |
| 무소속 | 무소속 서버 | 그룹 미소속 `GuestServer` | 4 |
| 묶음 | 이 묶음으로 그룹 만들기 | 서버 목록의 등록 시각 · 스펙 묶음 | 4 |
| nudge | nudge 세션 (영문 그대로) | `management/common/nudge/NudgeAction` | 58 |

정의 초안은 다음과 같다. 이 문장들은 초안이며 표준 제정 단계에서 확정한다.

- **자원** — 프로비저닝에 쓰이는 등록 대상. OS 이미지 · ISO · 메인보드 모델 · BIOS · BMC · RAID 카드 · 드라이버 · 유틸리티가 해당한다. 디스크 파일과 마커가 짝을 이룬다.
- **마커** — 자원 파일 옆에 두는 HMAC 서명 파일(`.provision.json`). 자원의 신원과 내용 해시를 담아 파일이 옮겨지거나 바뀐 것을 알아낸다.
- **드리프트** — DB 기록과 디스크 실물이 어긋난 상태. 12 종으로 나뉘며 종류마다 원인 · 영향 · 해결 방법이 다르다.
- **점검** — 디스크를 훑어 드리프트를 찾아내는 작업. 일반 점검은 마커 서명만, 정밀 점검은 파일 내용 해시까지 본다.
- **보관** — 지금 처리하기 어려운 드리프트를 정해진 기간 동안 목록에서 물려 두는 것. 코드 이름은 snooze 다.
- **휴지통** — 삭제한 자원을 즉시 지우지 않고 옮겨 두는 곳(`.soft-deleted/`). 보관 기간이 지나면 자동으로 영구 삭제된다.
- **격리** — 등록이 중간에 실패했을 때 업로드된 파일을 지우지 않고 따로 떼어 두는 것. 코드 이름은 quarantine 이고 화면 메뉴 이름은 "업로드 실패 복구" 다.
- **회수** — (1) 서버를 프로비저닝 대상에서 빼는 것 (2) 주인 없는 마커를 격리 구역으로 옮기는 것 (3) 휴지통을 벗어난 삭제 자원을 휴지통으로 되돌리는 것.
- **봉인** — 시스템 자산(진단 리눅스 · TFTP 아티팩트)에 마커를 발급해 이후 변조를 감지할 수 있게 하는 것.
- **정본** — 무결성 판정의 기준이 되는 내용. 파일 내용이 바뀐 것을 의도된 교체로 인정하면 현재 내용을 정본으로 삼는다.
- **수용** — 바뀐 파일 내용을 정본으로 받아들이고 마커의 해시를 다시 계산하는 것.
- **지문** — 파일 내용 해시. 화면에서는 "지문", 코드와 일부 화면에서는 "해시" 다.
- **정의서 (세팅 정의서)** — 프로비저닝 절차를 단계별로 적어 둔 문서. 서버에 할당하면 그 서버가 이 절차로 프로비저닝된다.
- **할당** — 정의서를 서버 또는 서버 그룹에 붙이는 것.
- **스냅샷** — 할당 시점의 정의서 내용을 복사해 굳혀 둔 것. 원본 정의서가 나중에 바뀌어도 이미 할당된 서버는 굳어진 내용으로 실행된다.
- **개시** — 할당된 정의서로 실제 프로비저닝을 시작하는 것. 개시 전에는 재할당이 가능하고 개시 후에는 막힌다.
- **표준** — 서버 그룹에 정해 두는 기본 정의서. 지정만으로는 붙지 않고 별도로 적용해야 멤버에게 할당된다.
- **묶음** — 서버 목록에서 등록 시각과 하드웨어 스펙이 같은 서버들을 자동으로 모아 보여주는 단위. 영속 개념이 아니다.
- **무소속** — 어느 그룹에도 속하지 않은 서버.
- **유령 DB 기록** — 삭제 표시된 자원이 휴지통에도 디스크에도 없고 DB 행만 남은 상태.
- **미아 마커** — 삭제된 자원의 마커가 본체 파일 없이 홀로 발견된 상태.
- **진단 리눅스** — PXE 로 부팅해 하드웨어 정보를 수집하는 경량 리눅스(Alpine 기반).
- **nudge** — 등록하려는 자원과 같은 것이 이미 휴지통이나 Deprecated 상태로 있을 때 등록을 멈추고 진행 방법(그래도 등록 · 기존 영구 삭제 후 등록 · 취소)을 사용자에게 묻는 절차. 제한 시간이 있는 세션으로 관리된다.

`DriftKind` 12 종은 그 자체가 용어군이므로 별도로 옮겨 적는다. 라벨과 설명이 이미 코드에 있어 정의 초안을 새로 쓸 필요가 없다.

| 상수 | 화면 라벨 | 해결 라벨 |
|---|---|---|
| `PATH_DRIFT` | 경로 이동됨 | 등록 경로 갱신 |
| `MISSING` | 자원 소실 | (자동 해결 없음) |
| `ORPHAN` | 미등록 마커 | 격리 구역으로 회수 |
| `SIGNATURE_INVALID` | 마커 서명 불일치 | 마커 서명 재발급 |
| `HASH_MISMATCH` | 내용 변경 감지 | 현재 내용을 정본으로 수용 |
| `SOFTDEL_ESCAPE_TO_ORIGINAL` | 삭제 자원 복귀 | 자원 복원 |
| `SOFTDEL_ESCAPE_TO_OTHER` | 삭제 자원 위치 이탈 | 휴지통으로 회수 |
| `SOFTDEL_MARKER_STRAY` | 미아 마커 | (본문 참조) |
| `TRASH_LOST` | 휴지통 자원 소실 | 휴지통 기록 정리 |
| `TRASH_MARKER_STALE` | 잔여 마커 정리 필요 | 잔여 마커 정리 |
| `GHOST_DB_ROW` | 유령 DB 기록 | 유령 기록 삭제 |
| `RESOURCE_REPLICA` | 자원 중복 존재 | (본문 참조) |

### 3.3 같은 개념에 표기가 여럿인 경우

- **격리** — 코드는 `OrphanQuarantine` 이고 화면은 세 가지로 부른다. 메뉴와 페이지 제목은 "업로드 실패 복구", 본문 설명은 "격리", `DriftKind.ORPHAN` 의 해결 라벨은 "격리 구역으로 회수" 다. 격리라는 낱말이 제목에 한 번도 나오지 않아 메뉴만 보고는 무엇을 하는 화면인지 알기 어렵다.
- **보관** — `DriftStatus.SNOOZED` 의 화면 표기다. 그런데 `maintenance/trash/settings.html:36` 의 라벨도 "TTL (보관 기간)" 이고 `fragments/maintenance/reconciliation-modals.html:31` 의 라벨도 "보관 기간" 이다. 앞쪽은 휴지통 자원이 자동 영구 삭제되기까지의 기간이고 뒤쪽은 드리프트를 목록에서 물려 두는 기간으로, 전혀 다른 개념이 같은 이름을 쓴다.
- **회수** — 위 3.2 의 정의대로 세 가지 뜻으로 쓰인다.
- **단계** — 세 층위에 쓰인다. 정의서 안의 `SettingProcess`("단계 추가" · "펌웨어 업데이트 단계"), 실행 엔진의 `ProvisioningPhase`("진단 리눅스" 등 7 개), 그리고 개발 인벤토리 코드(E1 · U3 등)의 "단계" 다. 앞의 둘은 사용자에게 보이고 매핑은 `SettingProcessPhaseMapper` 가 담당한다.
- **보존기간 / 보관 기간** — 휴지통 TTL 을 가리키는 두 표기가 함께 쓰인다. "보존기간" 11 회(`fragments/management/confirm-extend.html` 의 제목 "보존기간 연장" 포함), "보관 기간" 9 회다. 붙여 쓴 것과 띄어 쓴 것의 차이도 있다.
- **지문 / 해시** — `HashAcceptStage` 의 화면 표기는 "지문 재계산" 인데 같은 도메인의 다른 문구는 "해시 재계산"(`IntegrityVerificationStage.RECOMPUTE_HASH`)이다. 출현은 "해시" 28 · "지문" 21 이다.
- **nudge** — 코드 이름이 한국어 문구 안에 그대로 섞여 사용자에게 노출된다. 로그 문자열을 제외해도 22 개 파일 58 곳이다. `static/global/nudge-modal.js:144` 의 "nudge 세션이 만료되었습니다. 다시 시도해주세요.", `management/bios/exception/BiosNudgeRequiredException.java:32` 의 "동일한 해시의 자원이 이미 존재합니다. nudge 결정이 필요합니다.", `fragments/management/nudge-modal.html:45` 의 tooltip "이 시간 안에 결정하지 않으면 nudge 세션이 만료됩니다." 가 그 예다. 정작 그 모달의 제목은 "동일한 자원이 이미 존재합니다" 여서 화면 어디에도 nudge 가 무엇인지 알려주는 자리가 없다. 같은 사건을 부르는 이름도 갈린다 — `PurgeOrigin.NUDGE_REPLACE` 의 화면 표기는 "nudge 교체" 인데 휴지통 감사 로그 화면의 안내는 "충돌 교체" 다.
- **오펀 / 고아 / ORPHAN** — 코드 주석과 템플릿 주석에서 "ISO 오펀(등록 실패 격리)" 로 쓰이고, `DriftKind.ORPHAN` 의 화면 라벨은 "미등록 마커" 다. 사용자 화면에는 "오펀" 이 나오지 않는다.

### 3.4 상태 라벨의 품사 혼재

`GuestServerStatus` 는 "등록됨" · "프로비저닝 중" · "완료" · "실패" · "회수됨" 으로 과거형 · 진행형 · 명사가 섞여 있다. `ProvisioningStatus` 는 "작업 대기" · "작업 중" · "작업 완료" · "작업 실패" · "작업 건너뜀" 으로 "작업" 접두를 일관되게 붙인다. `DriftStatus` 는 "조치 필요" · "해결됨" · "보관" 으로 셋 다 다른 형태다. 같은 성격의 상태 배지인데 라벨 작성 규칙이 enum 마다 다르다.

---

## 4. 축 4 — CSS 클래스 어휘 (참고용)

### 4.1 정의 현황

css 파일 15 개에 클래스 491 개가 정의돼 있다. 템플릿의 인라인 `<style>` 에 정의된 것이 3 개 더 있다.

| 구분 | 고유 클래스 |
|---|---|
| 공용 (`static/css/` + `static/global/`) | 281 |
| 도메인별 (`management/` · `provisioning/` · `maintenance/` · `system/`) | 237 |
| 양쪽에 걸친 것 | 27 |

파일별 정의 수는 `global/style.css` 134 · `provisioning/bios-setting.css` 91 · `provisioning/setting.css` 54 · `provisioning/server.css` 52 · `css/table-list.css` 35 · `global/background-jobs.css` 34 · `css/confirm-modal.css` 27 · `css/miller.css` 20 · `management/os/os-page.css` 20 · `css/form-validation.css` 17 · `maintenance/trash/trash-list.css` 17 · `css/grouped-list.css` 16 · `css/accordion.css` 15 · `system/diagnostic-asset.css` 11 · `system/asset.css` 5 다.

### 4.2 접두 계열

| 계열 | 정의 수 | 주 정의 파일 · 담당 |
|---|---|---|
| `n-` | 269 | `global/style.css` 76 등 전 영역 공용 |
| 무접두 | 87 | `global/style.css` 47 · `provisioning/bios-setting.css` 30 |
| `bios-` | 50 | `provisioning/bios-setting.css` |
| `is-` · `has-` | 23 | 상태 수식자 |
| `cm-` | 21 | `css/confirm-modal.css` 확인 모달 |
| `trash-` | 17 | `maintenance/trash/trash-list.css` |
| `da-` | 10 | `system/diagnostic-asset.css` |
| `btn-` | 9 | `global/style.css` |
| `asset-` | 5 | `system/asset.css` |

`n-` 이 사실상의 전역 접두이고 나머지는 화면 단위 접두다. 그런데 화면 단위 접두를 붙이는 규칙 자체가 일정하지 않다. `bios-setting.css` 는 `bios-` 접두 50 개와 `bsd-`(BIOS setting detail 로 추정) 접두 17 개를 함께 정의하고, BMC 비밀번호 화면은 `bmc-` 접두 13 개를 무접두 군에 섞어 두고 있다. `n-` 을 쓰는 화면과 자체 접두를 쓰는 화면을 나누는 기준은 코드에서 확인되지 않는다.

전역 오류 모달은 `gem-` 접두 7 개(`gem-overlay` · `gem-card` · `gem-title` · `gem-message` · `gem-status` · `gem-actions` · `gem-backdrop`)를 쓴다. 같은 성격의 확인 모달이 `cm-` 를 쓰는 것과 짝이 맞지 않는다.

무접두 87 개 중 상당수는 Bootstrap 계열 이름이다 — `btn-primary` · `form-control` · `alert-danger` · `card-body` · `badge` · `text-muted` · `progress-bar` 등. 이들은 4.4 에서 보듯 거의 전부 사용처가 없다.

### 4.3 정의 없이 템플릿 · js 에서만 쓰이는 클래스

36 개다. `class` 속성과 Thymeleaf 표현식 안의 문자열 리터럴, js 의 `classList` · `querySelector` · 템플릿 문자열을 모두 훑어 세었다.

| 클래스 | 사용 | 성격 |
|---|---|---|
| `n-form-banner` | 27 파일 29 회 | 폼 오류 배너 컨테이너 |
| `n-check` | 5 파일 8 회 | 체크박스 래퍼 |
| `n-btn-extract` | 2 파일 4 회 | 추출 버튼 |
| `n-badge-` | 3 파일 4 회 | 문자열 결합용 접두 조각 |
| `n-empty-state` | 3 파일 3 회 | 빈 목록 컨테이너 |
| `n-pkg-group` | 2 파일 5 회 | 패키지 그룹 |
| `n-iso-section-wrap` · `n-env-groups-wrap` · `n-subprogram-section` · `n-subprogram-miller` · `n-version-specific` · `n-code-block` · `n-asset-table` · `n-process-card` · `n-row-warning` | 각 1~2 회 | 개별 레이아웃 |
| `n-btn-outline` | 1 파일 2 회 | 아래 설명 참조 |
| `mk3-2-delete-form` | 3 파일 3 회 | js 훅 (슬라이스 코드가 이름에 남음) |
| `pSize` · `pSizeUnit` · `pGrow` · `pDiskName` · `pMountPoint` · `pFileSystem` | 각 3~7 회 | js 훅 (파티션 행) |
| `uUsername` · `uPassword` · `uSudoer` · `uKeep` · `uKeepWrap` · `uEncrypted` | 각 3 회 | js 훅 (사용자 행) |
| `svcName` · `svcAction` | 각 3 회 | js 훅 (서비스 행) |
| `bios-dep` · `bios-select-body` | 각 2 회 | 미정의 |
| `table-sm` · `standard` · `is-` | 각 1~3 회 | 미정의 · 조각 |

기지 2 건에 대한 재실측 결과를 적는다.

- `n-form-banner` 는 27 개 파일에서 29 회 쓰이며 정의가 없다. 누적 메모리의 "23 곳" 보다 늘어난 값이다.
- `n-btn-outline` 은 **접미사 없는 형태로는 `maintenance/reconciliation/drift-detail.html` 한 파일에서 2 회만** 쓰인다. `n-btn-outline-success` · `-danger` · `-info` · `-warning` 네 변형은 `global/style.css` 에 정의돼 있고 21 개 템플릿 · 10 개 js 에서 널리 쓰인다. 즉 미정의인 것은 변형이 아니라 접미사 없는 기본형뿐이다. 누적 메모리의 "29 곳" 은 변형을 포함해 센 값이거나 다른 기준으로 센 값으로 보이며, 이 진단의 측정과 맞지 않는다.

js 훅으로 쓰이는 `pSize` · `uKeep` · `svcName` 계열 17 개는 `n-` 계열의 kebab-case 와 다른 camelCase 명명이다. 스타일을 붙일 목적이 아니라 `querySelector('.pSize')` 로 값을 읽기 위한 이름이어서 css 정의가 없는 것은 의도된 것으로 보이나, 클래스 어휘 안에 두 가지 명명 규칙이 섞이는 결과가 됐다. `mk3-2-delete-form` 은 슬라이스 인벤토리 코드가 클래스 이름에 남은 사례다.

### 4.4 사용처가 없는 정의

82 개다. `global/style.css` 에 60 개가 몰려 있다.

| 파일 | 미사용 | 내용 |
|---|---|---|
| `global/style.css` | 60 | Bootstrap 계열 이름 대부분 + `n-btn-info` · `n-btn-success` · `n-card-footer` · `n-page-sm` · `n-select-sm` · `n-table-empty` · `n-actions` · `n-footer-right` |
| `global/background-jobs.css` | 7 | `is-done` · `is-error` · `is-info` · `is-pending` · `is-running` · `is-success` · `n-bgjob-stage-chunk` |
| `provisioning/bios-setting.css` | 6 | `is-current` · `is-disabled` · `is-indent-1`~`4` |
| `system/diagnostic-asset.css` | 4 | `da-current` · `da-section` · `da-section-hint` · `da-section-title` |
| `css/table-list.css` | 4 | `is-current` · `is-resolved` · `n-actions` · `n-detail-fields` |
| `system/asset.css` | 2 | `asset-seal-card` · `asset-seal-row` |
| `css/grouped-list.css` | 1 | `n-glist-sub` |

`is-` 계열 미사용 다수는 js 가 동적으로 붙이는 상태 클래스일 가능성이 있다. `classList.add` 와 템플릿 문자열은 검사에 포함했으나 문자열 결합으로 조립하는 경우(`'is-' + status`)는 잡히지 않는다 — 실제로 `is-` 라는 조각이 사용 목록에 남아 있는 것이 그 흔적이다. 따라서 이 82 개 중 일부는 오탐이며, 제거 대상으로 확정하려면 개별 확인이 필요하다.

반면 `global/style.css` 의 미사용 60 개 중 52 개는 무접두 이름이고 그 대부분이 Bootstrap 계열이다 — `btn-primary` · `form-control` · `alert-danger` · `card-body` · `badge` · `text-muted` · `progress-bar` 같은 이름이 정의만 있고 어디서도 쓰이지 않는다. 프로젝트가 `n-` 체계로 옮겨 간 뒤 남은 잔재로 보인다. 나머지 8 개는 `n-` 접두인데도 쓰이지 않는 것들이다(`n-actions` · `n-btn-info` · `n-btn-success` · `n-card-footer` · `n-footer-right` · `n-page-sm` · `n-select-sm` · `n-table-empty`).

### 4.5 부수 관찰

CLAUDE.md 는 인라인 스타일을 금지하는데, 템플릿에 `style="..."` 속성이 32 개 파일에서 267 회 남아 있다. `fragments/management/nudge-modal.html` 은 모달 카드 · 제목 · 목록 · 타이머 배지의 레이아웃을 전부 인라인으로 쓰고, `management/*/list.html` 다섯 화면의 "+ X 등록" 링크는 `style="width: 100%;"` 를 반복한다. 축 4 의 본 주제는 아니나 클래스 어휘를 정비할 때 함께 다뤄야 할 대상이라 기록한다.

---

## 5. 정비 대상 후보 목록

축 1 · 축 2 에서 불일치로 판정한 항목만 사실로 적는다. 우선순위와 정본 선택은 이 문서에서 정하지 않는다.

**축 1 — 문체**

1. 요청형 세 표기 공존. "해주세요" 191 · "하세요" 133 · "해 주세요" 13 · "하십시오" 1. 특히 "부모부터 복구해주세요" 와 "부모부터 복구해 주세요" 는 같은 안내 문장의 두 철자다(붙임 5 곳 · 띄움 2 곳).
2. 해요체 60 문장 · 25 파일. 클라이언트 오류 모달 js 군(6 파일 23 문장)과 휴지통 운영 화면(3 템플릿 11 문장 + 검증 dto 2 파일 5 문장)에 몰려 있다.
3. 서버 렌더 오류와 클라이언트 모달 오류의 문체 분리. `error.html` "요청을 처리하지 못했습니다" 대 `error-modal.js` "요청을 처리하지 못했어요.".
4. 검증 실패 문구의 문체 분리. `messages.properties` 합니다체 대 `TrashSettingsRequestValidator` · `trash-action.js` 해요체.
5. 빈 목록 안내의 문체 혼재. 41 건 중 합니다체 24 · 무종결 8 · 요청형 8 · 해요체 1.
6. 도움말(`n-hint`) 94 건의 문체 혼재. 합니다체 54 · 무종결 22 · 요청형 14 · 해요체 4. 자매 화면인 휴지통 운영 설정(해요체)과 점검 운영 설정(합니다체)이 갈린다.
7. placeholder 내부 패턴 4 종. 예시형 42 · 명사구 6 · 명령형 5 · 조건형 1. 같은 typed-name 입력 칸에서 "자원명 입력" 과 "자원명을 정확히 입력..." 이 갈린다.
8. 상태 enum 라벨의 품사 규칙 부재. `GuestServerStatus` · `ProvisioningStatus` · `DriftStatus` 셋의 작성 방식이 서로 다르다.

**축 2 — 액션 동사**

9. 영구 삭제 / 영구삭제. 띄움 48 · 붙임 30. `nudge-modal.html` 한 파일 안, `PurgeOrigin.jobTitle` 과 `confirm-purge.html` 제목 사이에서 충돌한다.
10. 복구 / 복원. 같은 `restore` 엔드포인트. `maintenance/trash/list.html` 한 파일 안에서 둘 다 쓰인다.
11. 사용 중단 / Deprecated 표시 / 지원 중단. 같은 `deprecate` 엔드포인트, 세 라벨.
12. Deprecated 해제 / 권고 해제. 같은 `undeprecate` 엔드포인트, 두 라벨.
13. ISO 추가 / ISO 등록. 링크 라벨과 도착 페이지 제목이 다르다.
14. 신규 X 등록 / X 등록. 관리 목록 다섯 화면 각각에서 같은 목적지로 가는 두 라벨이 공존한다. 폼 제목 쪽도 "신규" 유무가 갈린다.
15. 생성 / 작성 / 만들기. 정의서는 작성 · 작성 · 생성, 템플릿은 작성 · 작성 · 저장, 그룹은 무동사 · 만들기 · 만들기.
16. 취소 / 닫기 / 나중에. 모달 이탈 버튼 세 어휘. 나누는 기준이 문서화돼 있지 않다.
17. 수정 / 편집. 자원 편집 제목 7 개 중 `subprogram-edit.html` 만 "편집". 부수로 BIOS · BMC 만 "메타" 수식어를 붙인다.

**축 3 — 용어 (정의 부재 자체가 항목이다)**

18. 용어 정의 문서가 없다. 유일한 원천이 37 개 enum 의 한국어 라벨이며 코드를 열어야 도달한다.
19. 같은 이름이 다른 개념을 가리키는 항목 — 회수(3 뜻) · 보관 기간(2 뜻) · 단계(3 층위).
20. 같은 개념에 이름이 여럿인 항목 — 격리 / 업로드 실패 복구, 보존기간 / 보관 기간, 지문 / 해시, 오펀 / 미등록 마커.
21. 코드 이름이 정의 없이 사용자 문구에 그대로 노출되는 항목 — nudge (22 개 파일 58 곳). 같은 enum 상수 `PurgeOrigin.NUDGE_REPLACE` 가 작업 이름으로는 "nudge 교체", 감사 로그 표시명으로는 "충돌 교체" 를 낸다.

---

## 6. 표준 제정이 결정해야 할 질문 — 표준안 초안 (2026-08-18 갱신)

당초 이 절은 질문만 담았다. 사용자가 4개 질문에 인용문으로 답해 **확정** 됐고, 나머지에는 세션의 **권고** 를 붙인다. 진행 방식: 확정은 그대로 굳고, 권고는 이견이 있는 항목만 인용문으로 뒤집으면 된다. 응답이 없는 권고는 채택으로 간주해 표준 문서(tsv · 작법 대응표)로 옮긴다.

**문체**

1. 정본 종결 어미를 합니다체로 고정할 것인가. 고정한다면 해요체 60 문장은 전부 바꾸는가, 아니면 특정 자리(예: 파괴적 액션의 부가 설명)에는 남기는가.
> 합니다 체 고정.

**확정** — 서술의 정본은 합니다체다. 파생: 해요체 60 문장은 전량 교체 대상이며(Q5 확정과 정합 — 예외 자리 없음), 출현이 두 덩어리(오류 모달 js 군 · 휴지통 화면 계열)에 몰려 있어 정비가 국소적으로 가능하다.

2. 요청형의 정본 표기는 무엇인가. "하세요" · "해주세요" · "해 주세요" 중 하나로 통일하는가, 아니면 요청형 자체를 줄이고 합니다체 서술로 바꾸는가.
> 하십시오 체 고정.

**확정** — 요청 · 명령의 정본은 하십시오체("~하십시오")다. 하십시오체는 합니다체와 같은 격식 등급(합쇼체)의 명령형이므로 Q1 확정과 한 몸으로 정합적이다. 파생: "하세요" 133 · "해주세요" 191 · "해 주세요" 13 이 전량 교체 대상이다(예: "부모부터 복구해주세요" → "부모부터 복구하십시오").

3. 요청형을 유지한다면 "해주세요" 를 붙여 쓰는가 띄어 쓰는가.

**소멸** — Q2 확정으로 "해주세요" 계열 표기 자체가 사라지므로 질문이 소멸한다.

4. 오류 안내의 문체를 전달 경로와 무관하게 통일하는가. 서버 렌더와 클라이언트 모달이 같은 문장을 쓰게 할 것인가.

**절반 확정 + 권고** — 문체는 Q5 확정으로 통일된다. 남는 절반(문장 자체의 통일)은 **통일을 권고**한다: 같은 상황에는 같은 정본 문장 하나를 두고, 서버 렌더와 클라이언트 모달이 그것을 공유한다(실현 수단은 S16-2 의 `messages.properties` 집약 — 정본 문구를 키로 두고 양쪽이 같은 키를 쓴다). 근거: 문장이 다르면 사용자가 서로 다른 오류로 오인한다.

> 통일.

5. 화면 요소 부류별로 문체를 다르게 정할 것인가, 한 문체로 전부 덮을 것인가.
> 하나의 문체로 전체 통일한다.

**확정** — 요소 부류별 분화 없이 단일 문체다. 파생: placeholder 와 상태 배지처럼 종결 어미가 없는 자리는 문체가 아니라 형태의 문제이므로 Q6 · Q7 이 별도로 정한다.

6. placeholder 의 정본 패턴은 무엇인가.

**권고** — 예시형 `예: ` 를 정본으로 한다(현행 42 건 다수파, 형식이 자명하다). 예시를 만들 수 없는 자유 서술 필드(메모 등)에만 명사구를 허용하고, 명령형 · 조건형 placeholder 는 폐지한다 — 조건 안내("비워두면 root 계정 잠금")는 placeholder 가 아니라 도움말(`n-hint`) 자리로 옮긴다.

> 권고 수용.

7. 상태 배지 라벨의 품사를 규칙으로 정할 것인가.

**권고** — 배지는 **명사형을 기본**으로 하고 진행 상태에만 "~ 중" 을 허용한다(예: 조치 필요 · 해결 · 보관 · 완료 · 실패 · 프로비저닝 중). 근거: 배지는 짧을수록 배지답고, `ProvisioningStatus` 의 일관 선례가 이미 있다. 실제 enum 라벨 개정의 적용은 S13(사용자 노출 enum 표시 정비) 소관으로 이관한다 — 규칙은 여기서 정하고 S13 이 소비한다.

> 권고 수용.

**액션 동사**

8. "영구 삭제" 와 "영구삭제" 중 정본은 무엇인가.

**권고** — **"영구 삭제"(띄움)**. 합성어로 사전에 등재된 낱말이 아니므로 띄는 쪽이 규범에 부합하고, 출현도 48 대 30 으로 다수다.

> 권고 수용.

9. "복구" 와 "복원" 중 정본은 무엇인가.

**권고** — **"복구"**. 관리 목록 다섯 화면과 공용 확인 모달 제목이 이미 복구이고, 연관 문구("부모부터 복구" · "하위 자원도 함께 복구")도 복구 계열이라 교체 반경이 작다. `DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL` 의 해결 라벨 "자원 복원" 도 "자원 복구" 로 함께 바꾼다(적용은 S13).

> 권고 수용.

10. deprecate 의 한국어 정본은 무엇인가.
> '사용 중단 권고' 로 한다.

**확정** — deprecate = **"사용 중단 권고"**. 파생: "Deprecated 표시" · "지원 중단" 등 세 표기가 전량 교체 대상이고, 영문 "Deprecated" 의 화면 노출은 폐지한다. 역동작 undeprecate 는 기존 `setting-detail` 표기였던 **"권고 해제"** 가 자연스럽게 정본이 된다(질문 12번 쌍도 이것으로 함께 해소). 배지 자리에는 다섯 글자가 길 수 있어 축약형 허용 여부("중단 권고")를 S13 에서 정한다.

11. 새로 만드는 동작의 정본 동사는 무엇인가.

**권고** — 대상 성격별 3어휘를 배타적으로 나눈다: **등록**(디스크 실물이 있는 자원 — OS · ISO · 보드 · 펌웨어 · 드라이버), **작성**(내용을 지어 만드는 문서 — 세팅 정의서 · BIOS 세팅 템플릿), **생성**(그 밖의 구조물 — 서버 그룹). 한 대상에는 한 동사만 쓴다 — 정의서 흐름의 제출 버튼 "정의서 생성" 은 "작성" 계열로, 그룹의 "만들기" 는 "생성" 으로 맞춘다.

12. "신규" 라는 수식어를 붙이는 규칙을 둘 것인가.

**권고** — **폐지**한다. "등록" 자체가 새로 들여오는 행위라 "신규" 는 중복 수식이다. 관리 목록 헤더 · 빈 목록 · 폼 제목 모두 "+ X 등록" · "X 등록" 으로 통일한다(빈 목록 쪽 현행이 정본이 된다).

13. 모달 이탈 버튼의 정본은 "취소" 인가.

**권고** — 실재하던 구분을 규칙으로 명문화한다: **작업을 포기하는 확인 모달 = "취소"** / **파괴적 액션이 없는 조회 패널 = "닫기"** / "나중에" 는 orphan-recovery 한 곳의 의도적 예외(격리 유지 의미)로 대응표에 등재해 유지한다.

> 권고 수용.

14. "수정" 과 "편집" 중 정본은 무엇인가.

**권고** — **"수정"**(7 중 6 다수). "드라이버 편집" 은 "드라이버 수정" 으로. "메타" 수식어는 제거한다 — 다른 자원의 수정 화면도 실제로는 메타데이터만 고치므로 BIOS · BMC 만 붙일 근거가 없다("BIOS 메타 수정" → "BIOS 수정").

> 권고 수용.

15. "폐기" 와 "영구 삭제" 의 구분을 유지하는가.

**권고** — **유지**한다. 대상이 다르다(등록 자원 vs 등록 실패 격리 파일). 사용자 설명 책임은 용어집 등재와 모달 문구 보강(기존 S12 모달 카피 정합 소관)으로 이행한다.

> 권고 수용.

16. "점검" 과 "검증" 의 경계를 정의로 확정하는가.

**권고** — **확정해 구분을 유지**한다: 점검 = 디스크 전수 스캔으로 드리프트를 찾는 일, 검증 = 지정한 자원 하나의 마커 서명 · 해시를 확인하는 일. 두 낱말을 한 문장에 섞는 혼용 문구(`ScanDepth.QUICK` 설명 등)는 정비 목록에 편입한다.

17. 버튼 라벨과 산문에서 같은 동작을 다르게 적어도 되는가("재시도" 대 "다시 시도").

**권고** — 분업을 명문화한다: **라벨 = "재시도"**, 산문에서는 "다시 시도하십시오" 를 허용한다. 28 대 28 로 이미 실재하는 분업이며, 라벨의 간결성과 산문의 자연스러움을 각각 살린다.

18. "적용" 을 두 뜻(외부 재적용 · 화면 상태 반영)에 계속 쓰는가.

**권고** — **현행 허용 + 대응표에 두 용법을 명시**한다. 사용자 혼동의 실증이 없고, 개명 비용("필터 적용" 대체어 발명)이 이득을 넘는다.

**용어**

19. 용어집을 어디에 두는가.

**기결정** — 원본은 `docs/glossary.tsv`(한 용어 한 행, git diff 가능), 사람용 xlsx 는 tsv 에서 생성하는 산출물로 DOC 단계 소관(2026-08-18 사용자 확정).

20. 용어집의 SSOT 를 무엇으로 하는가.

**권고** — **표기의 SSOT = tsv 문서, 코드가 따른다.** enum 라벨을 원천으로 삼는 안은 이번 진단이 반증했다 — 라벨이 도메인별로 흩어져 있어 같은 상수가 두 표기를 내는 충돌(`NUDGE_REPLACE` 의 "nudge 교체" / "충돌 교체")을 아무도 못 잡았다. 변경 절차는 tsv 갱신 → 코드 반영 순이며 관문이 이를 강제한다.

21. 한 이름이 여러 뜻을 갖는 항목을 이름을 나눠 해소하는가.

**권고** — **회수와 보관 기간은 이름을 나누고, 단계는 정의 명시로 유지**한다. 분리안: "회수" 는 서버 decommission 전용으로 남기고, 고아 마커의 이송은 "격리 구역으로 이동", 이탈 자원의 재이송은 "휴지통으로 이동" 으로 바꾼다. 휴지통 TTL 은 **"보존 기간"**(기존 "보존기간" 계열을 띄어쓰기 정비해 채택), 드리프트 snooze 는 **"보관 기간"** 으로 남겨 두 개념이 다른 이름을 갖게 한다. "단계" 세 층위는 같은 화면에 동시 노출되는 실증이 아직 없어 용어집 정의로만 구분하고, 동시 노출 화면이 발견되면 재론한다.

22. 코드 이름이 사용자 문구에 노출되는 것(nudge)을 허용하는가.

**권고** — **금지 원칙을 세우고 nudge 의 한국어 정본을 정한다.** 1후보 = **"중복 확인"**(절차명 — "중복 확인 세션이 만료되었습니다"), 대안 = "충돌 확인". `PurgeOrigin.NUDGE_REPLACE` 의 두 표기("nudge 교체" · "충돌 교체")는 "중복 교체" 하나로 수렴한다. 코드 식별자(`NudgeAction` · `OrphanQuarantine` 등)는 개명하지 않는다 — 표기 대응은 tsv 가 관리하고 화면 문구만 다룬다.

23. 영문 약어(PXE · TFTP · BMC · BIOS · iPXE · TTL)를 화면에서 그대로 쓰는가.

**권고** — **그대로 노출을 허용**한다. 대상 사용자가 데이터센터 운영자라 이 약어들이 그들의 일상 어휘이고, 풀어쓰기 강제는 오히려 소음이다. tsv 에 전체 명칭을 등재하고, 개념 병기가 자연스러운 자리는 "보존 기간(TTL)" 형을 허용한다.

24. 용어집을 어떻게 강제하는가.

**권고** — 2중 강제: ① CLAUDE.md 작업 전 체크리스트에 관문 등재 — 새 안내 문구 · 새 라벨을 만들기 전에 작법 대응표를 연다(기확정 방향) ② **금지 표기 검사 스크립트** — tsv 의 금지 동의어 열을 읽어 템플릿 · java · js 를 grep 하는 경량 도구를 S16-2 산출물로 만들어 정비 후 회귀를 막는다. 문서 단독으로는 drift 가 재발한다는 것이 이 진단 자체의 증거다.

**CSS (규칙 확정은 디자인 실험 종료 후)**

25. 미정의 클래스 36 개를 지금 처리하는가.

**유보 확정** — 디자인 실험 종료까지 보류한다. 진단 §4 전체가 실험의 입력으로 전달되며, 실험이 전면 재작성을 앞둔 지금 선행 정비는 이중 작업이다.

26. js 훅 전용 클래스(`pSize` 계열 17 개)를 `data-` 속성으로 옮길 것인가.

**유보 + 방향 지지** — `data-` 속성 이전이 정석이나, 실행은 실험 후 CSS 정비와 함께 한다.

27. `global/style.css` 의 Bootstrap 잔재를 제거 대상으로 확정하는가.

**유보 + 방향 지지** — 제거가 맞으나 실험 후 일괄 처리한다.


> 아래는 기타 사항이다.
> 11, 12번을 비롯해서 다양한 단어나 표현이 사용되는만큼, 이를 MessageSource 를 이용해서 엄격하게 구분짓는다. 만일 이를 유지하는 비용이 많이 들거나, 단어의 가짓수가 더 많아지거나, 단어의 구분이 애매한 상황이 발생하는 경우 이 표현들을 단순화 시킨다.
> 권고 안의 '등록', '생성', '작성' 등을 전부 각 도메인별로 '신규' 버튼과, 신규 생성 화면에서는 '저장' 등으로 일원화 시킬 수도 있다. 현재는 이 안을 기각하나 상기 조건에 걸리는 일이 발생할 경우 이와 같이 진행한다.
---

## 7. 종결 — 표준 확정과 산출물 (2026-08-18)

사용자가 §6 의 권고를 전량 수용하고 말미에 첨언 2건을 남겨 표준이 확정됐다.

- **첨언 반영 — MessageSource 엄격 구분 + 조건부 단순화 조항.** 동사 · 표현의 구분은 `messages.properties`(MessageSource)로 엄격하게 유지한다. 단 유지 비용 과다 · 정본 가짓수 증가 · 구분 애매 상황이 발생하면 단순화한다 — 예비안(현재 기각)은 도메인별 "신규" 버튼 + 신규 화면 제출 "저장" 일원화. 이 조항은 작법 관문 문서에 등재했다.
- **세션이 같은 논리(출현 다수 + 코드 정합)로 추가 확정한 2건** — 정비 후보 20번의 미질문 쌍이다: 지문/해시 → **"해시"**, 격리 관련 표기 → **"격리"**(메뉴명 "업로드 실패 복구" 의 개정 여부는 화면 변경이므로 S16-2 에서 재검토). 이견이 있으면 뒤집을 수 있다.

**산출물 세 가지** —

1. `docs/glossary.tsv` — 표기의 SSOT. 정본 67행(도메인 용어 · 액션 동사 · 드리프트 12종 · 기술 약어) × 6열(정본 표기 · 분류 · 정의 · 금지 표기 · 코드 실재 · 비고). 사람용 xlsx 는 이 파일에서 생성한다(DOC 단계).
2. `.claude/domain-conventions/new-user-copy.md` — 새 문구 · 라벨 작성 전 여는 관문 문서. 문체 규칙 8개 + 조건부 단순화 조항 + 적용 시점 분업.
3. CLAUDE.md 작업 전 체크리스트에 「새 안내 문구 · 화면 라벨 → `new-user-copy.md`」 행 등재.

**적용 시점의 분업** — 신규 문구는 관문으로 즉시. 기존 문구 정비와 `messages.properties` 집약은 S16-2(디자인 실험 승격 합류). enum 라벨 개정은 S13. 금지 표기 검사 스크립트는 S16-2 산출물. CSS 는 디자인 실험 종료 후.
