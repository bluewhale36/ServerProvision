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
| `s5` | 10단계 + CP | |
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

CP 칩: `.cp1`(blue) `.cp2`(green) `.cp3`(yellow) `.cp4`(orange) `.cp5`(red).

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
- 레인 색: `.a1`(blue) `.a2`(green) `.a3`(orange). `.actor`/`.step` 에 같은 `aN` 을 붙여 색을 맞춘다. 빈 칸도 `<div class="step aN"></div>` 로 정렬을 유지한다.
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
| `.pkg-tree` | 디렉토리/패키지 트리(monospace, `white-space:pre`) |
| `.diff-add` / `.diff-del` | 인라인 추가(green)/제거(red) 강조 |
| `.check-list` | 체크박스 목록(§11 마일스톤). `data-storage` 로 localStorage 동기화, 항목에 `data-key` |
| `.done` | 체크된 항목(취소선). JS 가 토글 |
| `.tip` | `data-tip` hover tooltip(시뮬레이터 외에도 사용 가능) |
| `.v2-badge` | 제목 옆 슬라이스 태그 배지 |

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
- [ ] plan 은 11섹션, 🎬 미리보기가 §2 직후이고 진짜 인터랙티브(상태 바꿔 규칙 체험)
- [ ] 디자인 객체는 2장 카탈로그의 정본만 사용(새 class 임의 생성 금지)
- [ ] 반응형 3장 블록 포함(헤더 비고정, 표/스윔레인/코드 처리, 자동확대 차단). 데스크톱 무변경
- [ ] JS 4 동작 + 데모 `demoRender()` 1회 호출, 데모 판정 = 서버 도메인 메서드
- [ ] 5장 폐기 class(`.ux-btn`/`.compare-card`/`.ok`/`.pill`/`.dim`/`.seq-*` 변형) 미사용
