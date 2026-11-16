# plan / report html 저작 규약 (디자인 객체 명세)

이 문서는 `plan/` 과 `report/` 에 두는 계획서(plan)와 보고서(report) html 을 작성할 때 쓰는 **디자인 객체(class, id, 컴포넌트), 문서 골격, 반응형 레이아웃, 인터랙션**의 단일 규약이다. plan-docx-architect 와 stage-report-architect 에이전트가 이 명세를 따른다.

이 명세는 제품 웹 UI 의 레퍼런스인 `DESIGN.md` 와 별개다. plan/report html 은 사용자와 AI 가 설계를 검토하는 자체 완결 문서이며, `DESIGN.md` 의 제품 UI 규약을 따를 필요가 없다.

배경: `.seq` 처럼 규약 없이 굳어 파일마다 다르게 쓰이던 객체가 수십 개 있었다(예: 데모 버튼을 `.n-demo-btn` 과 `.ux-btn` 두 이름으로, 비교 카드를 `.split-card` 와 `.compare-card` 로). 이 명세가 정본을 정해 드리프트를 막는다.

기준 구현은 `plan/plan_template.html` 이다. 이 명세와 템플릿이 어긋나면 이 명세를 정본으로 보고 템플릿을 맞춘다.

## 0. 문서 기본 규약

- **자체 완결 단일 파일**. 외부 CSS/JS/폰트/이미지 참조 없음. `<style>` 과 `<script>` 를 인라인한다. Notion 임베드가 sandboxed iframe 으로 렌더하므로 외부 의존이 있으면 깨진다.
- **파일명**: plan 은 `plan/YY-MM-DD_HH-MM-SS_<인벤토리코드>_plan.html`, report 는 `report/YY-MM-DD_HH-MM-SS_<단계명>_report.html`. 시각은 KST(Asia/Seoul), 구분자는 하이픈.
- **언어**: 본문 한국어. 코드/식별자/패키지명만 영어.
- **색 토큰(불가침)**: 모든 색은 `:root` CSS 변수로. 직접 hex 를 컴포넌트에 박지 않는다.

```css
:root {
  --bg:#f6f8fa; --card:#fff; --text:#1f2328; --text-muted:#57606a;
  --border:#d0d7de; --border-soft:#eaecef;
  --blue:#0075de; --blue-bg:#e6f0ff; --orange:#dd5b00; --orange-bg:rgba(221,91,0,0.10);
  --yellow:#C29343; --yellow-bg:#FAF3DD; --green:#1aae39; --green-bg:rgba(26,174,57,0.10);
  --red:#bf2626; --red-bg:rgba(191,38,38,0.10); --purple:#6f4cdd; --purple-bg:rgba(111,76,221,0.10);
  --mono:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
}
```

색 의미 규약: blue=정보/진행/CP1, green=성공/신규/추가/정상, yellow=주의/수정/CP3, orange=변경/CP4/강조, red=위험/삭제/CP5, purple=보조 강조, muted=비활성/부차.

## 1. 문서 골격

### 1-1. 최상위 구조

```
<header class="page-header"> … 제목 + .meta … </header>
<div class="layout">              ← grid 240px + 1fr
  <nav class="toc"> … 목차 + .toc-actions … </nav>
  <main>
    <input class="filter-box" id="filter">
    <details class="section" id="s1"> … </details>
    … s2 … s11 …
  </main>
</div>
<script> … 4 동작 + 🎬 데모 … </script>
```

### 1-2. 섹션 체계

각 섹션은 `<details class="section" id="sN">` 이고 `<summary>` + `.body` 로 구성한다. 데스크톱에서 `header.page-header` 와 `nav.toc` 는 sticky, ToC 는 `top:108px`.

| id | 섹션 | 비고 |
|---|---|---|
| `s0` | 재설계 동기 | 선택. v1→v2 재설계나 스코프 재정의 슬라이스에만. 일반 신규는 생략 |
| `s1` | 현재 상태(진단) | |
| `s2` | 요구사항 + 비목표 | |
| `s2-preview` | 🎬 미리보기 | **불가침. §2 직후. 진짜 인터랙티브 데모**(1-4 참고) |
| `s3` | URL / 데이터 흐름 | URL 표 + 흐름도(`.seq` 또는 `pre`) |
| `s4` | 도메인 모델(SSOT) | Entity 필드, VO/Enum, 도메인 메서드 |
| `s5` | 12단계 + CP | Step↔CP 표(CP1~CP7) + 승인 게이트(CP1 · CP4[중간 — 테스트 제외 main 구현 + 빌드 통과] · CP6 · CP7) 명기 — 무중단 구간 둘(CP1→CP4 게이트 지점 · CP4 승인→CP6 제출) |
| `s6` | Step 8 통합 테스트 | 성공/400/404/409/500 범주 |
| `s7` | 예외 계층 | 신규/재사용 + HTTP 매핑 |
| `s8` | 부산물 / 주의 / 결정 | Open Questions 표 포함 |
| `s9` | Verification | |
| `s10` | Critical Files | 신규/수정/유지 |
| `s11` | 다음 마일스톤 | `check-list` |

ToC 는 `nav.toc > ol#toc-list` 안에 `<li><a href="#sN">` 로. 🎬 미리보기 링크는 `class="preview"`(주황 강조). ToC 하단에 `.toc-actions`(전부 펴기/접기 버튼).

report 는 같은 골격을 쓰되 섹션 구성이 plan 11섹션과 다를 수 있다(보고 대상에 맞춤). 골격 CSS, ToC, filter, 반응형은 동일하게 유지한다.

## 2. 디자인 객체 카탈로그 (정본)

각 객체의 용도, 마크업, 변형, 반응형을 정의한다. 여기 없는 새 class 를 임의로 만들지 않는다 — 필요하면 이 명세에 먼저 추가한다.

### 2-1. 구조 / 레이아웃

| class | 용도 | 마크업 |
|---|---|---|
| `.page-header` | 상단 헤더(제목 + 메타) | `<header class="page-header">` |
| `.meta` | 헤더 안 메타 정보 줄 | `<div class="meta"><span><strong>라벨</strong> 값</span>…</div>` |
| `.layout` | 본문 2단 그리드(ToC + main) | `<div class="layout">` |
| `.toc` | 목차 사이드바 | `<nav class="toc">` |
| `.toc-actions` | 펴기/접기 버튼 묶음 | ToC 하단 |
| `.filter-box` | 섹션 검색 입력 | `<input class="filter-box" id="filter">` |
| `.section` | 접이식 섹션 | `<details class="section" id="sN"><summary>…</summary><div class="body">…</div></details>` |
| `.body` | 섹션 본문 | `.section > .body` |
| `.preview` | ToC 의 🎬 링크 강조 / `.section.preview` 헤더 강조 | |
| `.hidden` | filter 비매칭 숨김(`display:none`) | JS 가 토글 |

### 2-2. 콜아웃 (강조 노트)

핵심 진단, 비목표, 주의를 눈에 띄게 담는 블록.

```html
<div class="callout warn"><b>비 목표</b> : <ul><li>…</li></ul></div>
```

| 변형 | 색 | 용도 |
|---|---|---|
| `.callout` | blue | 정보/일반 |
| `.callout.warn` | yellow | 주의, 비목표 |
| `.callout.danger` | red | 위험, 결함 |
| `.callout.success` | green | 정상/드리프트 0 확인 |

콜아웃 안에는 rich 마크업(리스트, code, 강조) 가능.

### 2-3. 칩 / 배지

짧은 상태/분류 태그. `.chip`(각진 라벨)과 `.badge`(시뮬레이터용 둥근 상태).

```html
<span class="chip new">신규</span> <span class="chip mod">수정</span> <span class="chip cp4">CP4</span>
```

`.chip` 변형: `.new`/`.add`(green 신규·추가), `.mod`(yellow 수정), `.keep`(회색 유지), `.del`(red 삭제), `.move`(blue 이동), `.force`(red 강제), `.opt`(blue 옵션), `.dep`(yellow deprecated).

CP 칩: `.cp1`(blue) `.cp2`(green) `.cp3`(yellow) `.cp4`(orange) `.cp5`(red) `.cp6`(purple) `.cp7`(muted). CP6 · CP7 은 CH4 의 체크포인트 확장(CP1~CP7)에 맞춰 추가한 것으로, CP6=report 산출 · CP7=사용자 단독 E2E 를 가리킨다.

`.badge` 는 시뮬레이터의 상태 배지: `.on`(green 활성) `.off`(회색 비활성) `.dep`(yellow) `.del`(red). 본문 표에서는 `.chip` 을, 🎬 데모의 자원 상태 표시에는 `.badge` 를 쓴다.

### 2-4. 스윔레인 `.seq` (다층 시간 흐름)

**N 개 주체(레인) 사이의 시간순 데이터 흐름**을 보여주는 시퀀스 다이어그램. 이 용도로만 쓴다(§3 데이터 흐름). 첫 행은 주체(`.actor`), 이후 각 행은 한 시점의 상호작용을 주체별 칸(`.step`)으로 나열한다. 가로=주체, 세로=시간.

```html
<div class="seq">
  <div class="actor a1">브라우저</div><div class="actor a2">Controller</div><div class="actor a3">Service</div>
  <div class="step a1">폼 진입</div><div class="step a2">뷰 렌더</div><div class="step a3">조회</div>
  <div class="step a1">POST</div><div class="step a2">@Valid</div><div class="step a3">저장</div>
</div>
```

- 열 수는 주체 수만큼(`grid-template-columns:repeat(N,1fr)`). 2~4 열이 일반적.
- 레인 색: `.a1`(blue) `.a2`(green) `.a3`(orange) `.a4`(purple — 4-actor 흐름용). `.actor`/`.step` 에 같은 `aN` 을 붙여 색을 맞춘다. 빈 칸도 `<div class="step aN"></div>` 로 정렬을 유지한다.
- 반응형은 3-4 참고(좁은 화면에서 접지 않고 열 유지 + 가로 스크롤).

### 2-5. 시뮬레이터 (🎬 라이브 데모)

§2 직후 `s2-preview` 의 인터랙티브 데모. 상태를 바꿔 도메인 규칙(차단/전이/cascade)을 체험시킨다. **데모 판정 JS 는 서버 도메인 메서드와 동일해야 한다(드리프트 0)**.

| class | 용도 |
|---|---|
| `.ux-wrap` | 데모 전체 컨테이너 |
| `.ux-toolbar` | 상태 전환 버튼 줄 |
| `.group-label` | 툴바 라벨 |
| `.seg` | 상태 세그먼트 버튼(`.seg.active` 선택됨) |
| `.n-demo-btn` | 액션 버튼(`disabled` + `.tip[data-tip]` tooltip 으로 차단 사유) |
| `.ux-row` | 자원 행(`.muted` 비활성, `.child` 자식 들여쓰기) |
| `.label` | `.ux-row` 안 이름 |
| `.ux-caps` | 판정 캡션(monospace, `.t` red / `.f` green) |
| `.ux-message` | 결과 메시지(`.error`/`.success`) |
| `.tip` | `data-tip` 속성으로 hover tooltip |

데모 JS 규약: `demoBlocks(state)` 가 서버 판정을 1:1 재현, `demoRender()` 를 파일 끝에서 **1회 호출**(불가침). 버튼은 `demoBtn()` 이 `.n-demo-btn` + `.tip` 으로 생성.

### 2-6. 카드 / 그리드 (병렬 비교)

| class | 용도 | 마크업 |
|---|---|---|
| `.card-grid` | 정책/옵션 병렬 카드(auto-fit) | `<div class="card-grid"><div class="card">…</div>…</div>` |
| `.card` | 개별 카드. 색 변형 `.v-blue/green/orange/purple/red` | `.card h4` 는 monospace 제목 |
| `.split-grid` | 2단 비교 그리드 | `<div class="split-grid">…</div>` |
| `.split-card` | 비교 카드. `.before`(red) / `.after`(green) | BEFORE/AFTER 코드 대조 |
| `.state-flow` | 상태 전이 한 줄(monospace) | `ACTIVE ──deprecate──▶ DEPRECATED` |

### 2-7. 기타 컴포넌트

| class | 용도 |
|---|---|
| `.pkg-tree` | monospace 고정폭 도식(`white-space:pre` + `overflow-x:auto`). 디렉토리/패키지 트리와 **객체 간 호출 · 데이터 이동 도식**에 함께 쓴다 — 둘 다 들여쓰기와 괘선으로 층을 표현하므로 같은 스타일이면 충분하고, 도식용 class 를 따로 두면 같은 것에 이름이 둘이 된다 |
| `.diff-add` / `.diff-del` | 인라인 추가(green)/제거(red) 강조 |
| `.check-list` | 체크박스 목록(§11 마일스톤). `data-storage` 로 localStorage 동기화, 항목에 `data-key` |
| `.done` | 체크된 항목(취소선). JS 가 토글 |
| `.tip` | `data-tip` hover tooltip(시뮬레이터 외에도 사용 가능) |
| `.v2-badge` | 제목 옆 슬라이스 태그 배지 |
| `.shot` | **CP5 채증 스크린샷**(report 전용). `<figure class="shot">` + `<img>` + `<figcaption>` 한 벌. caption 은 **항목 식별자를 굵게 앞세우고**(`<b>A5</b> — …`) 무엇을 찍은 것인지 한 문장으로 잇는다. 기준 구현 `report/26-08-07_15-45-05_S10_report.html` 이 쓰던 것을 U3-5-d CP6 에서 정본으로 승격했다 |
| `dialog#shot-viewer` | `.shot` 이미지 크게 보기(report 전용 · 선택). 이미지 클릭 시 원본 크기로 열고 클릭 · Esc 로 닫는다. 색은 토큰만. S8-4 CP6 report 에서 처음 썼다 |

`.shot` 의 이미지는 **반드시 base64 data URI 로 인라인**한다(외부 참조 0 이 불가침). `<img style="max-width:100%; height:auto;">` 로 반응형을 지키고 `alt` 에 식별자와 요지를 적는다. 한 항목이 여러 컷을 가질 수 있으나 **같은 화면을 두 번 담지 않는다** — 중복은 증거를 늘리지 않으면서 파일만 키운다(U3-5-d 에서 실제로 한 장을 걷어냈다). 반대로 **평가 성격의 항목**(다른 항목의 컷을 다시 읽는 것)은 전용 컷을 두지 않아도 되며, 그 사유를 '미수행 항목과 사유' 절에 적는다.

표(`<table>`), 코드(`<code>`/`<pre>`), 콜아웃, 리스트는 일반 마크업을 쓰되 위 색 토큰과 반응형(3장)을 따른다.

## 3. 반응형 규약

데스크톱(>1024px)은 위 규약의 데스크톱 CSS 그대로 렌더한다. 아래는 태블릿/휴대폰 대응이며, **데스크톱 화면을 바꾸지 않는다**. 원칙: 작은 화면에서 sticky 헤더/ToC 가 표시 영역을 과하게 잠식하므로 비고정으로 전환하고, 넓은 요소(표, 스윔레인, 긴 코드)는 잘리지 않게 처리한다.

```css
/* 모바일 브라우저 텍스트 자동확대 차단 — 넓은 블록 안 글자가 제멋대로 부풀던 원인 */
html { -webkit-text-size-adjust:100%; -moz-text-size-adjust:100%; text-size-adjust:100%; }

@media (max-width:1024px){
  .layout { grid-template-columns:1fr; gap:16px; padding:16px; }
  header.page-header { position:static; }        /* 헤더 비고정 */
  nav.toc { position:static; max-height:none; top:auto; }
  .split-grid { grid-template-columns:1fr; }      /* 비교 그리드는 세로로 */
  /* 긴 인라인 코드가 뷰폭을 넘칠 때만 끊어 줄바꿈. break-word 는 min-content 불변이라 표/스윔레인 폭 계산에 영향 없음 */
  code { overflow-wrap:break-word; }
  /* .seq 스윔레인: 접지 말고 열 유지 + 가로 스크롤. repeat(N,1fr) 의 자동 최소폭(min-content)이 코드를 담으므로
     고정 최소폭을 주지 않는다(열 수 무관). 데스크톱 grid-template-columns 를 그대로 두고 overflow 만 얹는다. */
  .seq { overflow-x:auto; -webkit-overflow-scrolling:touch; }
}

@media (max-width:600px){
  body { font-size:13.5px; line-height:1.6; }
  header.page-header { padding:14px 16px; }
  header h1 { font-size:17px; line-height:1.35; }
  .meta { gap:6px 12px; font-size:11px; }
  .layout { padding:12px; gap:12px; }
  details.section > summary { padding:12px 14px; font-size:14px; }
  pre { font-size:11.5px; }
  .ux-toolbar .group-label { min-width:0; width:100%; margin:0 0 2px; }
  /* 표: 가로 스크롤 + 내용 많은 열 가독 폭 확보. 첫 컬럼(코드/id)은 줄바꿈 금지, 셀 code 축소 */
  table { display:block; overflow-x:auto; -webkit-overflow-scrolling:touch; }
  th, td { padding:9px 13px; }
  th:first-child, td:first-child { white-space:nowrap; }
  th:not(:first-child), td:not(:first-child) { min-width:15em; }
  table code { font-size:11px; padding:1px 4px; }
}
```

핵심 원리 3가지(반복되는 함정):

1. **고정 최소폭은 줄바꿈 안 하는 콘텐츠(인라인 코드)와 충돌한다.** grid/표 열에 `min-width:9em` 같은 고정값을 주면 그게 내용의 자연 최소폭(min-content)을 덮어써, 코드가 열보다 길면 옆으로 넘친다. 스윔레인은 고정 최소폭 없이 `repeat(N,1fr)`(자동 min-content)로 두어 코드를 담게 한다.
2. **긴 코드는 `overflow-wrap:break-word`** 로 뷰폭 넘칠 때만 끊는다. 평소엔 안 끊겨 코드 규약이 유지되고, min-content 를 바꾸지 않아 표/스윔레인 폭 계산에 영향이 없다.
3. **모바일 텍스트 자동확대**(`text-size-adjust`)를 끄지 않으면 넓은 표 블록 안 글자가 2배로 부푼다.

표의 chip 전용 열도 `min-width:15em` 이 되어 여백이 생기는 한계가 있다. 특정 표에서 문제되면 그 표에만 위치 기반으로 좁힌다(전역 규약은 내용 가독을 우선).

## 4. 인터랙션 규약 (JS)

말미 `<script>` 에 다음 4 동작을 포함한다(불가침). 셀렉터/훅은 고정.

1. **ToC 클릭 → 해당 섹션 open + smooth scroll**. sticky 헤더 높이만큼 offset(데스크톱 기준 −108). `nav.toc a[href^="#"]`.
2. **전부 펴기 / 접기**: `#btn-expand` / `#btn-collapse` 가 `details.section` 전체 open 토글.
3. **filter**: `#filter` 입력 → 텍스트 매칭 섹션만 표시(`.hidden` 토글 + 매칭 시 open).
4. **check-list localStorage**: `ul.check-list[data-storage]` 의 체크박스를 항목 `data-key` 로 localStorage 에 동기화, `.done` 토글.

🎬 데모 JS 는 슬라이스 도메인 규칙을 재현하되 `demoRender()` 를 파일 끝에서 1회 호출(불가침), 판정은 서버 도메인 메서드와 드리프트 0.

## 5. 난립 정리 (정본 vs 폐기)

기존 파일에서 규약 없이 갈라진 것을 정본으로 통일한다. **기존 파일은 프리즈(작성 시점 기록)라 소급 개명하지 않고, 앞으로 생성물에만 정본을 적용**한다.

| 갈래 | 정본 | 폐기/이유 |
|---|---|---|
| 데모 버튼 `.n-demo-btn`(템플릿, 106파일) vs `.ux-btn`(70파일) | **`.n-demo-btn`** | 템플릿과 데모 JS 가 생성하는 이름. `.ux-btn` 폐기 |
| 비교 카드 `.split-card`(90파일) vs `.compare-card`(16파일) | **`.split-card`** | 템플릿 정본. `.compare-card` 폐기 |
| 인라인 monospace `.mono`(51파일, 템플릿 미정의) | **`.mono` 를 정본으로 편입** | 실사용이 많고 용도 명확(짧은 monospace). 템플릿 CSS 에 추가 |
| 상태 `.ok`/`.pill`(템플릿 미정의) | `.chip`/`.badge` 로 흡수 | 중복. 신규 사용 금지 |
| `.dim`(회색 약화) | `.muted` 또는 `--text-muted` | 중복 |
| 스윔레인 확장 `.seq-call`/`.seq-lane-label`/`.ux-flow` | 표준 `.seq`/`.actor`/`.step` 로 표현 | 비표준 변형. 신규 사용 금지 |

**확정(D1, 2026-07-24)**: 데모 버튼 정본은 **`.n-demo-btn`**(템플릿과 데모 JS 가 생성하는 이름). `.ux-btn` 은 폐기하고 신규 생성물에서 쓰지 않는다.

## 6. 저작 체크리스트

plan/report html 제출 전 자체 점검:

- [ ] 자체 완결(외부 참조 0), 색은 `:root` 토큰만
- [ ] 골격(header/layout/toc/main) + 섹션 id 체계 + ToC + filter + 펴고접기
- [ ] plan 은 11섹션(화면 디자인 슬라이스는 7장 변형), 🎬 미리보기가 §2 직후이고 진짜 인터랙티브(상태 바꿔 규칙 체험)
- [ ] 디자인 객체는 2장 카탈로그의 정본만 사용(새 class 임의 생성 금지)
- [ ] 반응형 3장 블록 포함(헤더 비고정, 표/스윔레인/코드 처리, 자동확대 차단). 데스크톱 무변경
- [ ] JS 4 동작 + 데모 `demoRender()` 1회 호출, 데모 판정 = 서버 도메인 메서드
- [ ] 5장 폐기 class(`.ux-btn`/`.compare-card`/`.ok`/`.pill`/`.dim`/`.seq-*` 변형) 미사용

## 7. 디자인 plan 변형 (화면 디자인 슬라이스 전용)

2026-11-08 S22 우산 CP1 에서 정했다. 화면 디자인만 바꾸는 슬라이스(URL · 컨트롤러 · 도메인 · DB 변화 없음)는 1-2 의 11 섹션 대신 이 변형을 쓴다. 화면 변경은 글로 판정할 수 없으므로 **데모가 문서의 몸통**이고, 그 데모가 실제로 쓰는 CSS · JS 를 같은 파일의 설계 절로 보인다. 0장 기본 규약(자체 완결 · `:root` 토큰 · KST 파일명)과 3 · 4장(반응형 · JS 4 동작)은 그대로 적용한다. 파일명도 같다(`plan/YY-MM-DD_HH-MM-SS_<인벤토리코드>_plan.html`).

### 7-1. 섹션 체계

| id | 섹션 | 비고 |
|---|---|---|
| `s1` | 범위와 진단 | 이 슬라이스가 다루는 표식과 실측 수치, 요구 · 비목표를 한 절에 |
| `s2-preview` | 🎬 무대 | **문서의 몸통. 기본 펼침**. 7-2 의 무대 객체 |
| `s3` | CSS 설계 | 토큰 표(전 → 후) + 규칙 diff. 코드 블록은 무대가 적용하는 CSS 텍스트를 그대로 보인다(7-3) |
| `s4` | JS 설계 | 해당 시. 모듈 구성 · 호출 순서 · 실패 경로. 없으면 "JS 변경 없음" 한 줄 |
| `s5` | 적용 범위 | 파일과 건수, `DESIGN.md` 개정 문장 |
| `s6` | CP 와 검증 | Step ↔ CP 축약 표 + CP5 검증 항목 A · B · C |
| `s8` | 결정 | 채택 · 비채택 · 사유, Open Questions |
| `s10` | Critical Files | |
| `s11` | 다음 마일스톤 | `check-list` |

`s7`(예외) · `s9`(Verification)는 두지 않는다. 화면 디자인 슬라이스에 예외 변화가 없고, 검증은 `s6` 에 모은다.

### 7-2. 무대 객체 (카탈로그 추가)

| class / 요소 | 용도 |
|---|---|
| `.stage` | 무대 전체 컨테이너. 툴바 + 화면 + 판정 · 검사 패널 |
| `.stage-toolbar` | 화면 탭 · 전후 · 폭 · 시나리오 줄. 버튼은 `.seg` · `.n-demo-btn` 재사용 |
| `.stage-screen` | 스냅숏을 담는 상자. 안에 **Shadow DOM 호스트**를 두어 제품 CSS 와 문서 CSS 를 격리한다 |
| `.stage-wipe` | 전후 와이프 비교. 같은 스냅숏의 현행 · 제안 호스트 둘을 겹치고, 위쪽을 `clip-path: inset()` 으로 자른다. 손잡이는 `input[type=range]` |
| `.stage-inspect` | 요소 검사 패널. 무대 안 요소를 누르면 그 요소에 맞는 제안 CSS 규칙(`element.matches(selector)`)과 쓰인 토큰 이름, 주요 계산값을 보인다 |
| `.stage-panel` | 판정 패널. `.ux-caps` 규칙(`.t` 남음 · `.f` 해소)에 "결정으로 유지"(muted + 결정 번호)를 더한다 |
| `pre.css-src[data-from][data-file][data-select]` | CSS 설계 절의 코드 블록. 무대 CSS 저장소(7-3)의 텍스트에서 선택자 규칙을 뽑아 JS 가 채운다. 현행 · 제안을 나란히 둘 때는 `.split-grid` 의 `.split-card.before` · `.after` 안에 하나씩 |

격리 규칙:
- 스냅숏은 Shadow DOM 에 넣는다. `iframe srcdoc` 은 Notion 임베드의 sandbox 아래에서 부모 스크립트가 안을 조작하지 못할 수 있어 쓰지 않는다.
- 제품 CSS 의 `:root` 는 `:host` 로, `html` · `body` 선택자는 무대 루트 래퍼로 바꿔 shadow 안에 싣는다. 제품 CSS 의 media query 는 창 폭에 반응하므로, 폭 전환(1440 · 1024)은 호스트 폭을 고정하고 `transform: scale()` 로 맞춰 보인다.
- `@font-face` 는 shadow 안에서 등록되지 않는다. 글꼴은 문서 최상위 `<style>` 에 선언한다.

### 7-3. 드리프트 0 규칙

- 무대 CSS 는 제품 파일 단위로 한 번씩만 싣는다: `<script type="text/plain" class="stage-css" data-state="current|proposed" data-file="global/style.css">`(비 JS type 이라 실행되지 않는 텍스트 저장소). JS 가 파일마다 `CSSStyleSheet` 를 만들어 화면별 순서대로 shadow root 의 `adoptedStyleSheets` 에 붙인다. 설계 절의 코드 블록(`pre.css-src[data-from="proposed|current"][data-file][data-select]`)은 같은 텍스트에서 `data-select` 의 선택자 규칙만 뽑아 보일 뿐 따로 적지 않는다. 화면과 설계 절이 같은 텍스트를 쓰므로 어긋날 수 없다.
- 무대가 제품 CSS 를 shadow 안에 옮기며 생기는 차이(사이드바 `position: sticky` · `100vh`, `position: fixed` 패널)는 **무대 어댑터** 시트 하나(`.stage-adapter`, 두 상태 공통)로 맞추고, 어댑터 규칙은 설계 절에 별도로 밝힌다. 제안 CSS 자체에는 손대지 않는다.
- 제안 CSS 는 refine 실험 브랜치에서 실화면에 적용해 확인한 CSS 를 옮긴 것이다. 승격(CP2)은 이 CSS 를 원본 파일에 반영하는 일이다.
- 판정 패널의 판정식은 우산 plan 진단표의 판정식과 1:1 이다.

### 7-4. 스냅숏 · 글꼴 · 크기

- 스냅숏 = 샌드박스에서 렌더한 화면의 DOM. `<script>` 와 이벤트 속성을 지우고, 이미지는 data URI 로 넣는다. 샌드박스 데이터만 쓴다.
- 글꼴은 스냅숏에 쓰인 글자만 남긴 부분 글꼴(subset)을 base64 `woff2` 로 넣어 실제 모양을 보인다.
- 파일은 정본 하나이고 수 MB 까지 허용한다(Notion 임베드 1.8 MB 실증).
- 밀도 상한(태그 제외 본문 13,000 자 · `<table>` 7 개)은 서술 절에 적용한다. 스냅숏 DOM 과 CSS 텍스트는 세지 않는다.
- 원클릭 시나리오 4~6 개는 그대로 필수다(무대의 `.stage-toolbar` 에 둔다).

### 7-5. 체크리스트 추가

- [ ] 무대가 기본 펼침이고, 화면 탭 · 전후 전환 · 와이프 · 폭 전환 · 요소 검사 · 시나리오가 모두 동작
- [ ] 스냅숏이 Shadow DOM 안에 있고 문서 CSS 가 새지 않음(문서 쪽 `.n-*` 규칙 0)
- [ ] `pre.css-src` 가 무대 CSS 저장소 텍스트에서 뽑힘(직접 적은 CSS 코드 블록 0)
- [ ] 1360 · 820 · 390 폭에서 가로 넘침 0, 콘솔 오류 0
