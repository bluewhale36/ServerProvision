# E3 펌웨어 설정 — 착수 브리핑

> **문서 종류**: 단계 착수 브리핑(ue 스트림 인수인계). 원장 = Notion `E3 : 펌웨어 설정`(하위 E3-R · E3-0 · E3-1 · E3-2).
> **작성**: 2026-08-25 15:06 KST, ue 스트림 세션(U6 CP6 전사 중 준비 조사 · 2026-08-25 사용자 확인).
> **시리즈**: E 로드맵 최초 문서(`discussion/26-07-12_01-41-38_E-roadmap_discussion.md`)의 후속 — 로드맵을 재기술하지 않고 착수 시점의 판정과 입력만 담는다.

## 1. 유래 — 왜 지금 E3 인가

U6(회수 서버 재투입)의 CP6 전사가 진행되는 동안 다음 본류인 E3 의 착수 조건을 조사했다. 사용자 지시는 "E3 단계 준비, 필요시 ES 단계 선행" 이었고, 조사 결과 ES 선행이 불요하다는 판정과 하위 단계 둘이 이미 해소됐다는 사실이 2026-08-25 사용자 확인으로 확정됐다. 이 문서는 그 판정의 근거와 E3-1 CP1 의 입력을 인수인계한다.

## 2. ES-3 선행 불요 — 판정 근거

ES 우산(`ES : provisioning phase 전진 코드 수정`)의 정의는 "각 E 단계 진입에 앞서 그 phase 로의 커서 전진 저지를 푸는 동적 단계" 다. 그 저지는 ES-1 · ES-2 로 이미 풀려 있어, E3 진입에는 새 배선이 필요하지 않다.

| 배선 | 현황 | 근거 파일 |
|---|---|---|
| 커서 전진 | 활성 할당의 ownedPhases 를 읽어 다음 소유 phase 로 일반 전진 | `execution/engine/phase/PhaseCursorAdvancer` (ES-1) |
| 실행기 라우팅 | 빈 등록만으로 dispatch 매트릭스가 HOLD → 위임으로 바뀜(DEC-6) | `execution/engine/phase/PhaseExecutorRegistry` — E2 의 `FirmwareUpdatingExecutor` 가 두 번째 실물 |
| 정의서 → phase | BASIC_SETTING 프로세스가 FIRMWARE_SETTING phase 를 소유 | `provisioning/assignment/mapper/SettingProcessPhaseMapper` |

따라서 `FirmwareSettingExecutor` 빈 하나로 E2 완주 → E3 진입이 열린다. E3-1 CP1 은 "E2 종료 → E3 진입" 의 실동작 확인을 CP2 확인 항목으로 두면 충분하다.

남는 정비 후보는 하나 — **워커 골격**. `FirmwareFlashWorker` 는 FIRMWARE_UPDATING step 만 sweep 하고, E3-1 도 같은 워커 주도형(게스트가 할 일이 없는 phase)이다. 이것은 별도 ES 가 아니라 **E3-1 안에서 공통화**한다 — "미리 분리 금지, 갈라지는 시점에 분리" 원칙이며, E2-2 가 `BmcCredentialsFallback` 을 두 번째 사용처가 생길 때 추출한 선례와 같다. 공통화 규모가 크면 E3-1 을 a(골격 일반화 · 행동 무변경) / b(BIOS 적용)로 나눈다 — 분할 기준은 코드량이 아니라 CP5 검증 줄기가 하나인가다.

## 3. 하위 단계 재편 (Notion 반영은 별도 지시로)

- **E3-R(조사)** — 실측 세션 1~5호(Notion E0-4-1 ~ E0-4-5, 2026-08-18 ~ 08-20)가 조사 항목(Redfish 세대 · BIOS 설정 PATCH 경로 · 계정 변경 URI · 세션 · Syslog 위치)을 전부 소화했다. **종결 처리** 대상 — 산출물은 실측 원장과 `docs/T3-checklist.md` 다.
- **E3-0(BMC 신원 · 자격증명 실체화)** — **E1.6(BMC 계정 표준화)이 흡수**했다. 부트스트랩 경로(보드 시리얼 기반 공장 기본 → 표준 계정 1회 변경 = DEC-21)를 그대로 구현했고, 자격증명 보관은 DB 가 아니라 env 주입으로 확정(2026-08-25 vault 논의 — 공급 방식은 OPS 계열). **E1.6 이관 표기** 대상.
- **E3-1(BIOS 설정 적용)** — 선행 의존(E3-0 · E2-2) 충족. **착수 가능.**
- **E3-2(BMC 설정 적용)** — payload 계약 신설부터. E3-1 뒤.

## 4. E3-1 CP1 의 입력 — 이미 확정된 것

### 4-1. 실측 계약 (원장 = Notion E0-4-3, 2026-08-19, MS04-CE0 · BIOS F29 · BMC 13.06.27)

1. `GET /redfish/v1/Systems/Self/Bios` — 현재 `Attributes` 전체 + `AttributeRegistry: "BiosAttributeRegistry"` 참조 + ETag.
2. `PATCH /redfish/v1/Systems/Self/Bios/SD` + `If-Match: *` + `{"Attributes": {변경분}}` → **204**. 412 면 fresh ETag 로 재시도(E1.6 이 `RedfishClient.patchJson` 에 If-Match 프리미티브를 이미 만들었다).
3. `GET /redfish/v1/Systems/Self/Bios/SD` — pending("Future BIOS Settings")이 생겼는지. **비어 있으면 GET 404 · PATCH 는 수락** 하는 구현이다(2호의 404 가 만든 가설을 3호가 검증).
4. `POST ComputerSystem.Reset {"ResetType": "ForceRestart"}` — 호스트 POST 완료까지 수 분 대기. 전원 실패 모드("IPMI 성공 · 전원 불변")가 재현되면 PowerCycle 폴백(E1.5 가 구현).
5. readback — `GET .../Bios` 의 `Attributes` 를 목표와 대조. 원복까지 한 세트로 실증됨.

레지스트리: `GET /redfish/v1/Registries/BiosAttributeRegistry.json` — 273속성(Enumeration 256 · Integer 15 · Password 2, DefaultValue · 허용값 · ResetRequired · Dependencies 47). 인증은 Basic 유지(세션은 TTL 30초 · DELETE 불가 · 상한 10 — 단기 용도 전용).

### 4-2. 소비 입력

`AssignedProcessSnapshot.frozenBiosSettings`(U3-1 결정 D-C — BASIC_SETTING 전용 deep-freeze). 값 모델은 `BiosSettingValues` — 기본값 대비 **변경분(diff)만**, flat 1-depth, coerce 후 타입 보존. U2-2 설계가 이 구조를 고른 이유가 정확히 "Redfish PATCH `Attributes` 와 구조 동형이라 execution 이 무변환 소비" 다. 즉 E3-1 은 변환 계층 없이 스냅샷 값을 PATCH 바디로 쓴다.

### 4-3. 재사용 자산 (E2-2 · E1.5 · E1.6)

- step SPI(`FlashStep` — order · matches · execute)와 registry(순서 중복 fail-fast), cycle/worker 분리(self-invocation 회피) — 워커 주도 phase 의 골격.
- 신원 확인 관문(`BmcIdentityGuard` — 되돌리기 어려운 조작 직전 보드 시리얼 대조), 시한 정책(`FlashTimeoutPolicy`), 원장 목표 보존(E2-2 F-1 교훈 — close 가 statusMeta 를 덮으므로 목표를 별도 보존).
- 전원: `RedfishPowerService.reset(ForceRestart)` + PXE 복귀 대기(`PowerOnStep` · return-timeout).
- Redfish 프리미티브: `getForResource`(ETag) · `patchJson`(If-Match) — E1.6 신설.
- 자격증명: `BmcCredentialsFallback` + 성공 자격 캐시 — 표준화된 BMC 는 첫 후보로 열린다.

### 4-4. 순서의 실증적 근거

F27 → F29 flash 가 전원 계열 설정(SpeedStep · PackageCState · EPB)을 기본값으로 초기화했다(E0-4-3). "펌웨어 업데이트 → 설정 소실 → 재적용 필요" 가 실측됐고, FIRMWARE_UPDATING → FIRMWARE_SETTING 순서가 여기서 근거를 얻는다. 표준 이미지 flash + Redfish 설정 재적용 파이프라인이 사내 커스텀 이미지 요구의 대체 경로로 성립한다.

## 5. CP1 에서 결정할 쟁점

### E3-1
1. **설정 재부팅과 다음 phase 의 접점** — ForceRestart 후 게스트가 PXE 로 복귀하면 그 부팅이 곧 다음 소유 phase(RAID_CONFIGURATION · OS_INSTALLING)의 진입 부팅이 될 수 있다. readback 시점(재부팅 직후 BMC 에서 GET — 게스트 부팅과 무관)과 커서 전진 시점을 분리할지.
2. **실패 판정과 원장** — PATCH 거절(속성 없음 · 허용값 밖 · Dependencies 위반) · pending 미생성 · readback 불일치 각각을 어느 사유로 남기고, 재시도 가능성(retry 정책 — E2-1-b 의 사다리 · RetryPolicy 선례)을 어떻게 둘지.
3. **두 축의 구조** — BIOS_SETTING · BMC_SETTING 이 E2 의 `FirmwareAxis` 처럼 enum 보유값(step · 시한 · 판정 접근자)으로 갈리는가. E3-2 가 뒤에 오므로 축 구조를 지금 열어 둘지 E3-2 에서 갈라 낼지("미리 분리 금지" 와 대조).
4. **레지스트리 런타임 조회** — Notion 2026-08-07 입력: `Bios` 응답의 `AttributeRegistry` 값과 `@Redfish.Settings.SettingsObject` 링크를 따라가며 경로를 하드코딩하지 않는다(파일명에 버전이 박혀 BIOS 업데이트마다 깨진다). 현재 `biossetting` 화면은 `redfish_materials/`(git 비추적 외부 자산 — 이 워크트리에는 없음, lazy 파싱이라 기동 무관)를 파싱한다. E3-1 이 런타임 조회를 만들면 그 파일 의존을 어디까지 대체할지(화면은 MA6 소관으로 경계 — Notion 명시).

### E3-2 (사전 기록)
- 계약 대안 셋 — ① 항목별 Redfish PATCH(계정 · 네트워크 · NTP · SNMP — 표준 트리 실재, **즉시 PATCH 모델**로 BIOS 의 pending + 재부팅과 비대칭) ② 세팅 항목당 request URL · header · body 를 등록 · 재생하는 프로세스 모델(Notion idea — 인증 정보 평문 금지 · 펌웨어 갱신 시 재검증 수단이 함께 필요) ③ `AMIManager.BackupConfig / RestoreConfig` Oem 액션으로 골든 설정 복원(E0-4-5 보너스 발견).
- Syslog 는 Redfish 트리 밖 — AMI 웹 API `PUT /api/settings/log`, 인증 `POST /api/session` + CSRF 토큰 되돌림(이중 클라이언트 확정, HAR 채집 완료 — `discussion/26-08-20_17-45-50_bmc-redfish-fieldwork-5-N-recipe.md`).
- 시간 · NTP 는 양쪽 표면 존재 — Redfish 우선 원칙 제안. BMC 시계가 틀어져 있던 관찰(NTP 비활성)이 시간 설정의 실무 필수성을 실증.

## 6. 주의

- 자격증명 값은 코드 · 문서에 적지 않는다(env `provision.bmc.password` 계약).
- BIOS 설정 실험은 무해 속성 하나(`SETUP004_BootupNumLockState`)로, 원복까지 한 세트 — 실기 검증 시 안전 수칙(E0-4-3 §1).
- 로컬 개발 DB(3306)는 ES-2 · E2-1-a · R13 · U6 DDL 이 미적용이다(U6 CP5 에서 발견). E3 샌드박스는 U6 의 레시피(로컬 dump 이식 + 위 DDL 적용)를 따른다 — 메모리 `project_u6_guest_reintake` 참고.
- CP1 코드 대조 시 UNIQUE 제약은 부모를 참조하는 **모든 자식 테이블**까지 훑는다(U6 F-1 교훈).

## 7. 관련 파일

`execution/engine/firmware/`(E2-2 골격 전체) · `execution/engine/phase/PhaseCursorAdvancer` · `PhaseExecutorRegistry` · `provisioning/assignment/entity/AssignedProcessSnapshot`(frozenBiosSettings) · `provisioning/biossetting/vo/BiosSettingValues` · `global/redfish/RedfishClient`(getForResource · patchJson) · `global/redfish/RedfishPowerService` · `discussion/26-07-12_11-00-53_E3-R-bmc-redfish-survey_discussion.md`(§2 BIOS 속성 변경 경로 · §7 함정 — 실측으로 해소된 항목 다수) · `docs/T3-checklist.md`(E3 절).
