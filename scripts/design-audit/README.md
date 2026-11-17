# design-audit

렌더된 관리 화면을 브라우저로 열어 디자인 규약 위반을 센다. 마크업만 보고 판정할 수 있는 규칙은 `DesignConventionTest`(전체 테스트에 포함)가 맡고, 여기는 렌더해야 보이는 것을 맡는다.

| 도구 | 보는 것 | 규약 |
|---|---|---|
| `copy-scan.mjs` | 화면 글자와 title, placeholder, aria-label 의 대시, 가운뎃점, '~음/~함/~됨' 꼴, 해요체, '축' | DESIGN.md 7절 표기 |
| `line-scan.mjs` | 2.5px 안에 포갠 선, 사이에 글자 없이 나란한 두 선 | DESIGN.md 5절 구분선 |
| `overflow-scan.mjs` | 폭마다(기본 1440, 1024) 페이지 가로 넘침과 칸 밖으로 나간 표 칸, 배지, 버튼 | DESIGN.md 3절, 4절 |

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

`urls.txt` 는 한 줄에 경로 하나다(`#` 으로 시작하면 주석). `urls.sample.txt` 는 2026-11-16 디자인 샌드박스 데이터 기준의 92 화면이다. 자원 id 는 샌드박스마다 다르므로 자기 데이터에 맞게 고친다. 바꾼 화면만 넣어 돌려도 된다. 두 도구 모두 GET 외 요청을 막으므로 화면 상태를 바꾸지 않는다.

## 결과 읽기

- `copy-scan` 은 사용자 입력 데이터(정의서 이름, 메모)도 잡는다. 화면 문구인지 사람이 가른다.
- `line-scan` 의 `겹침` 은 거의 항상 결함이다. `빈 구분선` 은 판정이 필요하다. 탭 줄 아래에 판이 떨어져 서는 것, 묶음 머리 선 아래에 상자 항목이 줄지어 서는 것은 정상 간격으로 본다(2026-11-16 판정).
- 결과는 `out/` 에 쌓인다(git 제외).
