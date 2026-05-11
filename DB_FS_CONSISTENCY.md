# ServerProvision — DB CRUD ↔ FS 정합성 설계 정리

> **목적** : 본 프로젝트가 "DB 와 파일시스템의 정합성" 을 다루기 위해 도입한 메커니즘 4종 (reconciliation / nudge / trash / ghost) 의 누적된 설계 결정을 한 문서로 정리. Claude Project 등 외부 대화에 이식해 추가 의견 수집 / 비판 검토 / 대안 탐색 용도.
>
> **작성 시점** : 2026-05-07. MK3 (Trash 패턴) 완료 + MK3-1 (Ghost 일급 개념) CP4 진행 중. 본 문서는 구현 디테일이 아니라 **개념 / 결정 / 트레이드오프** 에 초점.

---

## 1. 프로젝트 컨텍스트 (요약)

### 1.1 무엇을 하는 시스템인가

사내 물리 서버 프로비저닝 자동화 시스템. 관리자가 다음 자원들을 등록 / 관리하면, 그 자원들을 조합한 "세팅 정의서" 가 PXE 부팅으로 진입한 신규 물리 서버에 BIOS/BMC 업데이트 → BIOS+RAID 설정 → OS 설치 → OS 후설정을 자동 수행한다.

### 1.2 자원 도메인 5종

| 도메인 | URL | 자원 형태 | 마커 형태 |
|---|---|---|---|
| **OS Image** | `/management/os` | 1 OS 버전당 N 개의 ISO 파일 | SIDECAR — `<file>.provision.json` |
| **Board Model** | `/management/board` | 메타데이터만 (FS 자원 없음) | 해당 없음 |
| **BIOS** | `/management/bios` | 디렉토리 트리 (폴더/zip/단일파일 업로드) | IN_TREE — `<dir>/.provision.json` |
| **BMC Firmware** | `/management/bmc` | 디렉토리 트리 (BIOS 와 동형) | IN_TREE |
| **Subprogram** (Driver/Utility) | `/management/subprogram` | 디렉토리 트리 | IN_TREE |

각 자원은 DB row 1 개 + FS 의 실제 파일/디렉토리 1 개 + 마커 파일 1 개로 구성. **세 가지가 동기화되지 않으면 시스템이 자원을 사용 불가**.

### 1.3 마커 (`.provision.json`)

각 FS 자원에 동반되는 메타파일. 내용 :
- `resourceType` (BIOS_BUNDLE / OS_ISO / BMC_FIRMWARE / SUBPROGRAM)
- `resourceId` — DB PK
- `attributes` (도메인별 부속 메타)
- `manifestHash` — 트리 SHA-256 (디렉토리) 또는 파일 SHA-256 (단일파일)
- `signature` — HMAC-SHA256 (HMAC secret 은 환경변수)
- `createdAt`

마커는 **DB 와 FS 의 연결 다리**. 마커가 있어야 reconciliation 이 "이 위치의 자원이 DB id=23 의 자원" 임을 인지 가능.

---

## 2. 핵심 철학 — Truth Source 분리

본 시스템의 모든 정합성 결정은 다음 2 truth-source 의 명확한 분리에 기반한다.

### 2.1 Active 트리 = FS-truth

자원의 **현재 위치 / 내용물** 은 파일시스템이 진실. 외부에서 관리자가 SSH 로 `mv` 했거나 디스크 재구성으로 절대경로 prefix 가 바뀌면 → reconciliation 이 자동 추종해 DB.path 를 갱신.

이유 : 운영자가 FS 측에서 사소한 조정을 했을 때 어플리케이션이 "그 자원을 못 찾겠어요" 라고 사용자에게 사용자 입력을 요구하면 운영 부담이 큼. FS 가 정상 상태면 시스템이 자원을 계속 사용할 수 있어야 함.

### 2.2 Lifecycle 메타 = DB-truth

자원이 **active / deprecated / soft-deleted 어느 단계인지**, **언제 trash 에 들어갔는지**, **어디서 trash 됐는지** 는 DB 가 진실. 외부에서 마음대로 trash 디렉토리에 파일을 던져 넣어도 그것이 lifecycle 변화로 해석되지 않음.

이유 : lifecycle 은 운영자의 명시적 의도. 외부 mv 같은 사소한 변화에 끌려가면 "실수로 mv 했더니 DB 가 자동 삭제됨" 같은 사고가 발생.

### 2.3 이중 의도 검증 명제 (불가침)

> 한 drift / **lifecycle 변경** 이 lifecycle + path 두 의도 변화를 동시 포함하면 자동 적용 제외. 의도가 단일화되는 sub-condition (예: SOFTDEL_ESCAPE_TO_ORIGINAL — 원래 경로로 복귀) 만 자동 적용.

예 : "trash 외부에서 자원을 발견했지만 위치도 다름" (`SOFTDEL_ESCAPE_TO_OTHER`) → 자동 복원 거절. lifecycle 변화 (delete → restore) + path 변화 (trash → 다른 위치) 두 의도가 섞임 → 운영자 명시 결정 필요.

**MK3-2 갱신 — 적용 범위 확장** : 본 명제는 reconciliation 의 12 DriftKind 분류뿐 아니라 **lifecycle 진입점의 사전조건에도 동일하게 적용**. softDelete 시점에 path 가 이미 어긋난 상태 (`Files.notExists(DB.path)`) 라면 lifecycle + path 두 의도가 섞이는 것으로 해석 → softDelete 자동 진행 거절 → 사용자 명시 액션 요구 (modal 3택). 자세한 내용은 §6.4 의 MK3-2 결정 박스 참조.

---

## 3. 정합성 메커니즘 4 종

### 3.1 Reconciliation — 시스템이 자가 점검하는 메커니즘 (MK1, MK3 확장)

**역할** : DB 와 FS 가 어긋난 상태 (drift) 를 주기적으로 / 시작 시 / 수동으로 감지. 자동 추종 가능한 drift 는 자동 적용, 그 외는 보고만.

**스캔 알고리즘** :
1. 모든 도메인의 active 자원 인벤토리 수집 (`MarkableScanner.findActiveMarkables()`)
2. 자원 path 의 부모 디렉토리 union → scan roots (extra-roots 환경변수로 추가 가능)
3. `Files.walk(root, depth=8)` 로 `*.provision.json` 마커 모두 수집
4. DB 인벤토리 ↔ 디스크 마커 셋 비교 → drift 분류
5. 자동 적용 가능 drift 는 즉시 (옵트인), 그 외는 DriftReport 영속화 + UI 표시

**스캔 트리거** :
- 시작 시 1 회 (`@EventListener(ApplicationReadyEvent)`, configurable)
- 1시간 주기 quick scan (configurable)
- 24시간 주기 deep scan (manifestHash 재계산 — 변조 감지)
- 수동 트리거 (`POST /maintenance/reconciliation/scan`)

스캔 자체는 BackgroundJob 으로 등록되어 navbar 작업 조회 아이콘에 진행률 노출.

**DriftKind 12종 (MK3 + MK3-1 통합)** :

| # | DriftKind | 발생 조건 | 자동 적용 |
|---|---|---|---|
| 1 | `PATH_DRIFT` | DB.path 위치 마커 부재. 다른 active 위치에 (resourceType, resourceId) 마커 발견. 본체 파일도 같이 | ✅ ON (옵트인) |
| 2 | `MISSING` | DB.path + 어디에도 매칭 마커 없음. 자원 분실 | ❌ OFF |
| 3 | `ORPHAN` | 마커는 디스크에 있지만 DB 매칭 자원 없음 | ❌ OFF |
| 4 | `SIGNATURE_INVALID` | HMAC 서명 깨짐. 변조 의심 | ❌ OFF |
| 5 | `HASH_MISMATCH` | deep scan 시 manifest 해시 불일치 | ❌ OFF |
| 6 | `RESOURCE_RENAMED` | 같은 부모 디렉토리 내 다른 파일명으로 자원+마커 동시 발견 (sidecar 1:1 매칭) | ✅ ON |
| 7 | `RESOURCE_RENAMED_ORPHAN` | 자원 부재, 같은 디렉토리에 마커는 잔존. 자동 추론 불가 | ❌ OFF |
| 8 | `SOFTDEL_ESCAPE_TO_ORIGINAL` | DB.is_deleted=true, trash 부재, active 트리의 DB.path 위치에 자원+마커 발견 | ✅ ON |
| 9 | `SOFTDEL_ESCAPE_TO_OTHER` | DB.is_deleted=true, trash 부재, DB.path 외 위치에 발견. 의도 모호 | ❌ OFF, 액션 옵션 없음 (보고만) |
| 10 | `TRASH_LOST` | DB.is_deleted=true, DB.trashed_path 위치 부재. 외부 정리 의심 | ❌ OFF |
| 11 | `TRASH_MARKER_STALE` | trash 자원 옆에 마커 잔존 (soft-delete 시 정리됐어야 함) | ✅ ON |
| 12 | `GHOST_DB_ROW` (MK3-1) | DB row 만 남고 FS 자원도 trash 도 없는 dead row | ❌ OFF (사용자 사후 검토) |

자동 적용 default 는 모두 OFF (`reconciliation.auto-apply-path-drift=false`, `reconciliation.auto-apply-ghost-row=false`). 운영자가 검토 후 활성화. 전역 OFF 옵션 (`reconciliation.auto-apply=false`) 도 존재.

### 3.2 Nudge — 사용자 등록 흐름의 충돌 협의 (MK2)

**역할** : 자원 신규 등록 시 hash / path 충돌이 발견되면 자동 진행하지 않고 modal 로 사용자에게 "proceed (그래도 등록) / replace (기존 자원 영구삭제 후 등록) / cancel" 3택 제시.

**왜 필요한가** :
이전 정책은 "동일 키 soft-deleted 자원이 있으면 silently hard-delete 후 등록 진행" 이었음. 사용자가 자기도 모르게 자원이 사라지는 사고 발생 → 정책 폐기. 모든 충돌 결정은 명시적 사용자 액션으로.

**Intent + Upload 2단계 핸드셰이크** :
1. `POST /<domain>/{parentId}/upload-intent` (JSON) — 메타만 보내 사전 검증 + token 발급
2. `POST /<domain>/{parentId}/upload` (multipart) — 실제 바이트 전송 + token 헤더

Intent 단계에서 충돌 (단계 A) 또는 Upload 단계의 hash 비교 (단계 B) 에서 nudge 발생. NudgeRegistry 가 5분 TTL 로 세션 보관.

**MK3-1 추가** : nudge candidate 사전 필터에 ghost 제외. ghost 만 충돌이면 nudge 미발급 + 정상 등록 진행.

### 3.3 Trash — Soft-delete 자원의 격리 (MK3)

**역할** : Soft-delete 시 자원과 마커를 별도 디렉토리 (`.soft-deleted/`) 로 격리. active 트리는 항상 깨끗.

**구조** :
```
<provision-base>/.soft-deleted/<resourceType>/<id>/<originalName>_<ts>_<UUID8>.<ext>
```
- `ts` : 밀리초 단위 timestamp
- `UUID8` : 같은 ms 에 두 건 들어와도 충돌 차단

**왜 격리하는가** :
이전 정책은 자원을 active 위치에 두고 DB.is_deleted 만 true. 같은 path 에 신규 등록 시 마커 / 자원 / DB 가 충돌 → reconciliation 이 (resourceType, resourceId) 매칭 실패하는 사고 발생. trash 격리로 active 트리가 항상 1:1 깨끗.

**4 단계 검증 (Restore 시)** :
1. `DB.trashed_path` 위치 파일 존재
2. 원래 경로 부모 디렉토리 접근 가능
3. 원래 경로 동일 이름 파일 부재
4. 다른 active 자원과 hash 충돌 없음

**TTL** : `trashed_at + 30일` 자동 hard-delete. 7일 / 1일 전 알림. 자원별 "보존기간 +30일 연장" UI 액션.

**Trash 페이지** (`/maintenance/trash`) :
- 5 도메인 합본 표시
- 컬럼 : 자원 종류 / ID / 이름 / 원래 경로 / 휴지통 이동 시각 / 잔여 TTL / 액션
- 액션 : 복원 / +30일 연장 / 영구삭제

### 3.4 Ghost — DB-truth ↔ FS-truth 양쪽이 음수인 dead row (MK3-1, 진행 중)

**역할** : "DB row 는 소프트삭제 상태이지만 FS 에 자원도 trash 도 없는" 상태를 일급 개념으로 인식.

**발생 시나리오** :
1. 자원 X 가 DB 등록됨 (path = `/opt/iso/foo.iso`)
2. 운영자가 외부에서 `mv /opt/iso/foo.iso /elsewhere/foo.iso` (FS-truth 변화)
3. 사용자 : "이 자원이 깨졌네, 정리하고 새로 등록하자" → softDelete 호출
4. softDelete 가 `Files.exists(/opt/iso/foo.iso)` 검사 → false → trash mv 건너뜀 → DB 만 lifecycle 전이
5. 결과 : `is_deleted=true AND trashed_at=null AND trashed_path=null AND Files.notExists(DB.path)` = **ghost**

**왜 일급 개념인가** :

ghost 가 비공식 상태로 남으면 4 영역에서 일관되지 않게 처리됨 :

| 영역 | 일급 개념화 전 | 일급 개념화 후 |
|---|---|---|
| **nudge** | 정상 hash 충돌 후보로 노출 → 사용자가 의미 없는 3택을 매번 풀어내야 함 | candidate 사전 필터링 — ghost 만 충돌이면 nudge 미발급 |
| **reconciliation** | invisible — active 스캔 제외 | `GHOST_DB_ROW` drift 감지 → drift apply = DB row hard-delete |
| **restore** | `trashed_path=null` early-return → DB flag 만 flip (lossy) | 명시적 거절 — `GhostRowRestoreNotAllowedException` (409) |
| **휴지통 페이지** | `trashed_at=null` 일괄 필터아웃 → 사용자가 ghost 인지 불가 | "복구 불가" 배지 + "정리" 액션만 활성 |

**핵심 멘탈 모델** : Ghost = DB-truth 와 FS-truth 가 어긋나 복구 불가능한 dead row. 4 영역이 동일 정의를 공유.

---

## 4. 4 메커니즘의 협력 그래프

```
                  ┌────────────────────┐
                  │   사용자 액션 진입   │
                  └──────────┬─────────┘
                             │
              ┌──────────────┼─────────────────┐
              │              │                  │
        등록(upload)      삭제(softDelete)     복원(restore)
              │              │                  │
              ▼              ▼                  ▼
         ┌────────┐     ┌─────────┐       ┌──────────┐
         │ Nudge  │     │  Trash  │       │ 4단계검증 │
         │ (충돌  │     │ (격리)  │       │  (Trash) │
         │  협의) │     └─────────┘       └──────────┘
         └────┬───┘            │                │
              │                │                │
              └────────────────┼────────────────┘
                               │
                               ▼
                     ┌──────────────────┐
                     │ DB ↔ FS 동기화   │
                     │ (마커 발급/삭제) │
                     └────────┬─────────┘
                              │
                              ▼
                     ┌────────────────────────┐
                     │  Reconciliation        │ ← (외부 mv / 변조 등 비협력 변화 감지)
                     │  startup + 1h + 수동   │
                     └────────┬───────────────┘
                              │
                              ▼
                     ┌────────────────────────┐
                     │  DriftKind 12 종 분류   │
                     │  자동 ON 5종 / OFF 7종 │
                     └────────────────────────┘

  Ghost (MK3-1) — 위 흐름이 어긋난 결과로 발생하는 dead state.
  4 영역이 동일하게 인지하고 단일 정리 경로 (drift apply / clear-ghost) 로 수렴.
```

---

## 5. 도메인 모델 핵심 (간략)

### 5.1 LifecycleEntity (5 도메인 super)

```java
abstract class LifecycleEntity extends BaseTimeEntity {
    private boolean isEnabled;       // 토글 (사용 가능/불가)
    private boolean isDeprecated;    // 사용 안 함으로 표시 (active 의 sub-state)
    private boolean isDeleted;       // soft-delete
    private Instant trashedAt;       // trash 이동 시각 (MK3)
    private String trashedPath;      // trash 내 절대 경로 (MK3)

    public void softDelete() { this.isDeleted = true; }
    public void restore() { this.isDeleted = false; }
    public void markTrashed(String path) { this.trashedAt = Instant.now(); this.trashedPath = path; }
    public void clearTrashed() { this.trashedAt = null; this.trashedPath = null; }
}
```

상태 의미 :
- `is_deleted=false + trashed_*=null` : active
- `is_deleted=true + trashed_*=non-null` : 정상 trash
- `is_deleted=true + trashed_*=null + Files.exists(path)` : **(MK3-2 갱신) 정상 케이스 아님 — reconciliation 이 추종해야 할 drift 상태**. 외부 mv 가 발생했으나 reconciliation 이 아직 추종 못한 형태. PATH_DRIFT 와 본질적으로 같은 사건의 다른 시점 표현. MK3-2 도입 후엔 이 상태에서 추가 lifecycle 변경이 들어오면 reject (이중 의도 검증 명제 적용)
- `is_deleted=true + trashed_*=null + Files.notExists(path)` : **ghost** (MK3-1, MK3-2 후 신규 생성 차단)

### 5.2 Markable / MarkableScanner SPI

```java
interface Markable {
    Long getResourceId();
    ResourceType getResourceType();
    Path getResourcePath();        // active 위치 (lifecycle 메타와 무관)
    MarkerLayout getMarkerLayout(); // SIDECAR / IN_TREE
    String getManifestHash();
    String getMarkerSignature();
    void reissueMarker(String hash, String signature);
}

interface MarkableScanner {
    ResourceType supportedType();
    List<Markable> findActiveMarkables();
    Set<Long> findSoftDeletedResourceIds();   // ORPHAN 보호용
    void applyDriftedPath(Long id, Path newPath);
    Optional<String> recomputeManifestHash(Markable m);

    // MK3 trash 액션
    List<Markable> findTrashed();
    List<Markable> findTrashedBefore(Instant t);
    List<Markable> findTrashedBetween(Instant s, Instant e);
    void extendTrashTtl(Long id);
    void restoreFromTrash(Long id);
    void purgeFromTrash(Long id);

    // MK3-1 ghost 액션
    boolean isGhost(Long id);
    List<Markable> findGhostMarkables();
    void applyGhostClear(Long id);
}
```

도메인-agnostic 인프라 (reconciliation, trash controller, ghost controller) 는 본 SPI 만 사용. 도메인은 SPI 구현체로만 인프라에 노출.

---

## 6. 핵심 결정 사항 (시간순)

### 6.1 MK1 — 마커 시스템 일반화 + 자동 추종

- **D11** : `ProvisionMarkerService` 를 BIOS 패키지에서 떼어내 `global/marker/` 로 승격. `MarkerContent` 일반화 (BIOS 특화 필드는 attributes Map 으로 흡수)
- **D12** : 트리거 = startup 1회 + 주기 + 수동
- **D13** : PATH_DRIFT 만 옵트인 자동 적용. 기타는 보고만
- **D14** : DriftReport 정규화 1:N (`drift_report` + `drift`) JPA 엔티티
- **D15** : 보고서 100건 FIFO prune
- **D17** : Miller Columns UI
- **D19** : 스캔 범위 = 자원 path.parent union + extra-roots

### 6.2 MK2 — Lifecycle 정합 + Nudge

- **WAVE 1** : 4 도메인 통합 lifecycle (active / deprecated / soft-deleted)
- **WAVE 2** : Intent + Upload 2단계 핸드셰이크 (단계 A 메타 충돌 nudge)
- **WAVE 3** : ISO clientHash precheck (단계 B 컨텐츠 충돌 nudge)
- 정책 핵심 : 이전의 "silently hard-delete soft-deleted 동일키" 폐기. 모든 충돌은 사용자 명시 액션

### 6.3 MK3 — Trash 패턴

- **DCN1** : trash root = `<provision-base>/.soft-deleted/` (macOS `.trash` 충돌 회피)
- **DCN2** : 도메인별 sub : `<trash>/<resourceType>/<id>/<name>_<ts-uuid>.<ext>`
- **DCN3** : timestamp = ms + UUID8 (ms 충돌 차단)
- **DCN4** : TTL 30일 자동 purge
- **DCN5** : saga 보상 (mv 실패 시 reverse mv + retry 3회 + critical alert)
- **DCN-NEW1** : SOFTDEL_ESCAPE_TO_ORIGINAL 자동 복원 (FS-truth 일관)
- **DCN-NEW9** : TTL 7일 / 1일 전 두 번 알림
- **DCN-NEW10** : RESOURCE_RENAMED 매칭 = sidecar 1:1
- **DCN-NEW11** : `reconciliation.auto-apply=false` 전역 OFF 옵션
- **DCN-NEW12** : SOFTDEL_ESCAPE 두 enum 분리 (`TO_ORIGINAL` / `TO_OTHER`)
- **DCN-NEW13** : SOFTDEL_ESCAPE_TO_OTHER 보고만, 액션 옵션 미제공

### 6.4 MK3-1 — Ghost 일급 개념 (CP4 완료)

- **DCM3-1.1** : Ghost 정의 = `is_deleted=true AND trashed_at=null AND trashed_path=null AND Files.notExists(DB.path)`
- ~~**DCM3-1.2** : softDelete 정책 변경 안 함~~ → **DCM3-2.1 로 대체** (MK3-2 갱신). softDelete 진입 시 `Files.exists(DB.path)` 사전조건 검증 강화로 Ghost 신규 생성 차단
- **DCM3-1.3** : nudge candidate 사전 필터링 — 모든 후보가 ghost 면 nudge 미발급. *MK3-2 후엔 ghost 신규 생성 차단으로 효과는 작아지나 fail-safe 로 유지*
- **DCM3-1.4** : 신규 `DriftKind.GHOST_DB_ROW`. drift apply = DB row hard-delete
- **DCM3-1.5** : 자동 적용 default OFF
- **DCM3-1.6** : restore 호출 시 `GhostRowRestoreNotAllowedException` (409)
- **DCM3-1.7** : 휴지통 페이지에 ghost 표시. "복구 불가" 배지 + 정리 액션만
- **DCM3-1.8** : 판정 SPI 단일 진입점 (`GhostEvaluator` 유틸리티 + `MarkableScanner.isGhost` default)

> **한시성 명시 (사용자 결정, 2026-05-07)** : Ghost 기능은 본 슬라이스 (MK3-1) 도입 이후 *절대 신규 생성되지 않아야* 하는 제약. MK3-2 의 softDelete 사전조건 강화로 신규 생성이 차단되며, 운영 검증 후 ghost 관련 코드 (DriftKind.GHOST_DB_ROW / GhostEvaluator / applyGhostClear / 휴지통 ghost 표시 등) 는 **별도 정리 슬라이스에서 전체 삭제 가능**. 즉 ghost 코드는 *영구 fail-safe* 가 아니라 *한시적 안전망 + 마이그레이션 도구*.

### 6.5 MK3-2 — softDelete Reject 정책 (진행 예정)

- **DCM3-2.1** : softDelete 진입 시 `Files.exists(DB.path)` 검증 사전조건 강화. false 면 409 reject
- **DCM3-2.2** : reject 응답에 구조화된 payload — missingPath / intentToken / availableActions / ghostCandidate
- **DCM3-2.3** : modal 3 택 — 위치 정정 후 삭제 (default · 권장) / 강제 정리 / 취소
- **DCM3-2.4** : "위치 정정 후 삭제" 는 saga — `scanForResource → applyDrift(forced) → 재조회 → softDelete` 4 단계. 일시 실패 시 자동 재시도 (3회, exponential backoff)
- **DCM3-2.5** : "강제 정리" 는 기존 `applyGhostClear` 경로 재사용
- **DCM3-2.6** : NudgeRegistry 동형 `DeleteIntentRegistry` 도입 (5분 TTL)
- **DCM3-2.7** : A2 분류 (`is_deleted=true + trashed_*=null + Files.exists(path)`) 의 "정상" 표기 폐기 — drift 상태로 재분류
- **DCM3-2.8** : 기존 ghost 마이그레이션 = 휴지통 페이지의 "ghost 일괄 정리" 액션
- **DCM3-2.9** : MK3-1 코드는 **한시적 안전망**. 운영 안정 후 별도 정리 슬라이스에서 전체 삭제 검토 (영구 fail-safe 아님)
- **DCM3-2.10** : feature flag `provision.softdelete.reject-on-missing` 게이팅. default false → 운영 검증 후 true
- **DCM3-2.11** : Audit 이벤트 7종 (`SOFTDELETE_REJECTED` 등) 은 **log only 로 시작**. 영속화는 별도 후속 슬라이스 (Audit Event Bus)
- **DCM3-2.12** : 트랜잭션 안의 `Files.exists` 허용 (안정성 비용으로 row lock 점유 시간 증가 수용)
- **DCM3-2.13** : 운영 환경 가정 — 단일 SSD 또는 RAID 묶음 디스크. NFS / 분산 FS 가정 외

---

## 7. 미해결 / 검토 중인 문제들

### 7.1 ~~GHOST_DB_ROW auto-apply default~~ — MK3-2 로 자연 해소

~~현재 OFF. 운영 안정 후 `true` default 화 검토 예정.~~ MK3-2 의 softDelete 사전조건 강화로 ghost 신규 생성이 차단되므로 본 항목의 가치가 작아짐. 도입 시점의 잔존 ghost 마이그레이션은 휴지통 페이지의 "ghost 일괄 정리" 액션 (사용자 명시 트리거) 으로 처리. auto-apply 는 fail-safe 동안 default false 유지.

**자연 해소 후 후속 항목** : MK3-2 운영 안정 후 ghost 관련 코드 (DriftKind.GHOST_DB_ROW / GhostEvaluator / applyGhostClear 등) 의 전체 삭제 검토 슬라이스 — DCM3-2.9 의 한시성 정책에 따라.

### 7.2 SOFTDEL_ESCAPE_TO_OTHER 의 사용자 액션

현재 보고만 (액션 옵션 없음). `TO_OTHER` 의 의도가 모호 (운영자가 trash 자원을 다른 위치로 살린 것? 단순 옮긴 것?) 라 자동/수동 모두 위험.

**의견 수집 포인트** : "사용자에게 위치 confirm modal 제시" 가 필요한 액션인지, 보고만 하고 무시하는 게 맞는지.

### 7.3 Marker secret 회전

HMAC secret (`PROVISION_MARKER_SECRET`) 변경 시 모든 디스크 마커가 `SIGNATURE_INVALID` 됨. `triggerReissueAllSignatures` 를 도입했으나 운영자가 명시 호출해야 함.

**의견 수집 포인트** : secret 회전 경로의 자동화 / 단계화 / fail-safe 가 필요한지.

### 7.4 분산 / 다중 인스턴스

현재 단일 JVM 가정. reconciliation 의 `RUNNING` 상태 차단도 in-memory. 다중 인스턴스 배치 시 고려할 부분 :
- nudge 세션 (NudgeRegistry) 의 in-memory 보관 — Redis 등 외부 store 필요?
- reconciliation 중복 실행 방지 — 분산 락 필요?
- ghost 정리의 동시성 — 같은 row 를 두 인스턴스가 동시 정리 시도 시 idempotent 해야

**의견 수집 포인트** : 단일 인스턴스 가정을 언제까지 유지할 것인지, 분산 도입 시 어떤 구조 변경이 필요한지.

### 7.5 ApplicationFileMover (MK4 — 후속 슬라이스)

UI 에서 파일을 옮기면 마커 + DB 가 동시 갱신. MK1 의 `applyDriftedPath` 를 역방향으로 호출. 본 슬라이스는 MK3-1 완료 후 진입 예정.

**의견 수집 포인트** : 이 기능이 정말로 필요한지 (운영자가 SSH 로 mv 하고 reconciliation 자동 추종 vs UI 에서 명시 mv).

---

## 8. 트레이드오프 / 비판 가능한 포인트

비판적 검토를 위해 자체 진단한 약점 :

### 8.1 마커 파일 자체의 신뢰성

- 운영자가 SSH 로 `cat marker.json` 후 직접 편집 가능 (HMAC 으로 변조 감지는 되지만 우회 가능)
- 마커가 손상되면 자원 자체는 멀쩡한데 등록 / 사용 불가
- 대안 : 마커 없이 `(resourceType, manifestHash)` 만으로 매칭 → 단순하지만 hash 충돌 시 모호

### 8.2 SPI 의 default 메서드 누적

`MarkableScanner` 가 MK1 → MK3 → MK3-1 거치며 default 메서드가 16개로 늘어남. 4 도메인이 모두 동일하게 모든 메서드를 override 하면 default 의미가 사라짐. 새 도메인 추가 시 어디까지 override 해야 하는지 인지 부담.

### 8.3 12 DriftKind 의 카테고리 폭발

자동 ON 5종 / OFF 7종. 사용자가 각 의미를 모두 이해해야 reconciliation 페이지를 의미 있게 활용 가능. 단순화 가능성 :
- 자동 ON 들을 1 종으로 합치고 detail 메시지로 구분?
- 하지만 자동 적용 액션 (path 갱신 vs marker 삭제 vs row 삭제) 이 다르므로 합치기 어려움

### 8.4 Trash 의 디스크 사용량

30일 보관 + 자원별 +30일 연장. ISO 가 12GB 인 경우 trash 디스크 용량 폭발 가능. 현재 정책은 운영자가 디스크 모니터링 + 수동 정리. 자동 압박 (디스크 80% 시 가장 오래된 trash 자동 purge?) 같은 fail-safe 미도입.

### 8.5 Ghost 정의의 race

`Files.exists` 검사는 시점 의존적. 정의 순간 부재 → 1초 후 외부에서 파일 복사 → ghost 가 ghost 가 아님. nudge 필터에서 제외했지만 다음 순간 사용자가 등록하면 conflict 재발생. 
- 완화 : nudge 후보 stream 필터링은 매 등록 진입마다 새로 평가
- 미해결 : ghost 정리 (drift apply 또는 clear-ghost) 직전 외부에서 자원 복구되면 의미 있는 데이터를 잃을 가능성 (자원은 살아있지만 row 가 hard-delete)
- 완화책 : `applyGhostClear` 직전 한 번 더 `GhostEvaluator.isGhost` 확인 (already implemented)

---

## 9. 제기되는 질문들 (Claude Project 에 던지고 싶은 것들)

1. **DB 와 FS 를 같이 다루는 시스템에서, 이 정도의 정합성 메커니즘 (4 종 + 12 DriftKind) 이 적정 복잡도인가?** 더 단순화 가능한가? 아니면 여전히 빈 곳이 있는가?

2. **Ghost 의 일급 개념화는 본질적인 결정인가, 아니면 softDelete 정책의 산물 (A2 케이스 허용) 을 사후 보정하는 패치인가?** A2 케이스 자체를 막는 정책이 더 맞는가?

3. **truth-source 분리 (active = FS-truth, lifecycle = DB-truth) 가 다른 도메인에도 일반화 가능한 패턴인가?** 본 시스템 외에 어디에 적용될 수 있는가? 반대 케이스는?

4. **이중 의도 검증 명제 — drift 가 lifecycle + path 두 변화를 동시 포함하면 자동 제외** — 이 원칙이 일관되게 12 DriftKind 에 적용되었는가? 위반 사례가 있는가?

5. **Reconciliation 의 자동 적용 default OFF 정책** — 사용자 검토 후 ON 권장. 운영 안정 후 default ON 으로 전환하는 게 맞는가, 아니면 처음부터 사용자 명시 액션만 받는 게 맞는가?

6. **Nudge / Reconciliation / Trash / Ghost 4 메커니즘의 책임 분리가 명확한가?** 어디가 어디의 책임을 떠맡을 수 있는가? 또는 합쳐야 하는가?

7. **단일 인스턴스 가정이 언제 깨질 수 있는가?** 깨진 시점에 어떤 구조가 가장 적게 망가지는가?

8. **마커 파일 (`*.provision.json`) 자체가 SPOF 인가?** 마커 손상 / 변조 / 분실에 대한 방어가 충분한가?

---

## 10. 참고 — 인벤토리 코드 / 영역 구분

- **MA1 ~ MA5** : Manage-Application (Stage 1 Management — 어플리케이션 자원 관리)
- **MK1 ~ MK4** : Manage-Kernel (Stage 2 Maintenance — 어플리케이션-서버 운영)
  - **MK1** : 경로 재조정 (Path Reconciliation) — 완료
  - **MK2** : Lifecycle / Nudge — 완료
  - **MK3** : Trash 패턴 — 완료
  - **MK3-1** : Ghost 일급 개념 — 진행 중 (CP4)
  - **MK4** : ApplicationFileMover — 예정
- **U1 ~ U2** : User (Stage 3 Provisioning)
- **S1** : Cross-cutting infrastructure (Background Job)
- **S3** : Cross-cutting Security Hardening
- **S4** : Form 검증 통일
- **S5-1 ~ S5-5** : UI 정합화 sub-slices
- **M0** : 1회성 영역 재구성 / 리네임 슬라이스

---

> 본 문서는 **개념 / 결정 / 트레이드오프** 중심. 구체 구현은 각 슬라이스의 plan docx (`plan/*.docx`) 와 코드 (`src/main/java/...`) 를 참고. 본 문서의 어떤 결정이라도 외부 의견에 따라 재검토 / 폐기 / 강화될 수 있다.
