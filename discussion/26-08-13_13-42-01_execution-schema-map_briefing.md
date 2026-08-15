# 게스트 실행 데이터 모델 — execution · assignment 테이블과 관계

> **문서 종류**: 참고 브리핑. E 실행 유효성 토론(`26-08-13_11-17-47_E-execution-readiness_discussion.md`)의 Q2 첨언 — "execution 도메인 관련 테이블과 그 관계를 별도 문서로 설명할 것" — 에 대한 산출물이다. Q2(DEGRADED 의 skip 기록 거처) 판단의 입력이 되도록, 각 테이블이 무엇을 담고 언제 행이 생기는지를 코드 실측으로 서술한다.
> **작성**: 2026-08-13 13:42 KST. 기준 코드 = 앵커 dev(`fe9833f`). U3-5-a(ue 워크트리)는 스키마 변경이 없어(커밋 명시 "DDL 0") 이 문서에 영향 없다.
> **읽는 순서**: §1 큰 그림 → §2 테이블별 → §3 행의 일생(시간 순 서사) → §4 Q2 를 위한 함의.

---

## 1. 큰 그림 — 두 클러스터

게스트 서버를 둘러싼 테이블은 성격이 다른 두 클러스터로 나뉜다.

- **execution 클러스터** — **게스트에 관한 사실**의 기록. 어떤 하드웨어가 존재하고(등록 · 인벤토리), 지금 어디까지 진행했으며(커서 · 신호), 무엇을 했는가(원장). 5개 테이블.
- **assignment 클러스터** — **게스트에게 시킬 일의 계획**의 기록. 어떤 세팅 정의서가 붙었고 그 내용이 무엇인가. 스냅샷이라 원본 정의서와 생명주기가 분리된다. 2개 테이블.

두 클러스터는 `guest_server` 를 통해서만 만난다. 참조 방향은 provisioning → execution 단방향이다(역방향 참조는 R7 이 제거한 생성자 순환을 재생성하므로 금지 — 엔진이 할당을 읽을 때도 `OwnedPhasesProvider` 인터페이스로 역전해 읽는다).

```
 (management)                (execution — 게스트 사실)            (provisioning — 할당 계획)

 board_model ◄───N:1─── guest_server_detail
                              │ 1:1
                              ▼
     host_nic_binding ──N:1─► guest_server ◄──N:1── setting_assignment ──1:N─► assigned_process
                              ▲        ▲                  │
        provisioning_progress─┘ 1:1    └──N:1── setup_step└─ source_definition_ref
                                               (append-only)   = setting_definition 의
                                                               id + 이름 "스냅샷" (FK 아님)
```

`guest_server_detail.board_model_id` 가 execution 에서 management 로 나가는 **유일한 구조화 FK** 다. 나머지 하드웨어(CPU · 메모리 · 디스크 · NIC)는 전부 `hardware_spec` JSON 안에 있다 — U3-5-a 의 `AssignmentEligibility` 가 detail 을 통째로 드는 이유가 이것이다(서버가 가진 하드웨어는 이 엔티티 하나에 모여 있다).

## 2. 테이블별 설명

### 2-1. `guest_server` — 앵커

게스트의 정체성과 운영자 입력. **상태 컬럼이 없다** — 운영 상태는 `decommissionedAt` + progress 신호에서 `GuestServerStatus.derive` 로 파생한다(U1 §D4).

| 컬럼 | 의미 |
|---|---|
| `id` (UUID PK) | 시스템 내부 식별자 |
| `system_uuid` (unique) | 하드웨어가 보고하는 SMBIOS UUID — 재부팅 멱등의 키 |
| `name` · `model_name` · `serial_number` · `memo` | 운영자 입력 4필드 |
| `decommissioned_at` | 회수 시각. null = 미회수 |
| `guest_token` (unique) | 게스트 신원 토큰 — 부팅 커널 인자로 전달, 에이전트 API 가 대조 |
| `last_seen_at` | 마지막 접촉 관찰 로그 — dispatch 판정 입력 아님 |

### 2-2. `guest_server_detail` — 하드웨어가 보고한 사실 (1:1)

`board_model_id` FK(management) + `board_serial` + `discovery_stage`(`IPXE_REGISTERED` → `DIAGNOSTIC_ENRICHED`) + `hardware_spec` · `software_spec` JSON + BMC 신원(`bmc_ip` · `bmc_mac`). 등록 시점엔 JSON 이 전부 null 이고 진단 리눅스 수집(E1-2)이 채우면서 stage 를 승급한다. 다회 갱신 주체가 있어 `@Version` 낙관적 락.

### 2-3. `host_nic_binding` — LAN NIC (1:N)

`host_mac`(unique) · `lan_ip` · `ip_source` · `is_primary` · `bond_group`. BMC 관리 포트와 별개의 네트워크 노드다.

### 2-4. `provisioning_progress` — 커서와 신호 (1:1)

실행 진행의 SSOT. **커서 1개 + 신호 3개** 구조다.

| 컬럼                               | 의미                                                     |
| -------------------------------- | ------------------------------------------------------ |
| `current_phase`                  | 큰 단계 커서 — 게스트 사실 신호에만 전진(DEC-2), 역행 금지                 |
| `started_at`                     | 개시 신호(DEC-26 명시 개시 버튼). null = 게스트는 대기 스크립트만 받는다       |
| `failed_at` + `failed_step_code` | 실패 신호(DEC-4). 해제는 운영자 재시도만. step code null = 운영자 수동 전환 |
| `completed_at`                   | 종단 신호(DEC-25 "보유 마지막 phase 완주")                        |
| `phase_meta` (JSON)              | phase 부속 데이터                                           |
| `last_transition_at` · `version` | 전이 시각 · 낙관적 락                                          |

실패와 종단은 도메인 메서드가 상호배타로 강제한다(공존 = 표현 불가 상태). **주목할 공백**: 종결 어휘가 완주(`completed_at`)와 실패(`failed_at`) 둘뿐이다 — E 토론 Q1 · Q4 첨언(TTL 만료 → 중단 = PARTIAL_SUCCESS)이 확정되면 세 번째 종결 방식의 구분이 필요해진다. 구분을 저장할지 파생할지는 §4.

### 2-5. `setup_step` — 실행 원장 (1:N, append-only)

무엇을 했는가의 이력. `step_code`(`ProvisioningPhaseStep` — phase 는 여기서 파생, 별도 컬럼 없음) + `status` + `status_meta` JSON + `started_at` · `finished_at`.

status 어휘: `PENDING` / `RUNNING` / `SUCCEEDED` / `FAILED` / **`SKIPPED`("작업 건너뜀")** — skip 어휘가 이미 있다.

행이 생기는 경로가 둘이다:

- **`SetupStep.instant(...)`** — **서버 측이 판정 즉시 적재**하는 단발 행(시작 = 종료 시각). BOOTSTRAPPING 의 `NETWORK_ALLOCATING` · `INIT_PERSISTING` 이 이 경로다.
- **`SetupStep.openRunning(...)`** — 게스트의 시작 보고로 RUNNING 열림 → 종료 보고로 1회 닫힘(`close`, 중복 보고 no-op 멱등). 진단 단계들이 이 경로다.

즉 **원장은 순수한 게스트 보고 기록이 아니다** — 서버가 적는 행이 설계상 처음부터 있었다. 이 사실이 Q2 판단을 바꾼다(§4).

### 2-6. `setting_assignment` — 할당 스냅샷 (게스트당 활성 1)

정의서를 게스트에 붙인 시점의 **행 단위 복사본**. 원본 정의서와의 연결은 `source_definition_ref`(id 스칼라 + 표시명) **소프트참조** — FK 가 없어 원본이 purge 되어도 스냅샷은 생존한다. 상태 컬럼 없이 두 시각에서 파생한다: `consumed_at`(개시 시 소비) · `superseded_at`(재할당 시 논리 종료), 활성 술어 = `superseded_at IS NULL`(부분 unique 인덱스 + `@Version` 이 게스트당 활성 1개 불변식을 지킨다).

**`owned_phases`** (CSV, 안정 코드) — 이 할당이 보유한 phase 집합의 **동결본이자 phase 진행의 유일 권위**(결정 D-G). 엔진(`PhaseCursorAdvancer`)이 `OwnedPhasesProvider` 를 통해 읽어 다음 phase 전진 / 종단을 판정한다.

### 2-7. `assigned_process` — 단계 스냅샷 (1:N)

정의서의 `SettingProcess` 를 행 단위로 복사한 불변 이력. `payload_json`(계약 원문 무변환 복사 — 실행 시점의 소비 대상) + `process_type`(표시 · 감사 전용 판별자) + `frozen_bios_settings_json`(**BASIC_SETTING 전용 deep-freeze** — 템플릿 내용까지 복사해 자족, 그 외 타입은 null). uk(assignment_id, process_type).

payload 의 참조 성격이 phase 별 검증 비대칭의 근원인데, 비대칭은 **두 겹**이다. **겹 ① 참조 해소 시점** — 펌웨어 `LATEST`("최신 버전") 모드는 의도만 계약에 담고 어떤 버전인지의 해석을 실행 시점으로 미룬다(`SPECIFIED` 직접 지정이면 이 겹은 없다). **겹 ② 실물의 거처** — 버전을 지정해도 스냅샷에 얼려지는 것은 참조이지 실물 파일이 아니다. 게스트는 실행 시점에 서빙 경로의 실제 파일을 소비하고 그 파일은 management 자원 세계에서 드리프트 · 소실될 수 있으므로, 펌웨어 · ISO 는 선택 모드와 무관하게 실행 시점 존재 검증이 필요하다(blocking=true 예시가 "ISO 소실" 인 이유). BIOS 세팅 템플릿만 deep-freeze 가 가능했던 것은 참조 대상이 실물 없는 **순수 값 묶음**이라 복사가 곧 자족이기 때문 — 두 겹이 모두 없는 유일한 경우다.

### 2-8. 인접 테이블

- `guest_server_group` · `guest_server_group_member`(U3-4) — 사람이 만드는 영속 묶음. guest_server 를 N:1 참조하며 실행 의미론에 관여하지 않는다.
- `pxe_network_config`(E1-I-3) — PXE 서브넷 설정. 게스트와 FK 관계 없음.

## 3. 행의 일생 — 시간 순 서사

**① PXE 최초 부팅** (`/api/pxe/v1/boot`) — 한 트랜잭션이 4행을 함께 만든다: `guest_server`(+ 토큰 발급) · `guest_server_detail`(iPXE 가 보고한 vendor/board 문자열로 `board_model` 해석, stage=IPXE_REGISTERED, JSON 전부 null) · `host_nic_binding`(primary, DHCP) · `provisioning_progress`(커서=BOOTSTRAPPING seed). 서버 측 instant step 2행(`NETWORK_ALLOCATING` · `INIT_PERSISTING`)이 원장에 적힌다. 재부팅은 멱등(systemUUID 조회로 등록 생략).

**② 할당** (U3) — `setting_assignment` + `assigned_process` N행 생성(정의서 스냅샷). execution 쪽은 무변화.

**③ 개시** (운영자 버튼) — `AssignmentStartService` 한 트랜잭션: `progress.startedAt` 기록 + 활성 할당 `markConsumed`. 이때부터 `/boot` 가 대기 스크립트 대신 phase 스크립트를 준다.

**④ 진단** (DIAGNOSE_LINUX) — 게스트 보고로 원장에 openRunning → close 행이 쌓이고, 수집 보고가 `guest_server_detail` 을 enrich(JSON 적재 + stage 승급). `last_seen_at` 이 접촉마다 갱신.

**⑤ phase 전진 · 종단** — 진단 완주 시 `PhaseCursorAdvancer` 가 `owned_phases` 를 읽어 다음 보유 phase 로 `current_phase` 전진, 없으면 `completed_at` 종단. 이후 각 phase 도 같은 리듬(부팅 재진입 → step 보고 → 완주 판정).

**⑥ 예외 경로** — 실패: `failed_at` + step code(게스트 보고) 또는 code null(운영자 수동 전환), 해제는 운영자 재시도뿐(펌웨어 flash 실패는 재시도 차단 — 벽돌 리스크). 회수: `decommissioned_at` 기록, `/boot` 는 회수 스크립트만. 재할당: 미개시 활성만 supersede-then-new.

## 4. Q2 를 위한 함의 — 실측이 바꾸는 것

**정정 하나부터.** 토론 문서 Q2 의 ⓐ 반론으로 "게스트가 보고하지 않은 행을 서버가 만들면 원장 순도가 깨진다" 를 들었는데, **실측상 그 순도는 애초에 없다** — `SetupStep.instant` 는 서버 측 판정의 즉시 적재를 위해 설계된 팩토리이고 BOOTSTRAPPING 2행이 이미 그 경로로 적힌다(DEC-3). 원장의 실제 계약은 "게스트만 적는다" 가 아니라 **"실행에 일어난 사실을 적는다"** 다. 따라서 ⓐ 의 비용은 생각보다 작다.

세 후보의 실측 재평가 —

- **ⓐ `setup_step` 에 SKIPPED 행** — 어휘(`SKIPPED`) · 팩토리(`instant`) · 선례(서버 측 적재)가 전부 이미 있다. 스키마 변경 0. "펌웨어 BIOS 축을 자원 결손으로 건너뜀" = `BIOS_UPDATING` / SKIPPED / statusMeta 에 사유 — 기존 구조에 자연스럽게 얹힌다.
- **ⓑ progress 수준 기록** — 필수인 것은 컬럼 신설이 아니라 **종결 의미론의 구분 가능성**이다. 지금 종결 신호는 `completed_at`(완주) · `failed_at` 둘뿐이라 TTL 중단(완주도 실패도 아닌 세 번째 종결)이 표현되지 않는데, 구분 자체는 집 관례대로 파생으로도 성립한다(종결 후 파생 입력 — 원장 · `owned_phases` — 이 전부 동결되므로 안전. `GuestServerStatus.derive` · `AssignmentState` 선례). 단 표현 방식과 무관하게 둘은 필요하다: ① `BootScriptDispatcher` 4행의 `osInstalled` 커서 프록시 교정 — TTL 중단 시 커서는 이미 미수행 phase 로 전진해 있어 "커서 ≥ OS_INSTALLING = OS 설치됨" 전제가 깨지고, 그대로면 OS 없는 베어메탈에 `completedExit` = 부팅 실패 루프다. ② 중단 사유(어느 자원 결손 · TTL 만료)의 사건 시점 기록 — 사유는 파생 불가라 저장이 강제된다. 후보지였던 `phase_meta` 는 이후 실측(작성자 0 · 소비자 0)으로 제거가 확정되어 후보에서 빠졌다(토론 문서 §8 D3). **후속 개정**: TTL 만료 = 실패 전환이 확정되면서(토론 문서 §8 D1 4차 개정) 세 번째 종결 방식과 ① 의 프록시 결함 시나리오는 소멸했다 — 중단이 `completed_at` 를 쓰지 않는다.
- **ⓒ 별도 이력 축** — 위 둘로 충족되면 스키마 신설 근거가 없다.

**관찰**: ⓐ 와 ⓑ 는 배타가 아니라 **층위가 다르다** — 축 하나를 건너뛴 사실(step 층위)은 ⓐ 가, 실행 전체가 부분 성공으로 끝났다는 판정(실행 층위)은 ⓑ 가 다룬다. 그리고 집 관례를 따르면 역할 분담은 "사실은 기록, 상태는 파생, 사유는 기록" 이다 — 축 skip 과 중단 사유는 사건 시점에 적고(ⓐ 의 SKIPPED 행 + statusMeta), 부분 성공이라는 판정은 동결된 원장 · `owned_phases` 에서 파생한다. 확정은 토론 문서 Q2 에서 한다.

## 5. 참조

- 엔티티 — `execution/entity/GuestServer` · `GuestServerDetail` · `HostNicBinding` · `ProvisioningProgress` · `SetupStep`, `provisioning/assignment/entity/SettingAssignment` · `AssignedProcess`
- enum — `execution/enums/ProvisioningPhase` · `ProvisioningPhaseStep`(step→phase 파생) · `ProvisioningStatus`(SKIPPED 포함) · `DiscoveryStage`, `provisioning/assignment/enums/AssignmentState`
- DDL — `ddl/U3-1_setting_assignment.sql` · `U3-2-a_active_uniqueness.sql` · `E1-0a_provisioning_progress_signals.sql` · `E1-2_collection.sql` · `schema.sql`(전체)
- 등록 트랜잭션 — `execution/service/GuestServerRegistrationService.initialRegistry`
