# E3-2 BMC 설정 — 웹 API 실측 결론 브리핑

> **문서 종류**: 단계 착수 브리핑(ue 스트림 인수인계). 실측 원장 = Notion `E0-3 : BMC 내부 API 경로 확인 작업`, 절차 · 판정 = 앵커 저장소 `discussion/26-08-20_17-45-50_bmc-redfish-fieldwork-5-N-recipe.md`(정정판).
> **작성**: 2026-08-25 17:31 KST, 앵커 세션. HAR 원본(`~/Downloads/192.168.1.1_SETTING.har`, 비밀번호 마스킹)은 로컬 참조 전용 — 저장소 · Notion 업로드 금지.

## 1. 정본 — 사내 표준 BMC 세팅은 AMI 웹 API 4종이다

Redfish 가 아니다. 5호 브리핑의 N1~N3(Redfish NTP · eth0 · syslog)는 사용자 확정 없이 상정된 후보였고 2026-08-25 에 폐기됐다. 항목별 계약(경로 · 요청 바디 · 응답)은 E0-3 이 정본이며 재채집하지 않는다.

| 항목 | 경로 | readback |
|---|---|---|
| DateTime(NTP 서버 · 시간대) | `PUT /api/settings/date-time` | 같은 URL `GET` |
| Cold Redundant | `POST /api/cold_redundant-status` | 같은 URL `GET`(필드 `get_cold_redundant_enable`) · `GET /api/cold_redundant-psu_count` |
| Fan Profile | `POST /api/settings/fanprofile`(보드별 JSON) | `GET …/fanprofile/mode` · `GET …/fanprofile/collection` |
| Network Bond | `PUT /api/settings/network-bond` | 재접속 후 `GET` |

## 2. 실측 결론 (2026-08-25, MS74-HB0 · BMC 13.06.27)

1. **브라우저 밖 curl 성립** — DateTime · Cold Redundant · Fan Profile 3종 200 확인. Bond 는 물리 콘솔 확보 시 수행(미실측).
2. **인증 계약** — `POST /api/session`(form: username · password) 응답의 `CSRFToken` 을 이후 모든 요청의 `X-CSRFTOKEN` 헤더로 되돌린다. `X-Requested-With: XMLHttpRequest` 동반, 쓰기는 `Content-Type: application/json`. 세션 쿠키 유지.
3. **인증 실패 신호 = 응답 바디** `{ "cc": 7, "error": "Invalid Authentication" }` — HTTP 상태코드가 아니라 바디의 `cc` 를 판독해야 한다.
4. **세션 TTL 약 10분 내외**(유휴 기준 추정, 정확값 미측). 한 집행 안에서도 만료될 수 있으므로 코드는 TTL 에 기대지 말고 **`cc: 7` 수신 시 재로그인 후 1회 재시도**.
5. **DateTime 바디의 `timestamp`** — `ntp_auto_date: 0`(NTP 미사용)이면 이 값이 곧 BMC 시각. 브라우저도 GET 응답 전체를 되돌리며 `timestamp` 만 현재 epoch 초로 바꿔 PUT 한다 → 코드는 서버 시각을 실어 보낸다.
6. **Fan Profile 바디** — 단일행 JSON(약 2 KB, `strVersion` · `arrProfile[]` · `strMode`). Notion 에서 복사한 JSON 은 탭 · NBSP 오염으로 `{"error":"Invalid Data","code":1010}` 을 냈고, HAR 정본 바디로는 성립 → **보드별 JSON 을 검증된 자원 파일로 보관해 그대로 전송**한다.
7. **Bond 후 세션 삭제**는 BMC 가 끊는 것이 아니라 웹 UI 가 `DELETE /api/session` 을 명시 호출한 것 — 코드는 생략 가능. 단 Bond 재구성으로 일시 단절될 수 있어 재접속 대기는 둔다.

## 3. 구현 함의 (E3-2 plan 입력)

- **웹 API 클라이언트 1개**(global 인프라 후보): 세션 발급 · CSRF 헤더 부착 · `cc` 바디 판독 · 만료 시 재로그인 1회 재시도. Redfish 클라이언트(`RedfishClient`)와 별개 — 인증 모델이 다르다.
- 항목별 집행은 E0-3 계약을 데이터로 드는 enum/설정(보드별 Fan Profile 자원 포함)으로, 소비처에 항목 분기가 자라지 않게.
- 순서: DateTime → Cold Redundant → Fan Profile → **Bond 마지막**(단절 위험).
- 검증(readback)은 위 표의 경로로 항목마다.

## 4. 남은 실측

- W4 Bond 왕복(콘솔 확보 시) · W5 지속성(재시작 후 유지, `PreserveConfiguration` 보존 — 선택) · TTL 정확값(필요 시).
