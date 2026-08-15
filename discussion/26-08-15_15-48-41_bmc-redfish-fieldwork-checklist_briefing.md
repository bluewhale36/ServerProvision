# BMC Redfish 실측 체크리스트 — 2026-08-18(화) 세션

> **문서 종류**: 실측 세션 계획 · 기록 양식(참고 브리핑). E0-3 의 다음 실측 세션에서 확인할 항목을 사전에 고정해, 한 번의 출근으로 E2-3 · E1.5 · E3 의 게이트를 최대한 여는 것이 목적이다.
> **작성**: 2026-08-15 15:48 KST.
> **주 목적**: BMC firmware update 경로 확정(현재 미파악 — GIGABYTE 공식 매뉴얼에 부재).
> **연결**: `discussion/26-07-12_11-00-53_E3-R-bmc-redfish-survey_discussion.md`(웹 조사 — 예상 URI 전체) · `discussion/26-08-08_14-48-20_E2-bmc-redfish-pivot_discussion.md`(13.06.27 UEFI Shell 폐쇄 → Redfish 일원화) · `docs/T3-checklist.md`(E2-3 착수 게이트 3건) · Notion `E0-3 : BMC Redfish API 경로 확인 작업`(실측 이력 원장).

---

## 0. 출발점 — 지금까지의 실측과 이번 세션의 위치

E0-3 의 기존 실측 기록(Notion)은 **전부 AMI 웹 API(`/api/...`) 트리**다 — 로그인(`/api/session` + CSRFToken) · 자격증명 변경 · 센서 · 시스템 인벤토리 · 팬 프로파일 · 섀시 LED. **Redfish 트리(`/redfish/v1`)의 실측 기록은 아직 없다.** 따라서 이번 세션은 BMC firmware update 경로 확정이 주 목적이지만, 사실상 **13.06.27 펌웨어에서의 첫 Redfish 트리 검증**이기도 하다. A1 이 관문인 이유다.

웹 조사(E3-R)가 확보해 둔 예상 계약은 「GIGABYTE Firmware Upgrade Guide v0.04」(2022) 기준이고, 13.06.27(2024~2025 빌드)에서 유지되는지가 미확인으로 남아 있다 — 이번 실측이 그 답을 만든다.

## 1. 안전 수칙 (시작 전 확인)

1. **자격증명 · 토큰 · 실제 비밀번호를 이 문서에 적지 않는다.** 이 파일은 git 추적 예정 자산이다. 실측 결과의 원장은 종전대로 Notion E0-3 페이지 본문(응답 JSON 코드 블록 관례)이며, 이 문서에는 판정 결과만 남긴다.
2. **flash 실집행(X 등급)은 유휴 장비에서만, 그날 판단으로.** 경로 확정(R 등급)만으로도 세션 목적은 달성된다. 실집행 시 동일 버전(13.06.27) 재적용이 가장 무해한 실험이다.
3. **업데이트 진행 중 BMC WebGUI 접속 금지**(공식 가이드 명시).
4. **Redfish 세션은 쓰고 나서 반드시 DELETE.** 세션 수 상한이 있어(타 벤더 예 16) 누적되면 로그인 불가가 된다.
5. 전원 제어(B2)는 **꺼도 되는 유휴 장비**에서만.

## 2. 위험 등급

| 등급 | 뜻 | 이번 세션 |
|---|---|---|
| R | 읽기 전용 GET — 무해 | 전 항목 수행 |
| S | 상태 변경이나 가역(세션 생성 · 전원 제어 · 설정 PATCH 시도) | 유휴 장비에서 수행 |
| X | 집행(펌웨어 flash) | 선택 — 당일 판단 |

## 3. 준비물

- BMC IP · admin 자격증명(Notion E0-3 페이지에서 관리), 랩탑에 `curl` · `jq`.
- Redfish 세션 발급(이후 요청은 `X-Auth-Token` 헤더):

```bash
curl -k -i -X POST https://$BMC_IP/redfish/v1/SessionService/Sessions \
  -H 'Content-Type: application/json' \
  -d '{"UserName":"<계정>","Password":"<비밀번호>"}'
# 응답 헤더의 X-Auth-Token 과 Location(세션 URI) 기록. 종료 시:
curl -k -X DELETE https://$BMC_IP<세션 Location> -H "X-Auth-Token: $TOKEN"
```

- Basic auth(`-u 계정:비밀번호`)도 병행 가능 — 웹 조사의 curl 예시가 전부 Basic auth 였으므로 둘 다 통하는지 자체가 확인 항목(B1)이다.

## 4. Part A — 주 목적: BMC firmware update 경로 확정

| ID | 등급 | 확인 |
|---|---|---|
| A1 | R | `GET /redfish/v1` — Redfish 서비스 활성 여부 · `RedfishVersion` 실측값 · `Oem.Ami` 존재 |
| A2 | R | `GET /redfish/v1/UpdateService` — **E2-3 게이트 ①** |
| A3 | R | `GET /redfish/v1/UpdateService/FirmwareInventory` — Targets 후보 · 버전 표기 형식 |
| A4 | R | SimpleUpdate 의 허용 파라미터(ActionInfo) — `UpdateComponent` OEM 확장 유지 여부 |
| A5 | R | 웹 콘솔 펌웨어 업데이트 화면의 XHR 캡처 — Redfish 밖 대안 경로 |
| A6 | X | (선택) SimpleUpdate 실집행 — 동일 버전 재적용 + Task · 진행률 관측 |
| A7 | R | 듀얼 이미지 · 실패 복구 경로 확인 |

**A1** — 첫 관문. 404 거나 서비스 비활성이면 웹 콘솔 서비스 설정에서 Redfish 활성화부터. 여기서 막히면 이후 전 항목의 전제가 바뀌므로 최우선.

**A2** — 응답에서 셋을 채집한다: ① `Actions` 의 SimpleUpdate target 문자열(조사값: `/redfish/v1/UpdateService/Actions/SimpleUpdate` — 표준 관례 `UpdateService.SimpleUpdate` 와 다른 명명이었음) ② `MultipartHttpPushUri`(조사값: `/redfish/v1/UpdateService/upload`) ③ `Oem.AMIUpdateService`(FlashPercentage · UpdateStatus · UpdateTarget · PreserveConfiguration). **셋이 유지되면 E2-3 게이트 ①이 열린다.** 응답 JSON 전문을 Notion 에 붙인다.

**A3** — `FirmwareInventory` 하위 목록(조사값 Targets 후보: `BMC` · `BIOS` · `BIOS2` · `BMCImage1` · `MB_CPLD1` 등)과 각 항목의 버전 문자열 형식을 채집한다. 버전 형식은 E2-1 의 FirmwareVersion Value Object 비교 규약의 실측 입력이다.

**A5** — A2~A4 가 조사값과 다르거나 부재하면, 웹 콘솔에서 실제 펌웨어 업데이트 화면을 열고 개발자도구 Network 탭으로 XHR 을 캡처한다. 기존 E0-3 실측이 확인했듯 이 BMC 는 AMI 웹 API(`/api/...`)가 병존하므로, 콘솔이 쓰는 실경로가 Redfish 가 아닐 수 있다 — 그 경우 그 경로가 E2-3 의 집행 계약 후보가 된다.

**A6** — 실집행을 한다면: 게스트 **전원 OFF 상태에서** SimpleUpdate 를 던져 수락 여부를 본다(**E2-3 게이트 ②** — 가이드에 전원 상태 요건이 미기재라 실측만이 답). 응답의 Task URI(`/redfish/v1/TaskService/Tasks/{id}`) 폴링과 `Oem.AMIUpdateService.FlashPercentage` 를 관측하고, 완료 후 A3 재조회로 버전을 대조한다. `PreserveConfiguration` 의 보존 항목(Network · REDFISH · NTP 등)도 함께 기록.

**A7** — `DualImageConfigurations` 존재 확인(A2 응답 안). 실패 대비 선례: HPE Cray CSM 은 `ipmitool mc reset cold` 후 5분 뒤 재시도.

## 5. Part B — E1.5 대비: 세션 · 전원 제어 · ETag

| ID | 등급 | 확인 |
|---|---|---|
| B1 | S | Redfish 세션 발급 · 삭제 실동작(§3 예시) — Basic auth 병용 가능 여부 포함 |
| B2 | S | `POST /redfish/v1/Systems/Self/Actions/ComputerSystem.Reset` — **E2-3 게이트 ③** |
| B3 | S | ETag 요구 실측 — `Bios/SD` · `Accounts/{id}` PATCH |
| B4 | R | 세션 상한 값(문서 또는 실측) |

**B2** — 유휴 장비에서 `{"ResetType":"ForceOff"}` → `GET /redfish/v1/Systems/Self` 의 `PowerState` 로 꺼짐 확인 → `{"ResetType":"On"}` → 켜짐 확인. 이 왕복이 실동작하면 E2-3 게이트 ③과 E1.5 의 핵심 계약이 실증된다.

**B3** — ① `GET /redfish/v1/Systems/Self/Bios` 응답의 ETag 헤더 기록 ② If-Match 없이 `PATCH /redfish/v1/Systems/Self/Bios/SD` (무해한 attribute 1개, 바디 `{"Attributes":{...}}`) → 412 발생 여부 ③ 412 면 fresh ETag 로 If-Match 재시도. MAAS 선례(If-Match 재사용 → 412)가 실측 근거이며, 결과가 E1.5 클라이언트의 "GET → ETag → PATCH, 412 시 1회 재시도" 기본기 채택 여부를 정한다. **PATCH 를 실제 반영까지 가려면 유휴 장비 + 원복까지 한 쌍으로.**

## 6. Part C — E3 대비: BIOS 설정 트리 · Syslog 위치

| ID | 등급 | 확인 |
|---|---|---|
| C1 | R | `GET /redfish/v1/Systems` — instance 명이 정말 `Self` 인지 |
| C2 | R | `GET /redfish/v1/Systems/Self/Bios` — `AttributeRegistry` 이름 · `@Redfish.Settings.SettingsObject` 가 `/Bios/SD` 인지 · Attributes 키 체계 샘플 |
| C3 | R | `GET /redfish/v1/Registries` → 해당 registry JSON 채집(이 보드 1벌) |
| C4 | R | 웹 콘솔 Syslog 설정 화면의 XHR 캡처 — Redfish 트리 밖(`/api/...`) 추정 확인 |
| C5 | 참고 | 신품 장비 개봉 시: 공장 기본 자격증명(시리얼 끝 11자 · G9 스티커) · `PasswordChangeRequired` 강제 여부 — E3-0 입력, 이번 장비는 이미 사내 비밀번호로 전환돼 확인 불가 |

C2 · C3 의 JSON 전문은 E3-1 plan 의 직접 입력이다. C4 는 E3-2 의 계약 범위 결정 입력(Redfish 밖이면 AMI 웹 API 계약을 별도로 세워야 하는지의 근거).

## 7. 기록 양식

수행하며 아래 표를 채운다(이 문서를 직접 편집하거나 Notion 에 복제). 응답 JSON 원문은 Notion E0-3 페이지에 항목 ID 를 제목으로 붙인다.

| ID | 결과(유지 / 다름 / 부재 / 미수행) | 한 줄 요약 | 원문 위치 |
|---|---|---|---|
| A1 | | | |
| A2 | | | |
| A3 | | | |
| A4 | | | |
| A5 | | | |
| A6 | | | |
| A7 | | | |
| B1 | | | |
| B2 | | | |
| B3 | | | |
| B4 | | | |
| C1 | | | |
| C2 | | | |
| C3 | | | |
| C4 | | | |

## 8. 우선순위와 반영처

- 시간이 부족하면 **A(경로 확정) → B(게이트 · E1.5) → C(E3 대비)** 순. A1~A4 + B2 만 끝나도 E2-3 게이트 3건 중 2건이 판정된다(②는 A6 실집행이 있어야 완결).
- 결과 반영처: ① `docs/T3-checklist.md` 의 E2-3 착수 게이트 3건 체크 + 완료 기록 ② E2-3 · E1.5 · E3-1 · E3-2 plan 의 입력 ③ Notion E0-3 본문(원장).
