# BMC Redfish 실측 체크리스트 5호 — BMC 설정(E3-2)의 쓰기 표면 채집과 왕복 실증

> **문서 종류**: 실측 세션 계획 · 기록 양식(참고 브리핑). 원장 = Notion `E0-4-5`(신설 필요 시).
> **작성**: 2026-08-20 KST.
> **주 목적**: E3-2(BMC 설정)의 실행 계약을 세우기 위한 실측 — **사내 표준 BMC 세팅의 각 항목을 어떤 API 로 쓸 수 있는가**(Redfish 또는 AMI 웹 API)와 **쓰기 왕복이 실제로 도는가**.
> **연결**: 1호(자격증명 PATCH 204 실증 — E3-0 재료) · 3호(Syslog 의 Redfish `NetworkProtocol` 부재 확정 · H4 재캡처 이월 · SessionTimeout 30초) · 4호(§8 — 펌웨어 트랙은 Redfish 최종 확정) · `docs/T3-checklist.md`.
> **장비 현행 상태**: MS04-CE0 · BIOS F29 · BMC 13.06.27 · 보드 시리얼 QG260700082.

---

## 0. 출발점 — E3 의 반쪽만 실증돼 있다

E3(펌웨어 설정) 로드맵에서 **E3-1(BIOS 설정)은 3호 G 계열이 전 왕복(PATCH → pending → Reset 적용 → 원복)을 실증**했고 검증 룰 원천(속성 레지스트리 273개)까지 확보됐다. 반면 **E3-2(BMC 설정)는 쓰기 실측이 사실상 0** 이다 — 있는 것은 1호의 자격증명 PATCH(fresh ETag + `If-Match`, 204) 한 건과, 3호의 부정 실측(Syslog 항목이 Redfish `NetworkProtocol` 에 없음 — AMI 웹 API 계약일 가능성) 뿐이다. BIOS 처럼 pending(SD) 경유인지 직접 PATCH 즉시 적용인지도 미확인이다. 이 격차를 메우는 것이 5호다.

## 1. 선행 입력 — 사내 표준 BMC 세팅 항목 (O1, 사용자 확정 필요)

실측 범위는 "실무가 실제로 세팅하는 항목"이 정한다. 아래 후보 표를 실측 전에 확정할 것 — 행 추가 · 삭제 자유:

| 항목 후보 | 사내 세팅 여부 | 비고 |
|---|---|---|
| 관리자 비밀번호 변경 | (확인) | 1호 기실증 — 재실측 불요 |
| BMC 네트워크 (Static IP · 게이트웨이 · VLAN) | (확인) | 접속 상실 위험 — N 계열 유의 |
| NTP · 시간대 | (확인) | |
| 원격 Syslog 전송 | (확인) | Redfish 부재 확정 — AMI 웹 API 후보 |
| SNMP · 알림(SMTP) | (확인) | |
| 추가 계정 · 역할 | (확인) | |
| 기타 (팬 정책 · KVM · 가상미디어 정책 등) | (확인) | |

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

## 5. Part N [S] — 쓰기 왕복 실증 (가역 항목만)

- **N1**: NTP 설정 왕복 — `PATCH NetworkProtocol` (또는 M1 에서 확인된 경로)로 NTP 서버 주소 변경 → readback → 원복. `If-Match` 요건 · 즉시 적용 여부(BMC 재시작 필요?) 기록.
- **N2**: 무해 네트워크 속성 왕복 — hostname(또는 DNS 서버) 변경 → readback → 원복. IP · VLAN 은 물리 콘솔 확보 시에만 별도 수행(N2-x 로 기록).
- **N3**: Syslog(또는 §1 확정 항목 중 AMI 웹 API 소관 1개) 왕복 — M6 에서 채집한 계약으로 curl 재연(브라우저 밖에서 같은 요청이 성립하는가 — 인증 방식 포함) → 원복.
- **N4**: 계정 신설 · 삭제 왕복 — `POST Accounts` 로 시험 계정 생성 → 로그인 확인 → 삭제. (사내 세팅에 계정 추가가 없으면 생략.)
- **N5**: **지속성 확인** — N1~N3 중 1개 항목을 설정한 채 BMC 재시작(가능하면) 후 값 유지 확인. 여유가 되면 **펌웨어 업데이트와의 상호작용**(BMC SimpleUpdate 의 `PreserveConfiguration` 이 이 설정들을 실제로 보존하는가)도 — 단 이는 X 급 실집행을 동반하므로 선택 항목.

## 6. 판정이 만드는 것 (E3-2 설계 입력)

- M1 의 pending 유무 → E3-2 의 쓰기 모델(BIOS 식 pending 적용 vs 즉시 PATCH).
- M6 · N3 → Syslog 류가 AMI 웹 API 소관으로 확정되면 E3-2 는 **이중 클라이언트**(Redfish + AMI 웹 API)가 되고, 그 인증 · 세션 계약이 설계에 합류.
- §1 표 × M/N 결과 → "항목별 API 경로 매트릭스"가 E3-2 plan 의 §계약 표가 된다.

## 7. 채증 규율

항목 식별자(M1~M6 · N1~N5)와 1:1 — R 계열은 응답 JSON 원문, S 계열은 전 · 후 · 원복 3점 값 + 요청 원문, M6 은 HAR + 저장 직전 화면. 원장은 Notion, 완료 기록은 `docs/T3-checklist.md`.
