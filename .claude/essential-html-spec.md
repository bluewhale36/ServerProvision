# essential report html 저작 규약

essential 은 한 슬라이스의 **구조 · 책임 · 흐름**을 코드를 읽지 않고도 파악하게 하는 문서다. 정규 CP6 report 가 "무엇을 검증했고 어떤 결과였나" 를 담는다면, essential 은 "무엇을 만들었고 · 어떤 구조이며 · 각 클래스가 무엇을 맡고 · 요청 한 건이 어떻게 흐르는가" 만 담는다. 검증 방식 · 결과 · 다음 작업은 쓰지 않는다. 기준 구현(승인본) = `report/26-09-14_23-08-12_S19-1_essential.html`. 후속 선례 = `report/26-09-15_09-35-16_S19-2_essential.html`, R16 워크트리의 `report/26-09-17_13-08-22_R16_essential.html`.

## 0. 언제 · 어디에
- CP6 정규 report 뒤에 사용자가 지시할 때 낸다(구조가 큰 슬라이스: 보안 체인 · 인프라 배선 · 실행 엔진 phase 처럼 클래스가 여럿 얽힐 때). 정규 report 를 대체하지 않는다.
- 파일 `report/YY-MM-DD_HH-MM-SS_<인벤토리코드>_essential.html`(KST). 자체 완결 단일 파일(외부 CSS · JS · 폰트 · 이미지 참조 0). 이미지(png)를 넣지 않는다 — 도식은 인라인 SVG 다.
- 저작 분업은 plan · report 와 같다: 내용(절 본문 · 도식의 레인 · 메시지 · 트리 행 · 책임 표 · 계약)은 **세션이 브리프로 쓰고**, html 조립 · SVG 생성 · 렌더 확인은 Opus 하위 에이전트에 위임하며 산출물은 세션이 검수한다.

## 1. 골격
- `<header class="page-header">`(sticky) — `<h1>` 은 `<코드> Essential — <주제>` + `<span class="v2-badge">구조 · 책임 · 흐름</span>`. `.meta` 에 5 항목: **슬라이스**(코드 · 종류) · **상위**(우산 단계 · 없으면 대체 관계) · **대상**("무엇을 만들었고 · 어떤 구조이며 · 각 클래스가 무엇을 맡고 · 요청 한 건이 어떻게 흐르는가") · **제외**("검증 방식 · 결과 · 다음 작업") · **생성**(YYYY-MM-DD KST). 전제가 있으면 **전제** 항목을 더한다(예: "S19-1 essential 위에 얹는다").
- `<div class="layout">` = `nav.toc`(좌 · sticky · `ol#toc-list` · `.toc-actions` 의 전부 펴기/접기 버튼) + `<main>`(맨 위 `input.filter-box#filter`).
- 절은 `<details class="section" id="sN" open>` + `<summary>` + `.body`. 소절은 `<h4>` 로 `N-M. 제목`. 절 5 개, id · 순서 고정:
  - `s1` **§1. 범위 — 무엇을 만들었나**: 종전 → 이번 · 만든 것(카드 3~5 · `.card-grid`) · 바꾸지 않은 것 · 필요하면 변경 전후(`.split-grid` + `.before`/`.after` · `.diff-add`/`.diff-del`).
  - `s2` **§2. 구조**: 2-1 부품 배치(블록 흐름 도식) · 2-2 패키지 트리(**텍스트 트리**) · 2-3 타입 사슬(가로 흐름 도식) · 2-4 그 슬라이스의 정적 산출물(조각 · 스크립트 · 골든)을 `<pre>` 로.
  - `s3` **§3. 데이터 흐름 — 요청 한 건**: 정상 시퀀스 1~2 · 거절/실패 분기 1(시퀀스 또는 분기 흐름도) · 필요하면 외부 상대(게스트 · 데몬)와의 시퀀스. 첫 줄에 범례: "실선 = 호출 · 값, 점선 = 반환, 고리 = 자기 처리. 번호 = 시간 순. secret 은 *** 로".
  - `s4` **§4. 클래스별 책임 — 맡는 것 · 맡지 않는 것**: 묶음별 `<h4>` + 표 4 열(클래스 · 변경(신규/수정/이름 변경) · 맡는 것 · 맡지 않는 것).
  - `s5` **§5. 계약**: 경로 · credential · 응답 · 설정값 · 파일 · 명령 · DDL 처럼 바깥과 약속한 것만 표 · `<pre>` 로.
- 색은 `:root` 토큰만(`--bg --card --text --text-muted --border --border-soft --blue(-bg) --orange(-bg) --yellow(-bg) --green(-bg) --red(-bg) --purple(-bg) --mono`). 반응형 2 단계(`@media (max-width:1024px)` 에서 레이아웃 1열 · 헤더 · 목차 비고정, `600px` 에서 표 · `pre` · 도식 가로 스크롤). `.split-grid` 는 `minmax(0,1fr)` 로 잡아 긴 식별자가 옆 칸을 밀지 않게 한다.

## 2. 도식 — 그래픽은 흐름에만
- **패키지 · 디렉토리 트리는 `pre.pkg-tree` 텍스트 트리**(├── └── · 이름 뒤에 맡는 일 한 줄). 트리형 그래픽(`tree_svg`)은 **코드의 흐름을 트리로 보일 때만**(필터 체인 · 호출 사슬 · 분기 묶음) 쓴다(2026-09-17 지시).
- 도식은 `<figure class="dg">` + 인라인 `<svg>` + `<figcaption>`(무엇이 무엇을 뜻하는지 한두 문장). 생성기는 `.claude/tools/dgkit.py`(정본) — `node(x,y,w,h,lines,kind)` · `edge(prefix,pts,label,kind=call|ret)` · `text_block` · `seq_svg(prefix, lanes, msgs)`(lanes `[(kind,[줄])]` · msgs `(kind, from, to, [줄])` kind ∈ call · ret · self) · `tree_svg(prefix, rows)`(rows `(depth, kind, name, desc_lines, group_id)`) · `diagram(prefix,W,H,body)` · `wrap(svg, caption)`. 폭은 `MAXW` 1170 안에서 `lane_w` 를 줄여 맞추고 긴 식별자는 두 줄로 나눈다.
- 상자 종류(kind → class) 와 뜻: `guest`(외부 요청자 · 파랑) · `chain`(필터 · 데몬 · 경계 · 초록) · `part`(부품 · 서비스 · 주황) · `ctl`(컨트롤러 · 파랑) · `data`(엔티티 · 설정 · 보라) · `value`(값 · 레코드 · 회색 테두리) · `rule`(인가 규칙 · 초록 테두리) · `reject`(거절 · 예외 · 빨강) · `decision`(판정 · 노랑) · `web`(화면 · 배경색). 묶음 테두리 `.stage`(실선) · `.stage2`(점선 = 공유 · 전용 아님을 뜻함). 화살표 `.call`(실선 · 채운 촉) · `.ret`(점선 · 빈 촉).
- 렌더 확인: Playwright(headless Chrome)로 1360 · 820 · 390 폭에서 문서 가로 overflow 0 · 도식 잘림 · 겹침 없음을 본다. 4 열 이상 스윔레인은 `repeat(n,1fr)` 대신 `minmax(0,1fr)` + `overflow-wrap:anywhere`.

## 3. JS 4 동작(승인본과 동일 · 추가 금지)
① 목차 클릭 = 해당 절 `open` 뒤 부드러운 스크롤(헤더 높이 108 보정) ② 전부 펴기/접기 ③ 필터 입력 = 절 본문 텍스트 검색 · 일치 절만 열고 나머지 `.hidden` ④ `ul.check-list[data-storage]` 체크 상태 localStorage 보존(essential 에는 보통 없음).

## 4. 문장 규율
- 프로그램 객체 이름은 약어 없이 전체 이름 · 영문 용어(credential · principal · proxy · fragment · ETag …)는 영문 그대로 · '·' 앞뒤 공백 한 칸 · 표에 산문을 밀어넣지 않는다(짧은 헤더 3~4 열 · 근거는 표 밖) · 비밀값은 `***`.
- 사실은 코드에서 확인한 것만 적는다(에이전트가 브리프와 코드가 어긋난 곳을 찾으면 세션이 판정한다).

## 5. 저작 체크리스트
- [ ] 헤더 메타 5(+전제) · 절 5 id 순서 · 목차 5 링크 1:1
- [ ] §2 패키지 트리는 텍스트 · 그래픽은 흐름에만 · 도식마다 figcaption
- [ ] §3 범례 한 줄 · 시퀀스 번호 = 시간 순 · 거절 분기 1 이상
- [ ] §4 표 4 열 · 묶음별 h4 · "맡지 않는 것" 비지 않음
- [ ] 외부 참조 0 · 이미지 0 · :root 토큰만 · 반응형 2 단계 · JS 4 동작 · 검증 · 결과 · 다음 작업 없음
- [ ] Playwright 3 폭 렌더 확인 · 세션 검수(브리프 대비 누락 · 코드 대조 정정 목록)
