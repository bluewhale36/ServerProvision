# BMC Redfish 실측 체크리스트 5호 — BMC 설정(E3-2)의 쓰기 표면 채집과 왕복 실증

> **문서 종류**: 실측 세션 계획 · 기록 양식(참고 브리핑). 원장 = Notion `E0-4-5`(신설 필요 시).
> **작성**: 2026-08-20 KST.
> **주 목적**: E3-2(BMC 설정)의 실행 계약을 세우기 위한 실측 — **사내 표준 BMC 세팅의 각 항목을 어떤 API 로 쓸 수 있는가**(Redfish 또는 AMI 웹 API)와 **쓰기 왕복이 실제로 도는가**.
> **연결**: 1호(자격증명 PATCH 204 실증 — E3-0 재료) · 3호(Syslog 의 Redfish `NetworkProtocol` 부재 확정 · H4 재캡처 이월 · SessionTimeout 30초) · 4호(§8 — 펌웨어 트랙은 Redfish 최종 확정) · `docs/T3-checklist.md`.
> **장비 현행 상태**: MS04-CE0 · BIOS F29 · BMC 13.06.27 · 보드 시리얼 QG260700082.
> **정정(2026-08-25)**: §1 후보 표와 §5 N1~N3 은 사용자 확정(O1) 전에 세션이 상정한 것이었고, 그 확정 없이 레시피로 굳힌 오류다. **사내 표준 BMC 세팅의 정본 = Notion `E0-3 : BMC 내부 API 경로 확인 작업`**(AMI 웹 API 쓰기 4종 — DateTime · Cold Redundant · Fan Profile · Network Bond, 요청 바디 · 응답 채집 완료). N1~N3 은 폐기하고 §5 를 E0-3 기준 W 계열로 대체했다. Redfish 쓰기 왕복(NTP · eth0)은 표준 항목이 아니므로 하지 않는다.

---

## 0. 출발점 — E3 의 반쪽만 실증돼 있다

E3(펌웨어 설정) 로드맵에서 **E3-1(BIOS 설정)은 3호 G 계열이 전 왕복(PATCH → pending → Reset 적용 → 원복)을 실증**했고 검증 룰 원천(속성 레지스트리 273개)까지 확보됐다. 반면 **E3-2(BMC 설정)는 쓰기 실측이 사실상 0** 이다 — 있는 것은 1호의 자격증명 PATCH(fresh ETag + `If-Match`, 204) 한 건과, 3호의 부정 실측(Syslog 항목이 Redfish `NetworkProtocol` 에 없음 — AMI 웹 API 계약일 가능성) 뿐이다. BIOS 처럼 pending(SD) 경유인지 직접 PATCH 즉시 적용인지도 미확인이다. 이 격차를 메우는 것이 5호다.

## 1. 선행 입력 — 사내 표준 BMC 세팅 항목 (정본 = Notion E0-3, 2026-08-25 정정)

| 항목 | 경로(AMI 웹 API) | 비고 |
|---|---|---|
| 로그인 · CSRF | `POST /api/session` → `CSRFToken` → 이후 `X-CSRFTOKEN` 헤더 필수 | 공통 |
| DateTime(NTP · 시간대) | `PUT /api/settings/date-time` | 바디 채집 완료 |
| Cold Redundant | `POST /api/cold_redundant-status` | 바디 채집 완료 |
| Fan Profile | `POST /api/settings/fanprofile` | 보드별 JSON 채집 완료(MS03-CE0 · MS74-HB0 · MS04-CE0) |
| Network Bond | `PUT /api/settings/network-bond` | 응답 후 세션 삭제 — 접속 상실 위험 |
| ID indicator | `POST /api/actions/chassis-led` | 조작(설정 아님) |

전부 AMI 웹 API 다. 종전 후보 표에 있던 관리자 비밀번호(E1.6 소관) · SNMP/SMTP · 추가 계정 · syslog 는 표준 항목이 아니다.

## 2. 안전 수칙

1. **BMC 네트워크 설정 변경은 접속 상실 위험** — IP · VLAN 변경 실측은 물리 콘솔(또는 로컬 KVM) 확보 상태에서만 수행하고, 그 외 세션에서는 hostname · DNS 같은 무해 속성으로 대체한다.
2. 모든 쓰기 항목은 **변경 전 현행 값 채집 → 변경 → readback → 원복** 이 한 세트다. 원복 없이 세션을 끝내지 않는다.
3. 자격증명 미기재. 응답 원문의 원장은 Notion(`E0-4-5`).
4. 세션 인증은 Basic 우선(3호 확정 — 세션 토큰은 30초 만료라 왕복 실측에 부적합).

## 3. 위험 등급

1~4호와 동일 — R(읽기 전용) / S(가역 상태 변경) / X(집행). 이번 호는 R(M 계열)과 S(N 계열)뿐, X 없음.

## 4. Part M [R] — 설정 표면 전수 채집 (읽기 전용 · 30분 내외)

- **M1**: `GET /redfish/v1/Managers/Self` 전문 채집 — ⓐ 어떤 속성이 실리는가 ⓑ `@Redfish.Settings`(pending) 링크가 있는가(BIOS 는 SD pending 경유였다 — BMC 도 그런지가 E3-2 쓰기 모델을 가른다) ⓒ `Oem.AMI` 확장 유무 ⓓ ETag.
- **M2**: `GET /redfish/v1/Managers/Self/NetworkProtocol` — NTP · SNMP · SSH · IPMI · KVM · 가상미디어 등 프로토콜 표면 전수. 3호의 "Syslog 부재" 재확인 겸.
- **M3**: `GET /redfish/v1/Managers/Self/EthernetInterfaces` 컬렉션 + 각 인터페이스 전문 — DHCP/Static · 주소 · VLAN · hostname · DNS 의 쓰기 표면.
- **M4**: `GET /redfish/v1/AccountService` + `Accounts` 컬렉션 — 계정 신설 · 역할(Role) 표면, 비밀번호 정책 속성.
- **M5**: **Oem 트리 탐색** — `Managers/Self` 응답의 Oem 링크를 따라가며 Syslog · 알림류 설정이 Redfish Oem 확장에 있는지 최종 확인(표준 트리 부재 ≠ Redfish 전체 부재).
- **M6**: **AMI 웹 API 채집(H4 재캡처 흡수)** — 브라우저 개발자도구 HAR 켜고 BMC 웹 UI 의 설정 화면(Settings > Log Settings 류 — Syslog 원격 전송, 그리고 §1 에서 확정된 항목 중 Redfish 표면이 없는 것들)을 **실제로 저장까지 조작** → 요청 · 응답(`/api/settings/...`) 채집. 3호 H4 는 SEL 조회 화면(`/api/logs/event`)을 잘못 캡처했으므로, 이번엔 "설정을 저장하는 순간"의 요청이 잡혀야 한다.

## 5. Part W [S] — E0-3 쓰기 4종의 브라우저 밖 재연 (2026-08-25 정정 — 종전 N1~N3 폐기)

레시피 = `discussion/26-08-20_17-45-50_bmc-redfish-fieldwork-5-N-recipe.md`(정정판). 항목 계약은 E0-3 이 정본이라 재채집하지 않고, **코드가 같은 요청을 보낼 수 있는가**만 실증한다.

- **W1**: DateTime 왕복 — 한 필드만 바꿔 PUT → readback → 원복.
- **W2**: Cold Redundant 왕복 — enable 1 → 0.
- **W3**: Fan Profile 왕복 — `strMode` FAN_PROFILE ↔ default(보드별 JSON 파일).
- **W4**: Network Bond — 세션 삭제 거동 확인, 재로그인 후 readback · 원복. **마지막 · 물리 콘솔 확보 시에만.**
- **W5**: 지속성(선택) — 재시작 후 유지, `PreserveConfiguration` 보존 여부.

## 6. 판정이 만드는 것 (E3-2 설계 입력, 2026-08-25 정정)

- E3-2 의 항목별 계약 = **E0-3 의 웹 API 4종**(Redfish 아님). M 계열의 Redfish 쓰기 모델 판정(pending 유무)은 BMC 설정에는 적용되지 않는다.
- W1~W4 → 웹 API 클라이언트의 세션 · CSRF · TTL · Bond 후 재로그인 계약이 E3-2 plan 의 §계약 표가 된다.
- 판정 항목 ⓐ~ⓔ 는 레시피 §6.

## 7. 채증 규율

항목 식별자(M1~M6 · W1~W5)와 1:1 — R 계열은 응답 JSON 원문, S 계열은 전 · 후 · 원복 3점 값 + 요청 원문, M6 은 HAR + 저장 직전 화면. 원장은 Notion, 완료 기록은 `docs/T3-checklist.md`.
