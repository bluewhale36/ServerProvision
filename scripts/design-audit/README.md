# design-audit

렌더된 관리 화면을 브라우저로 열어 디자인 규약 위반을 센다. 마크업만 보고 판정할 수 있는 규칙은 `DesignConventionTest`(전체 테스트에 포함)가 맡고, 여기는 렌더해야 보이는 것을 맡는다.

| 도구 | 보는 것 | 규약 |
|---|---|---|
| `copy-scan.mjs` | 화면 글자와 title, placeholder, aria-label 의 대시, 가운뎃점, '~음/~함/~됨' 꼴, 해요체, '축' | DESIGN.md 7절 표기 |
| `line-scan.mjs` | 2.5px 안에 포갠 선, 사이에 글자 없이 나란한 두 선 | DESIGN.md 5절 구분선 |
| `overflow-scan.mjs` | 폭마다(기본 1440, 1024) 페이지 가로 넘침과 칸 밖으로 나간 표 칸, 배지, 버튼 | DESIGN.md 3절, 4절 |
| `snippet-check.mjs` | 앱 없이 마크업 조각과 제안 CSS 를 앱 시트 위에 렌더해 폭마다 스크린샷과 위 세 검사를 한 번에 | 위와 같음 |

세 화면 점검 도구와 `snippet-check` 는 검사 함수(`lib.mjs` 의 `copyLinesInPage`, `lineHitsInPage`, `overflowInPage`, `BAD`)를 함께 쓴다. 규칙을 고치면 한 곳만 고친다. 점검 순서는 `.claude/skills/design-review` 스킬이 정한다.

## 준비

```bash
cd scripts/design-audit
npm install            # playwright-core 만 받는다. 브라우저는 설치된 Chrome 을 쓴다
```

## 실행

샌드박스 앱이 떠 있어야 한다. 계정은 환경변수로 넘긴다(저장소에 적지 않는다).

```bash
SPV_BASE=http://localhost:7797 SPV_USER=<계정> SPV_PASS=<비밀번호> node copy-scan.mjs urls.txt out/copy.json
SPV_BASE=http://localhost:7797 SPV_USER=<계정> SPV_PASS=<비밀번호> node line-scan.mjs urls.txt out/lines.json
SPV_BASE=http://localhost:7797 SPV_USER=<계정> SPV_PASS=<비밀번호> node overflow-scan.mjs urls.txt out/overflow.json 1440,1024
```

스니펫 점검은 샌드박스와 계정이 필요 없다. 조각 파일에는 body 안 마크업만 쓴다.

```bash
node snippet-check.mjs 조각.html out/snippet 1440,1024,390 --sheets css/miller.css --css 제안.css
```

`--sheets` 는 기본 시트(reset, style, form-validation, table-list) 뒤에 얹을 앱 시트(static 기준 경로), `--css` 는 아직 앱에 없는 제안 CSS 다. 결과는 폭마다 `w<폭>.png` 와 `report.json` 이다. 스크린샷은 눈으로도 본다.

`urls.txt` 는 한 줄에 경로 하나다(`#` 으로 시작하면 주석). `urls.sample.txt` 는 2026-11-16 디자인 샌드박스 데이터 기준의 92 화면이다. 자원 id 는 샌드박스마다 다르므로 자기 데이터에 맞게 고친다. 바꾼 화면만 넣어 돌려도 된다. 두 도구 모두 GET 외 요청을 막으므로 화면 상태를 바꾸지 않는다.

## 결과 읽기

- `copy-scan` 은 사용자 입력 데이터(정의서 이름, 메모)도 잡는다. 화면 문구인지 사람이 가른다.
- `line-scan` 의 `겹침` 은 거의 항상 결함이다. `빈 구분선` 은 판정이 필요하다. 탭 줄 아래에 판이 떨어져 서는 것, 묶음 머리 선 아래에 상자 항목이 줄지어 서는 것은 정상 간격으로 본다(2026-11-16 판정).
- 결과는 `out/` 에 쌓인다(git 제외).
