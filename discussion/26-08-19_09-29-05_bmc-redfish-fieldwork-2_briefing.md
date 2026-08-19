# BMC Redfish 실측 체크리스트 2호 — 다음 세션

> **문서 종류**: 실측 세션 계획 · 기록 양식(참고 브리핑). 1호(2026-08-18 수행, 원장 = Notion `E0-4`)의 잔여와 새 질문을 다음 출근 실측으로 고정한다.
> **작성**: 2026-08-19 09:29 KST.
> **주 목적**: **BIOS 를 Redfish SimpleUpdate 로 flash 할 수 있는가** — 답에 따라 E2-2 의 설계가 갈린다(가능 = E2-3 과 같은 Redfish 채널로 통합, 가상 USB · UEFI Shell 인프라 불요 / 불가능 = 가상 USB 설계를 확신으로 확정).
> **연결**: `discussion/26-08-15_15-48-41_bmc-redfish-fieldwork-checklist_briefing.md`(1호) · Notion `E0-4`(원장 — 이번 결과도 같은 페이지에 이어 적는다) · `docs/T3-checklist.md`(게이트 반영처) · `discussion/26-08-01_22-33-04_E2-substage-restructure_discussion.md`(현행 E2-2 = 가상 USB 확정의 원 결정).

---

## 0. 출발점 — 1호가 연 것과 이번 세션의 질문

1호 실측으로 E2-3 착수 게이트 ①(OEM 계약 — 모양 갱신 확인) · ②(전원 OFF 수락)가 통과했고, BMC SimpleUpdate 가 다운로드 → 검증 → flash → 완료로 완주했다(`.ima_enc` · HTTP pull · 약 7분 37초). FirmwareInventory 에 `BIOS` · `BIOS2` 멤버가 실재함도 확인됐다.

이번 세션의 중심 질문은 하나다 — **같은 채널로 BIOS 도 되는가.** 현행 E2-2 확정안(가상 USB + UEFI Shell)은 이미지 빌더 · 부팅 루프 · 재부팅 후 간접 검증이라는 무거운 인프라를 전제한다. BIOS 가 SimpleUpdate 로 된다면 그 인프라가 통째로 불필요해지고, 안된다고 확인돼도 가상 USB 설계를 흔들림 없이 진행할 수 있다. 어느 쪽이든 E2-2 plan 의 결정적 입력이다.

## 1. 안전 수칙 (시작 전 확인 — 1호보다 엄격하다)

1. **BIOS flash 는 BMC flash 보다 벽돌 파장이 크다.** 유휴 장비 전용이며, 실집행(D3)은 **현재와 동일한 버전(F27) 재적용만** 한다. 신버전 실험 금지.
2. **D 계열은 순서 강제** — D1(ActionInfo) · D2(인벤토리)가 선행이고, **D3 실집행은 D1 응답에서 BIOS 대상 지정 방법이 확인될 때만** 진행한다. 확인되지 않으면 D3 을 건너뛰고 D5(웹 콘솔 대안 경로 채증)로 간다.
3. flash Task 진행 중 전원 조작 · WebGUI 접속 금지. Task 종결까지 대기 후 다음 조작.
4. 실패가 나면 무리한 재시도 금지 — 상태(Task 메시지 · FirmwareInventory)를 채증하고 중단한다. 듀얼 BIOS 거동(D4)을 모르는 상태에서의 반복 시도가 가장 위험하다.
5. 자격증명 · 토큰은 이 문서에 적지 않는다. 응답 원문의 원장은 Notion `E0-4` 다.

## 2. 위험 등급

1호와 동일 — R(읽기 전용) / S(가역 상태 변경) / X(집행).

## 3. Part D — 주 목적: BIOS 의 Redfish flash 가능 여부

| ID | 등급 | 확인 |
|---|---|---|
| D1 | R | `GET /redfish/v1/UpdateService/SimpleUpdateActionInfo` — 허용 파라미터 전문 채집 |
| D2 | R | `GET /redfish/v1/UpdateService/FirmwareInventory/BIOS` 와 `.../BIOS2` — 버전 표기 · 속성 |
| D3 | X | (D1 통과 시) BIOS 동일 버전 재적용 SimpleUpdate 실집행 |
| D4 | R | D3 후 버전 재확인 — 듀얼 BIOS 의 어느 쪽이 바뀌는지 |
| D5 | R | 웹 콘솔 BIOS 업데이트 화면 채증 — D1 이 막혔을 때의 대안 경로 |

**D1** — 이 세션의 관문. 1호 A2 응답이 `@Redfish.ActionInfo: /redfish/v1/UpdateService/SimpleUpdateActionInfo` 를 가리켰으나 내용은 미조회다. 응답에서 볼 것: `UpdateComponent` 파라미터(OEM 확장)의 존재와 허용값에 BIOS 가 있는지, 또는 표준 `Targets` 파라미터 방식인지. **BIOS 를 지정하는 방법이 응답에서 확인되면 D3 진행, 아니면 D3 생략.**

**D2** — 두 멤버의 `Version` 문자열 형식(F27 예상)과 속성 전량. E2-1 의 FirmwareVersion Value Object 비교 규약의 BIOS 쪽 입력이다.

**D3** — BIOS F27 배포 패키지의 BIOS 파일(조사 기준 `.RBU` — **실물 확장자를 패키지에서 확인**. 13.06.27 은 BMC 펌웨어라 BIOS 파일이 들어 있지 않다 — 2026-08-19 정정)을 1호와 같은 방식(PC 의 python http.server)으로 서빙하고, D1 이 확인해 준 파라미터로 SimpleUpdate 를 던진다. Task 메시지 시퀀스를 처음부터 끝까지 채증한다. **관찰 포인트**: BIOS 는 BMC 와 달리 호스트 재부팅이 있어야 반영될 수 있다 — Task 가 어디까지 진행되고 무엇을 요구하는지(즉시 flash 인지, 다음 부팅 대기인지)가 E2-2 흐름 설계의 입력이다.

**D4** — `GET /redfish/v1/Systems/Self` 의 `BiosVersion` 과 FirmwareInventory 재조회. `BIOS` / `BIOS2` 중 어느 슬롯이 갱신되는지로 듀얼 BIOS 거동을 확인한다.

**D5** — D1 에서 Redfish 경로가 막혔을 때만: 웹 콘솔의 BIOS 업데이트 화면에서 허용 파일 형식과 XHR(개발자도구 Network 탭)을 캡처한다. 그 경로가 가상 USB 대신 쓸 수 있는 두 번째 후보가 된다.

## 4. Part E — 전원 제어 완결 (게이트 ③ · E1.5 계약)

| ID | 등급 | 확인 |
|---|---|---|
| E1 | S | `Reset` `{"ResetType":"On"}` → `PowerState: "On"` 확인 — **게이트 ③ 완결** |
| E2 | S | (여유 시) `GracefulShutdown` 실동작 — UC-2 즉시 정지의 입력 |
| E3 | R | `GET /redfish/v1/Systems/Self/ResetActionInfo` — 허용 ResetType 전량 채집(E1.5 계약 어휘) |

1호에서 ForceOff 는 실증됐다. E1 의 On 재투입까지 확인되면 `BIOS flash → ForceOff → BMC flash → On → 검증` 흐름의 전원 왕복이 완결된다.

## 5. Part F — 잔여 소항목 (1호 이월)

| ID | 등급 | 확인 |
|---|---|---|
| F1 | S | Redfish 세션 발급 재시도 — `POST /redfish/v1/SessionService/Sessions`, 실패 시 상태코드 · 본문 채증. `GET /redfish/v1/SessionService` 로 상한 · 타임아웃 확인 |
| F2 | R | `GET /redfish/v1/Systems/Self/Bios` **응답 전문**에서 `@Redfish.Settings` 링크 확인 — BIOS pending 객체가 `/Bios/SD` 인지 확정(E3-1 전제). 링크가 없으면 `GET /redfish/v1/Systems/Self/Bios/SD` 직접 시도 |
| F3 | R | 웹 콘솔 Syslog 설정 화면의 XHR 캡처(1호 C4 이월) — E3-2 계약 범위 입력 |
| F4 | R | 실패 복구 가용성 관측(1호 A7 이월) — `ipmitool mc reset cold` 도구 가용 여부 확인만, 실패 유발 금지 |

F1 결과와 무관하게 E1.5 클라이언트는 Basic auth 우선으로 설계한다(1호 실측 — Basic 가용). F1 은 세션 방식의 가용 여부를 확정해 두는 근거용이다.

## 6. 기록 양식

| ID | 결과(가능 / 불가 / 유지 / 다름 / 미수행) | 한 줄 요약 | 원문 위치 |
|---|---|---|---|
| D1 | | | |
| D2 | | | |
| D3 | | | |
| D4 | | | |
| D5 | | | |
| E1 | | | |
| E2 | | | |
| E3 | | | |
| F1 | | | |
| F2 | | | |
| F3 | | | |
| F4 | | | |

## 7. 우선순위와 반영처

- 순서는 **D → E → F**. 시간이 없으면 D1 · D2 만으로도 E2-2 설계 분기 판정의 절반이 선다(허용 파라미터 실측). E 는 5분, F 는 15분 안쪽이다.
- 결과 반영처: ① `docs/T3-checklist.md`(전원 제어 항목 완결 처리, E2 절에 BIOS 경로 판정 기재) ② **E2-2 plan 의 설계 분기 결정**(Redfish 통합 vs 가상 USB 확정 — 필요시 E2 재편 discussion 의 후속 개정) ③ E1.5 · E3-1 · E3-2 plan 입력 ④ 원장 = Notion `E0-4` 에 이어 기록.
