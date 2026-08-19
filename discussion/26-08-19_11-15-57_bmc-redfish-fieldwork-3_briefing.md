# BMC Redfish 실측 체크리스트 3호 — BIOS 설정 쓰기 실증과 잔여 채증

> **문서 종류**: 실측 세션 계획 · 기록 양식(참고 브리핑). 원장 = Notion `E0-4-3`.
> **작성**: 2026-08-19 11:15 KST.
> **주 목적**: **BIOS 설정을 Redfish 로 쓰는 왕복(PATCH → Reset → readback)의 실증** — E3-1 의 전제 복구가 걸려 있다.
> **연결**: 2호(`discussion/26-08-19_09-29-05_bmc-redfish-fieldwork-2_briefing.md`) · 원장 1 · 2호 = Notion `E0-4` · E0-1(2026-05~06 실측 — PATCH `Bios/SD` 동작 기록) · `docs/T3-checklist.md`.
> **장비 현행 상태**: MS04-CE0 · BIOS **F29**(2호에서 상향됨) · BMC 13.06.27 · 보드 시리얼 QG260700082.

---

## 0. 출발점 — 2호의 404 와 E0-1 의 대조가 만든 가설

2호 F2 에서 `GET /redfish/v1/Systems/Self/Bios/SD` 가 404 였고 Bios 응답에 `@Redfish.Settings` 링크도 없었다. 그런데 E0-1(2026-05~06) 실측 기록은 **`PATCH /redfish/v1/Systems/Self/Bios/SD` 를 `If-Match: *` 로 실제 수행해 동작**시켰다 — "Request Body 의 속성 값을 변경하도록 대기시킨다. 적용은 Reset 필요". E0-1 에 GET 확인 기록은 없다.

여기서 가설: **pending(SD) 객체는 비어 있으면 GET 404 를 내지만 PATCH 는 수락하는 구현**이다(AMI 계열에서 실제로 있는 거동). 이번 세션의 G 파트가 이 가설을 검증한다 — 참이면 E3-1 의 전제(BIOS 설정의 Redfish 적용)가 그대로 복구되고, 거짓이면(PATCH 도 404) 보드/펌웨어 차이를 의심해 벤더 문의에 합류시킨다.

## 1. 안전 수칙

1. G 파트는 **호스트 재부팅(ForceRestart)을 동반**한다 — 유휴 장비 전용.
2. 설정 실험 전 **현행 Attributes 전체 스냅샷을 먼저 채집**한다(G0 — BIOS 가 F29 로 바뀐 뒤의 기준값. 1호 채집본은 F27 시점이라 낡았다).
3. 실험 속성은 무해한 것 하나만: `SETUP004_BootupNumLockState`("On" ↔ "Off") 권장. 부팅 · 보안 관련 속성 금지.
4. 실험 후 **원복(G5)까지가 한 세트**다 — 원복 없이 세션을 끝내지 않는다.
5. `HPM_BIOS2` 실집행은 **벤더 답변 전 금지**(.hpm 이미지 미확보 · 용도 미확정).
6. 자격증명 미기재. 응답 원문의 원장은 Notion `E0-4-3`.

## 2. 위험 등급

1 · 2호와 동일 — R(읽기 전용) / S(가역 상태 변경 · 재부팅 포함) / X(집행).

## 3. Part G — 주 목적: BIOS 설정 쓰기 왕복 실증

| ID | 등급 | 확인 |
|---|---|---|
| G0 | R | `GET /redfish/v1/Systems/Self/Bios` — F29 기준 Attributes 전체 스냅샷(실험 전 기준값) |
| G1 | R | `GET /redfish/v1/Systems/Self/SD` — 1호가 발견한 System 수준 SettingsObject 의 정체 |
| G2 | S | **`PATCH /redfish/v1/Systems/Self/Bios/SD` 직접 시도** — GET 404 에 속지 않기(가설 검증의 본체) |
| G3 | R | PATCH 직후 `GET /redfish/v1/Systems/Self/Bios/SD` 재조회 — pending 이 생겼는지 |
| G4 | S | `ComputerSystem.Reset` `{"ResetType":"ForceRestart"}` → 부팅 후 `GET .../Bios` readback — 적용 확인 |
| G5 | S | 원복 PATCH + ForceRestart + readback — 장비 원상 복귀 |
| G6 | R | `GET /redfish/v1/Registries/BiosAttributeRegistry.json` — 속성 메타(타입 · 허용값) 전문 채집 |

**G2 상세** — 바디는 무해 속성 1개만:

```json
{ "Attributes": { "SETUP004_BootupNumLockState": "Off" } }
```

헤더는 E0-1 이 실증한 `If-Match: *` 로 먼저, 412 가 나면 fresh ETag(`GET /Bios` 응답 헤더) 로 재시도. **응답 코드가 이 세션의 핵심 채증값**이다 — 2xx 면 가설 참(경로 실재), 404 면 가설 기각(보드 · 펌웨어 차이로 벤더 문의 합류).

**G4 주의** — ForceRestart 후 호스트 POST 완료까지 수 분 대기 후 readback. 2호 E1 의 실패 모드(전원 명령 성공 · 상태 불변)가 재현되면 `PowerCycle` 폴백을 쓰고 그 사실을 채증한다.

**G6** — E3-1 과 BIOS 세팅 템플릿(biossetting)의 검증 룰 원천이 되는 파일이다. 응답이 크면 파일로 저장해 원장에 첨부.

## 4. Part H — 세션 인증 · Syslog 위치

| ID | 등급 | 확인 |
|---|---|---|
| H1 | S | 세션 발급 재시도 — **헤더 `Content-Type: application/json` 명시**(2호 415 의 원인). 성공 시 `X-Auth-Token` 으로 GET 1회 → 세션 DELETE 까지 |
| H2 | R | `GET /redfish/v1/SessionService` — 세션 타임아웃 · 상한 값 |
| H3 | R | `GET /redfish/v1/Managers/Self/NetworkProtocol` — 응답에 Syslog 항목이 있는지 직접 확인 |
| H4 | R | (H3 에 없을 때만) 웹 콘솔 Syslog 화면 XHR 캡처 |

**H4 방법** — 웹 콘솔 로그인 → **F12 개발자도구 → Network 탭을 먼저 열어 두고** → Syslog(원격 로그 서버) 설정 화면 진입 → 설정을 저장(값 변경 없이 재저장이면 무해) → Network 탭에 기록된 요청 URL 이 `/redfish/...` 인지 `/api/...` 인지 채증. E3-2 가 Syslog 자동화를 어느 API 로 계약할지의 근거다.

## 5. Part I — 펌웨어 트랙 잔여 · 상태 확인

| ID | 등급 | 확인 |
|---|---|---|
| I1 | R | PFR 사본 버전 raw 조회 — Check_PFR 문서의 ipmitool 두 줄(셀렉터 0x71 · 0x72). F29 상향이 PFR 사본에 반영됐는지 |
| I2 | R | `ipmitool -I lanplus -H <BMC_IP> -U <계정> -P <비밀번호> mc info` — 대역외 도구 가용성(복구 절차 `mc reset cold` 의 전제 확인) |
| I3 | S | (선택) 전원 On/Off 왕복 3회 — 2호 E1 실패 모드("IPMI 성공 · 전원 불변")의 재현 빈도 관측. E1.5 폴백 설계의 빈도 근거 |
| I4 | 보류 | `HPM_BIOS2` 실집행 — 벤더 답변 대기(안전 수칙 5) |

## 6. 벤더 문의 대기 항목 (실측과 별개 트랙)

① 사내 커스텀 BIOS 이미지를 RBU 형식으로 만들 수 있는가 — 도구 · 서명 요건 ② RBU flash 에서 커스텀 내용이 반영되지 않는 원인 ③ BIOS 2영역(`BIOS2`)의 원격 갱신 방법 — `HPM_BIOS2` 사용법과 `.hpm` 이미지 제공 여부 ④ BIOS 설정의 Redfish 쓰기 경로 공식 확인(G2 가 404 로 기각될 경우 합류).

## 7. 기록 양식

| ID | 결과(가능 / 불가 / 유지 / 다름 / 미수행) | 한 줄 요약 | 원문 위치 |
|---|---|---|---|
| G0 | | | |
| G1 | | | |
| G2 | | | |
| G3 | | | |
| G4 | | | |
| G5 | | | |
| G6 | | | |
| H1 | | | |
| H2 | | | |
| H3 | | | |
| H4 | | | |
| I1 | | | |
| I2 | | | |
| I3 | | | |

## 8. 우선순위와 반영처

- 순서는 **G → H → I**. G2 하나만으로도 E3-1 전제의 참 · 거짓이 판정된다. G 전체(재부팅 2회 포함)는 30분, H · I 는 각 10분 안쪽이다.
- 결과 반영처: ① E3-1 plan 의 전제 확정(G2~G5) ② `biossetting` · E3-1 의 검증 룰 원천(G6) ③ E1.5 계약(H1 · H2 · I3) ④ E3-2 계약 범위(H3 · H4) ⑤ `docs/T3-checklist.md` 갱신 ⑥ 원장 = Notion `E0-4-3`.
