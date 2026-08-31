# E2-4 집행 현황 상세화 — 착수 브리핑

> **문서 종류**: 단계 착수 브리핑(ue 스트림 인수인계). 원장 = Notion `E2-4 : 집행 현황 상세화`(3차 개발, E 계열).
> **작성**: 2026-08-25 00:11 KST, 앵커 세션(R13 실기 캠페인).

## 1. 유래 — R13 실기 통합 테스트(2026-08-24~25)의 두 관찰

1. **계획 rail 의 완료 표시 부재.** 세팅 정의서 할당 화면의 계획 phase rail 은 `AssignmentQueryService.orderedPlan` 이 진단 리눅스(무조건 phase)를 첫 행에 넣은 전체 로드맵인데, R13(진단 자동 진행) 이후 할당 시점에 진단이 이미 완료된 경우가 생기면서 사용자가 "진단을 다시 도는가" 로 오독했다. rail 은 계획 전용(`AssignmentPlanResponse` — actual 미반영)이라는 설계 주석이 있으나, 진행 · 완료 상태를 결합해 보여 줘야 오독이 사라진다.
2. **집행 중 현황 불투명.** 펌웨어 집행 동안 어느 축(BIOS/BMC)을 굽는 중인지, BMC Task 에서 무엇이 조회되는지 화면에 없다. 사용자가 Postman 으로 Task 를 수동 폴링해 상황을 파악했다.

## 2. 쓸 수 있는 재료

- `GuestServerDetailResponse.FirmwareFlash`(running · axes · remainingMinutes · poweredOff) — E2-2 가 이미 내려 주는 축별 진행. 화면 카드가 이미 있으나 Task 수준 상세는 없다.
- `PollFlashTaskStep` 이 30초마다 Task 를 읽는다 — 관측값을 표시 재료로 남기는 구조(원장 또는 메모리)를 설계에 포함할 것.
- Task 수동 폴링의 전이 기록 원장 = Notion `E0-4`(New → Running → Completed, Exception 두 형태).
- AMI Oem 진행 필드(`Oem.AMIUpdateService.UpdateInformation.FlashPercentage`)는 실측에서 null 이었다(2026-08-25) — percentage 원천은 재탐색 필요, 없으면 상태 문구 수준으로 확정.

## 3. 범위 주의

- **표시 전용** — 집행 로직(step 체계 · 판정)은 건드리지 않는다.
- R13 후속으로 이미 반영된 것과 겹치지 않게: 집행 중 중단성 버튼 차단(`isDisruptionBlocked`)은 완료됐다.

## 4. 관련 파일

`provisioning/assignment/service/AssignmentQueryService.java`(orderedPlan) · `templates/provisioning/server-detail.html`(rail · FirmwareFlash 카드) · `execution/engine/firmware/step/PollFlashTaskStep.java`.
