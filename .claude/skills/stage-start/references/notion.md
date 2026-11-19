# Notion 운영 정보

2026-11-17 에 DB 를 직접 조회해 적었다. 값이 맞지 않으면(템플릿 이름 변경, 속성 추가 등) DB 를 다시 fetch 하고 이 파일을 고친다. 단계 페이지 골격과 4 항목 규약의 정본은 CLAUDE.md 'Notion 작업 규약' 이다. 여기에는 그 규약을 실행하는 데 필요한 id 와 속성 이름만 둔다.

## 개발 상세 DB (단계 페이지)

- 데이터베이스: `https://app.notion.com/p/356ef0e868f980528012f3fedb42a68f`
- data source: `collection://356ef0e8-68f9-80e0-aa87-000b0b25dae9`
- 템플릿(`create-pages` 의 `template_id`):
  - 일반 단계 `(단계) : (개요)` = `3a7ef0e8-68f9-80cc-a6f5-e788d667efe1`
  - OPS 단계 `OPS-n : (개요)` = `3b0ef0e8-68f9-80e6-a151-dea78a5960fc`

| 속성 | 형식 | 쓰는 법 |
|---|---|---|
| `이름` | 제목 | `<코드> : <주제>`. 부제나 괄호 상세는 넣지 않는다 |
| `설명` | 텍스트 | 2~4 문장. scope 핵심 · 방식 · 산출물 |
| `상태` | status | 시작 경계에서 '진행 중'. 선택지: 시작 전 · 진행 중 · CP1 완료 ~ CP6 완료 · 완료 · 취소 |
| `시작 일자` | date | 오늘(KST). `date:시작 일자:start` 로 넣는다 |
| `개발 단계` | relation(1) | **항상 채운다.** 현재 값 = 5차 개발 `https://app.notion.com/p/3dfef0e868f98034bfc8e114dd46537a`. 사용자가 바꾸라고 할 때까지 이 값이다 |
| `상위 항목` | relation(1) | 하위 단계면 **직속 부모**. 계열 최상위 단계(예: U7)는 비운다 |

### 만드는 순서 (tabs 때문에 두 번에 나눈다)

1. `create-pages`: parent = data source, `template_id` 와 속성만 넘긴다. tabs 블록은 API 로 직접 만들 수 없어서 템플릿으로 만든다. `template_id` 를 주면 같은 호출에 `content` 를 줄 수 없다.
2. `update-page`: tabs 아래에 본문을 붙인다(4 항목, 토론이면 `## 토론` 골격까지). 요약 탭 한 줄도 이때 넣는다. 요약 탭 쓰는 법은 아래 '탭 안에 쓰는 법' 을 따른다.

### 탭 안에 쓰는 법

`<tab>` 다음 첫 줄은 **탭 제목**이다. 제목 줄에는 `요약` · `plan` · `report` 만 두고, 내용은 빈 줄 하나 뒤 다음 줄부터 쓴다. 그래야 내용이 탭 안의 블록이 된다.

```
<tab>
요약

수행 내용 핵심 한 줄.
</tab>
```

- `요약<br />내용` 처럼 제목과 같은 줄에 쓰면 내용이 **탭 제목**으로 들어간다. 이 실수가 여러 페이지에서 반복됐다.
- plan, report 탭의 embed 도 같은 원리다. 제목 줄에 붙이면 `\<embed` 로 이스케이프된다.
- 쓰고 나서 fetch 해 `<tab>` 다음 줄이 제목 낱말 하나뿐인지 확인한다.
- tabs 를 고칠 때는 언제나 `<tabs>` 부터 `</tabs>` 까지 전체를 old_str 로 잡는다(아래 '탭 임베드' 4 단계와 같다).
3. 다시 fetch 해서 tabs 가 남아 있는지, 부모의 `하위 항목` 에 새 페이지가 잡혔는지 확인한다.

### 부모 상태

자식이 '진행 중'이 되면 부모도 다시 판정한다(notion-sync 에이전트 정의의 계층 규약과 같다). 자식이 모두 '시작 전'이면 부모도 '시작 전', 모두 '완료'면 '완료', 그 밖에는 '진행 중'이다. 부모가 '시작 전'이었다면 '진행 중'으로 바꾸고, 시작 일자가 비어 있으면 오늘로 채운다.

## plan, report 탭 임베드

단계 페이지의 plan 탭과 report 탭에 html 을 붙이는 절차다. 언제 하는지는 CLAUDE.md 'plan / report html 규약' 이 정한다(plan 은 CP1 제출 때, report 는 CP6 제출 때, 고친 판을 낼 때마다 교체). 메인 세션이 직접 한다. notion-sync 에이전트에는 첨부 도구가 없다.

1. `notion-create-file-upload` 에 `filename` 만 넘긴다(파일 이름 그대로). 반환의 `upload_url` 과 `upload_headers.authorization` 을 받는다. URL 은 짧은 시간만 산다.
2. 스크립트로 보낸다. 커밋 전이어도 되고, 파일 크기가 모델 출력과 상관없다.
   ```bash
   bash .claude/skills/stage-start/scripts/notion-upload.sh '<upload_url>' '<authorization>' plan/<파일>.html
   ```
   `[STOP]` 이면 embed 하지 않고 사유를 보고한다. `[OK]` 면 마지막 줄 `EMBED …` 의 태그를 쓴다.
3. 페이지를 fetch 해서 지금 tabs 블록을 읽는다.
4. `update-page` 의 `update_content` 로 **tabs 블록 전체**(`<tabs>` 부터 `</tabs>` 까지)를 old_str 로 잡아 통째로 바꾼다. 탭 하나의 조각만 잡으면 tabs 가 탭 하나로 무너진다.
   - embed 태그는 탭 제목 다음 **별도 줄**에 둔다. `plan<br /><embed …>` 처럼 같은 줄에 붙이면 `\<embed` 로 이스케이프되어 글자로 남는다.
   - 고친 판으로 교체할 때는 그 탭의 옛 embed 줄을 새 태그로 바꾼다. 다른 탭의 내용은 fetch 한 그대로 둔다.
   ```
   <tab>
   report
   <embed source="file-upload://<id>" />
   </tab>
   ```
5. 다시 fetch 해서 탭이 셋 그대로이고 embed 가 `notion-file-block://` 로 바뀌었는지 확인한다.

업로드 응답이 403 이면 커밋과 push 를 마친 뒤 `notion-create-attachment` 의 `source_url` 에 커밋 SHA 고정 raw URL(`https://raw.githubusercontent.com/bluewhale36/ServerProvision/<SHA>/<경로>`)을 넘긴다. 커밋 지시가 없으면 사용자에게 알리고 기다린다.

## 패치 내역 DB

- data source: `collection://41aef0e8-68f9-8063-9ec7-000b5e665517`

| 속성 | 쓰는 법 |
|---|---|
| `관련 개발 단계` | relation(여럿). 새 단계 페이지를 **더한다**(기존 값을 지우지 않는다) |
| `패치 상태` | 착수 때 '진행 중'. 선택지: 시작 전 · 진행 중 · 완료 · 취소 · 통합 |
| `패치 시작 일자` | 비어 있을 때만 오늘(KST)로 채운다 |
| `패치 종료 일자` | 착수 때는 건드리지 않는다(cp7-ship 이 채운다) |

단계 페이지의 ① scope 요약에는 그 패치 항목을 `<mention url=…>` 로 멘션한다.
