# BMC 설정 쓰기 왕복 레시피 — 5호 W 계열 (E0-3 기준 정정판)

> **정정(2026-08-25)**: 종전 N1(Redfish NTP) · N2(Redfish eth0) · N3(syslog) 는 사내 표준 항목이 아니라 세션이 "쓰기 모델 시험용" 으로 상정한 후보였다(5호 §1 이 사용자 확정(O1) 전 후보 표였는데, 그 확정 없이 실행 절차로 굳힌 오류). **사내 표준 BMC 세팅의 정본은 Notion `E0-3 : BMC 내부 API 경로 확인 작업`** — AMI 웹 API 쓰기 4종(DateTime · Cold Redundant · Fan Profile · Network Bond)이며 요청 바디와 응답이 이미 채집돼 있다. 이 레시피는 그 4종을 **브라우저 밖(curl)에서 재연**하는 절차로 대체한다.
> **목적**: E3-2(BMC 설정)가 코드로 같은 요청을 보낼 수 있는지의 실증 — 세션 + CSRF 성립 · Bond 후 세션 삭제 거동 · readback 경로 · 지속성. 항목 자체의 계약은 E0-3 이 정본이라 여기서 다시 채집하지 않는다.
> **셸**: zsh. 값만 변수에 담고 옵션(-u · -H · -b)은 명령에 직접 쓴다 — zsh 는 따옴표 없는 변수를 단어 분할하지 않아 `AUTH='-u a:b'` 방식이 깨진다.
> **원장**: Notion `E0-3`(응답 원문 관례). 자격증명 · 토큰 실값은 문서에 남기지 않는다.

## 0. 공통 — 세션 발급 (E0-3 로그인 절)

```zsh
BMC=https://192.168.1.1
COOKIE=/tmp/bmc_cookies.txt
# ① 세션 발급 — 쿠키는 파일로, 응답의 CSRFToken 을 TOKEN 에 복사
curl -sk -c "$COOKIE" -d 'username=admin&password=<PW>' "$BMC/api/session"
TOKEN='<TOKEN>'   # 응답 CSRFToken (약 128자)
# 이후 모든 요청: 아래 헤더 3개 + 쿠키. X-CSRFTOKEN 이 없으면 401(E0-3 확정).
# curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" ...
```

각 항목은 **현행 채집 → 변경 → readback → 원복** 한 세트. readback 은 같은 URL 의 GET 을 먼저 시도한다(AMI 관례) — GET 이 없으면 PUT/POST 응답 바디로 대체하고 그 사실을 기록한다.

## 1. W1 — DateTime (NTP 서버 · 시간대) [S]

E0-3 계약: `PUT /api/settings/date-time`. 원본값 `primary_ntp: pool.ntp.org` · `secondary_ntp: time.nist.gov` · `timezone: Asia/Seoul`.

```zsh
# ② 현행
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" \
  "$BMC/api/settings/date-time" | tee /tmp/datetime_before.json
# ③ 변경 — secondary_ntp 만 바꾼다(E0-3 바디 전체를 실어 보내되 한 필드만 다르게). 나머지 필드는 ②의 값을 그대로.
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" -X PUT \
  -d @/tmp/datetime_changed.json "$BMC/api/settings/date-time"
# ④ readback → ⑤ 원복(②의 JSON 을 그대로 PUT)
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" \
  "$BMC/api/settings/date-time" | tee /tmp/datetime_after.json
```

> request body 에 timestamp 숫자가 들어간다. 한국 시간대로 맞추되 NTP 를 사용하지 않으며 Asia/Seoul timezone 으로 맞춰야 하나 timestamp 숫자가 매 순간 다르게 들어가야 한다. 직접 시도 불가.

**HAR 대조(2026-08-25)**: 브라우저도 정확히 그렇게 한다 — `GET` 응답 전체를 바디로 되돌리되 `timestamp` 만 **현재 epoch 초**로 바꿔 `PUT`(HAR: 1787645850, `ntp_auto_date: 0` 이라 이 값이 곧 BMC 시각). 즉 시도 불가가 아니라 "현재 시각을 넣어 보내는" 절차다. 코드도 같은 방식(NTP 미사용 표준이면 서버 시각을 epoch 로 실어 보냄).

```zsh
# ② 현행을 받아 timestamp 만 현재값으로 치환해 PUT — jq 필요
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "X-Requested-With: XMLHttpRequest" "$BMC/api/settings/date-time" \
  | jq --argjson now "$(date +%s)" '.timestamp = $now' > /tmp/datetime_put.json
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" -X PUT \
  --data-binary @/tmp/datetime_put.json "$BMC/api/settings/date-time"
```

## 2. W2 — Cold Redundant [S]

E0-3 계약: `POST /api/cold_redundant-status` `{"master_psu":0,"set_cold_redundant_enable":0}`.

```zsh
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" -X POST \
  -d '{"master_psu":0,"set_cold_redundant_enable":1}' "$BMC/api/cold_redundant-status"
# readback(GET 시도) → 원복: set_cold_redundant_enable:0
```

> 가능.

**HAR 대조**: readback 은 같은 URL 의 `GET` — 응답 필드명이 `get_cold_redundant_enable`(요청은 `set_` 접두). 브라우저는 `GET /api/cold_redundant-psu_count`(`{psu_count}`)도 함께 읽는다.
## 3. W3 — Fan Profile [S]

E0-3 계약: `POST /api/settings/fanprofile`, 보드별 JSON(MS03-CE0 · MS74-HB0 · MS04-CE0). 바디가 크므로 E0-3 페이지의 해당 보드 JSON 을 파일로 저장해 보낸다. 왕복은 `strMode` 를 `FAN_PROFILE` ↔ `default` 로.

```zsh
# E0-3 의 해당 보드 JSON → /tmp/fanprofile_<board>.json 저장 후
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" -X POST \
  -d @/tmp/fanprofile_MS74-HB0.json "$BMC/api/settings/fanprofile"
# readback(GET 시도) → 원복(strMode 되돌린 파일로 POST)
```

> 파일을 `-d` 인자로 넘겼으나 `{ "error": "Invalid Data", "code": 1010 }` response.

**HAR 대조(2026-08-25)**: 브라우저 요청은 `Content-Type: application/json` + 단일행 JSON 1,979자(`strVersion` · `arrProfile[2]` · `strMode`) — 레시피의 헤더 · 메서드와 같다. 따라서 1010 은 전송 방식이 아니라 **바디 내용** 문제일 가능성이 높다. 후보: ⓐ E0-3 페이지에서 복사한 JSON 에 Notion 렌더의 탭 · NBSP 가 섞임 ⓑ pretty 본을 복사하며 필드 누락. **판별법**: HAR 바디를 그대로 뽑아 둔 정본 `/tmp/fanprofile_from_har.json`(맥) 으로 `--data-binary @` 전송 — 성립하면 사용자 파일 오염, 그래도 1010 이면 환경 차이(그때 `-v` 로 요청 헤더 채집). `-d @file` 은 개행만 제거하므로 무해하나 `--data-binary` 가 정직하다.
**→ 2026-08-25 성립 확인**: HAR 정본 바디(`--data-binary @/tmp/fanprofile_from_har.json`)로 200 — 1010 의 원인은 파일 오염(Notion 복사)으로 확정. 코드는 E0-3 의 보드별 JSON 을 **자원 파일**(단일행 · 검증된 JSON)로 보관해 그대로 실어 보낸다.
**readback 경로 발견**: `GET /api/settings/fanprofile/mode` → `{"strMode"}` · `GET /api/settings/fanprofile/collection` → 프로파일 배열. 원복 판정은 `/mode` 로 충분.

## 4. W4 — Network Bond [S · 접속 상실 위험]

E0-3 계약: `PUT /api/settings/network-bond` `{"id":1,"bond_enable":1,"bond_mode":"active-backup","bond_ifc":"eth1","auto_configuration_enable":1}` — **응답 후 세션이 삭제된다**(E0-3 실측). **마지막에 수행**하고, 물리 콘솔(또는 로컬 KVM) 확보 상태에서만 한다.

```zsh
curl -sk -b "$COOKIE" -H "X-CSRFTOKEN: $TOKEN" -H "Content-Type: application/json" -H "X-Requested-With: XMLHttpRequest" -X PUT \
  -d '{"id":1,"bond_enable":1,"bond_mode":"active-backup","bond_ifc":"eth1","auto_configuration_enable":1}' "$BMC/api/settings/network-bond"
# → §0 으로 재로그인 후 readback(GET) → 원복(bond_enable:0) → 다시 세션 삭제되면 재로그인
```

**HAR 대조(2026-08-25)**: PUT 200 직후의 `DELETE /api/session` 은 **BMC 가 끊는 것이 아니라 웹 UI 가 명시적으로 로그아웃을 호출**한 것이다. 즉 "Bond 후 세션이 삭제된다" 는 E0-3 서술은 UI 동작이며, 코드는 삭제 호출을 생략할 수 있다(단 Bond 재구성으로 네트워크가 잠시 끊길 수 있어 재접속 대기는 필요 — 실측 항목 ⓓ 유지).

## 5. W5 — 지속성 (선택)

W1~W3 중 1개를 설정한 채 BMC 재시작 후 값 유지 확인. 여유 시 SimpleUpdate 의 `PreserveConfiguration` 이 이 설정들을 보존하는지(X 급 실집행 동반 — 선택).

## 6. 기록할 것 (E3-2 설계 입력)

- ⓐ 브라우저 밖 curl 로 4종이 성립하는가 (성립 = E3-2 웹 API 클라이언트 구현 가능) — **W1 · W2 · W3 성립 확인(2026-08-25)**, W4 는 콘솔 확보 시
- ⓑ `X-CSRFTOKEN` 없이 보냈을 때 거절 코드 — **실측(2026-08-25)**: `{ "cc": 7, "error": "Invalid Authentication" }`(HTTP 상태와 무관하게 바디의 `cc: 7` 이 인증 실패 신호 — 코드는 상태코드가 아니라 이 바디를 판독해야 한다)
- ⓒ 웹 세션 TTL — **실측(2026-08-25)**: 약 10분 내외로 만료(정확값 미측 · 유휴 기준 추정). Redfish 세션(30초)보다 길지만 한 집행 안에서도 만료될 수 있으므로 코드는 **`cc: 7` 수신 시 재로그인 후 1회 재시도**하는 방식이 정직하다(TTL 값에 기대지 않는다)
- ⓓ Bond 후 세션 삭제 → 재로그인 필요 여부와 재접속 소요
- ⓔ 항목별 readback 경로(GET 가능 여부)

## 7. 보안

자격증명 · CSRF 토큰 실값은 원장(Notion) · 저장소에 남기지 않는다. HAR 원본 2건도 평문 비밀번호 포함이라 업로드 금지.
