# E0-3 Fan Profile 보드별 JSON (단일행 · 검증됨)

- `fanprofile_MS74-HB0.json` — **HAR 정본**(브라우저가 실제 전송한 바디, 2026-08-25 curl 로 200 실증).
- `fanprofile_MS03-CE0.json` · `fanprofile_MS04-CE0.json` — Notion E0-3 압축본을 파싱 · 재직렬화(구조는 MS74 정본과 동일 검증, **실기 전송은 미검증**).
- 모두 `strMode: FAN_PROFILE` 상태의 전문. 왕복 원복은 `strMode` 를 `default` 로.
- 출처: Notion `E0-3 : BMC 내부 API 경로 확인 작업`. 민감정보 없음.
