# MK3-2 — softDelete Reject 정책 전환 + 시스템 비판 관찰 종합

> **수신** : Claude Code
> **발신** : Claude Project (외부 비판 검토 결과)
> **작성** : 2026-05-07
> **참조 문서** : `DB_FS_CONSISTENCY.md` (MK3-1 CP4 진행 중 시점의 통합 정리)
>
> **목적** : 외부 비판 검토에서 도출된 6 종의 관찰을 모두 정리하고, 그 중 가장 본질적인 결함 (ghost 일급화의 사후 패치 성격) 을 해소하기 위한 신규 슬라이스 **MK3-2 (softDelete Reject 정책)** 의 방향과 코드 변경 지점을 빠짐없이 제시. 나머지 비판들은 본 슬라이스 범위 밖이지만 시스템의 약점으로 함께 전달 — Claude Code 가 후속 슬라이스 작업 시 인지하고 있을 것.
>
> **본 문서의 권고는 MK3-1 을 폐기하라는 뜻이 아님.** MK3-1 의 아티팩트 (`DriftKind.GHOST_DB_ROW`, `applyGhostClear`, 휴지통 페이지의 ghost 표시) 는 **MK3-2 의 마이그레이션 안전망 + 정책 우회 경로의 영구 fail-safe** 로 위치가 재해석된다. 코드는 살아남되 의미가 바뀐다.

---

## 0. 한 페이지 요약

### 0.1 외부 비판 검토 결과 — 6 종 관찰

본 검토는 `DB_FS_CONSISTENCY.md` 를 외부 시각으로 읽어 도출된 6 종의 비판이다. 각 비판의 우선순위와 본 문서의 처리 방향 :

| # | 관찰 | 우선순위 | 본 문서 처리 |
|---|---|---|---|
| 1 | **Ghost 가 필요해져버린 이유** — softDelete 의 사전조건 부족이 근본 원인 | 최우선 (시스템 핵심 불변식 자가 위반) | **MK3-2 슬라이스로 해소** (§1~§7) |
| 2 | **4 메커니즘의 책임 겹침** — PATH_DRIFT 와 RESOURCE_RENAMED 의 결과 행위 동일 | 중 (단순화 여지) | **§11.1 별도 관찰** — 후속 슬라이스 |
| 3 | **SPI default 누적** — `MarkableScanner` 16 메서드, ISP 위반 신호 | 중 (확장 부담 누적 중) | **§3.5 + §11.2** — MK3-2 와 동시 또는 별도 슬라이스 |
| 4 | **Ghost race 의 본질** — 자체 진단 §8.5 의 race 는 ghost 가 *존재* 하기 때문에 발생 | 최우선 (1 번과 같은 뿌리) | **§1.3** — MK3-2 로 자연 해소 |
| 5 | **마커 파일이 SPOF** — `(resourceType, resourceId)` 매핑의 유일한 디스크 측 증거 | 중-고 (운영 사고 가능성) | **§11.3 별도 관찰** — DB backup 컬럼 등 후속 슬라이스 |
| 6 | **12 DriftKind 카테고리 폭발** — 본질은 3 축의 조합, UI 표현이 enum 평면화 | 중 (UX 부담) | **§11.4 별도 관찰** — UI 리팩토링 슬라이스 |

### 0.2 본 슬라이스 (MK3-2) 의 처방 — 비판 1, 4 해소

**softDelete 진입 시 `Files.exists(DB.path)` 검증을 사전조건으로 강화**. false 면 진행 거절 (409). 사용자에게 modal 로 다음 3 택 제시 :

1. **위치 정정 후 삭제** (default · 권장) — reconciliation 즉시 실행 → PATH_DRIFT 자동 적용 → 정정된 위치에서 정상 trash mv
2. **강제 정리** — 자원이 진짜 분실됐을 때. row hard-delete (기존 `applyGhostClear` 경로 재사용)
3. **취소**

이로써 ghost 의 신규 생성 경로가 차단된다. 동시에 §8.5 의 race 도 자연 해소 (정리할 ghost 가 생성되지 않으니 race window 도 없음). MK3-1 의 ghost 정리 경로는 (a) 도입 시점의 기존 ghost 일괄 정리 도구, (b) 정책 우회 fail-safe 로 의미가 전환된다.

### 0.3 사용자 영향

**유일하게 추가 클릭이 발생하는 케이스는 "외부 mv 직후의 삭제"** 1 종. 이외 모든 시나리오 (정상 삭제, 휴지통 액션, deprecate, 신규 등록) 는 무변화. 추가되는 1 클릭은 *현재 시스템이 조용히 ghost 를 만들며 잃고 있는 정보* 를 사용자에게 명시적으로 묻는 클릭이라 운영 사고 예방 효과가 크다.

---

## 1. 진단 — 왜 MK3-1 만으로는 부족한가 (비판 1, 4)

### 1.1 이중 의도 검증 명제의 자가 위반 (비판 1)

`DB_FS_CONSISTENCY.md §2.3` :

> 한 drift 가 lifecycle + path 두 의도 변화를 동시 포함하면 자동 적용 제외.

이 명제는 reconciliation 의 12 DriftKind 분류 원칙으로 적용되어 있다. 자동 ON 5 종 / OFF 7 종 분류가 정확히 이 명제의 펼침이다.

그러나 **softDelete 진입점은 이 명제를 따르지 않고 있다**. 시나리오 :

```
T0: 자원 등록 — DB.path = /opt/iso/foo.iso, 거기 파일 존재
T1: 운영자 SSH mv → /opt/iso-archive/foo.iso       (path 의도 변화 — FS-truth shift)
T2: 운영자 UI "삭제"                                  (lifecycle 의도 변화)
T3: softDelete 가 Files.exists(/opt/iso/foo.iso) → false
T4: trash mv 건너뜀, DB 만 is_deleted=true
T5: ghost 생성
```

T2 의 사용자 의도는 lifecycle 변경 (delete) 단일이지만, T3 시점의 시스템 상태는 *path 도 이미 변해 있는* 상황이다. 즉 시스템 입장에서는 lifecycle + path 두 의도가 동시에 처리되는 것이고, 이는 §2.3 의 자동 적용 제외 조건에 정확히 해당한다. **그런데 코드는 그걸 자동 진행한다.**

DriftKind 가 reconciliation 의 분류기로 명제를 지키는 동안, softDelete 가 lifecycle 진입점에서 명제를 깨고 있다. **명제가 시스템 전체에 일관되지 않게 적용되어 있는 것이 본질적 결함이다.**

### 1.2 A2 케이스의 재해석 (비판 1 의 연장)

`DB_FS_CONSISTENCY.md §5.1` 의 상태 분류 :

- `is_deleted=true + trashed_*=null + Files.exists(path)` : "외부 mv 후 곧 되돌릴 수 있는 상태 (정상 케이스)" — **A2**

A2 가 "정상 케이스" 로 보존된 결정 (DCM3-1.2) 이 ghost 발생의 직접 원인이다. 구조적으로 :

- A2 자체는 이미 "외부 mv 가 발생했다" 는 신호. 즉 **A2 는 정상 상태가 아니라 reconciliation 이 아직 추종하지 못한 drift 상태** 다.
- A2 는 PATH_DRIFT 와 본질적으로 같은 사건의 다른 시점 표현. PATH_DRIFT 는 "DB.path 위치 부재 + 다른 위치에 자원" 인데, A2 는 거기에 lifecycle 변경이 끼어든 형태.

A2 를 정상으로 인정한 결과, 시스템은 lifecycle 변경 진입점에서 path 가 이미 어긋났는지 검사하지 않게 됐고, ghost 가 자라났다.

**올바른 위치** : A2 는 reconciliation 이 빨리 해소해야 하는 drift 상태. 그 동안 lifecycle 변경이 들어오면 거절. 즉 A2 를 보존하지 말고 *추적해서 줄이는* 방향이 명제와 일관된다.

### 1.3 race 의 본질 (비판 4)

자체 진단 §8.5 :

> ghost 정리 직전 외부에서 자원 복구되면 의미 있는 데이터 손실 가능. 완화책 : `applyGhostClear` 직전 한 번 더 `GhostEvaluator.isGhost` 확인.

이 race 는 **ghost 가 일급 상태로 *존재하기 때문에* 발생** 한다. ghost 가 생성되지 않으면 정리할 ghost 도 없고, race 도 없다. "한 번 더 확인" 은 race window 를 좁힐 뿐 닫지 못한다.

비판 1 (softDelete 사전조건 강화) 과 비판 4 (race 의 본질) 는 **같은 뿌리** 다. ghost 의 신규 생성 경로를 차단하는 한 행동이 두 비판을 동시에 해소한다.

### 1.4 결론

MK3-1 은 ghost 를 *잘 다루는* 메커니즘으로 설계됐지만, 본 진단에 따르면 ghost 는 *생성되어선 안 되는* 상태다. MK3-2 가 그 생성 경로를 막고, MK3-1 은 (a) 기존 ghost 마이그레이션 + (b) 정책 우회 fail-safe 로 위치가 재정의된다.

---

## 2. 정책 결정 사항 (MK3-2 슬라이스)

### 2.1 핵심 결정 (DCM3-2.x)

| 코드 | 결정 |
|---|---|
| **DCM3-2.1** | softDelete 진입 시 `Files.exists(DB.path)` 검증을 사전조건으로 강화. false 면 409 reject. |
| **DCM3-2.2** | reject 응답은 사용자 modal 을 위한 구조화된 payload 포함 — 어느 path 가 없는지, 어느 액션이 가능한지. |
| **DCM3-2.3** | modal 의 3 택 — 위치 정정 후 삭제 / 강제 정리 / 취소. **default = 위치 정정 후 삭제**. |
| **DCM3-2.4** | "위치 정정 후 삭제" 는 reconciliation 강제 실행 → PATH_DRIFT 자동 적용 → 재시도. 단일 트랜잭션이 아닌 saga (각 단계 보상 가능). |
| **DCM3-2.5** | "강제 정리" 는 기존 `applyGhostClear` 경로 재사용. 신규 코드 추가 없음. |
| **DCM3-2.6** | NudgeRegistry 와 동형의 **DeleteIntentRegistry** 도입. 5 분 TTL token 으로 modal 응답을 추적. |
| **DCM3-2.7** | A2 상태 분류는 §5.1 에서 제거. `is_deleted=true + trashed_*=null` 은 모두 ghost 후보로 간주. |
| **DCM3-2.8** | 기존 ghost row 마이그레이션 = 휴지통 페이지의 "ghost 일괄 정리" 액션으로 사용자 명시 트리거. 자동 일괄 정리 금지. |
| **DCM3-2.9** | MK3-1 의 `DriftKind.GHOST_DB_ROW` 와 `applyGhostClear` 는 deprecated 표시 없이 유지. fail-safe 로서 영구 코드. |
| **DCM3-2.10** | 정책 도입은 feature flag (`provision.softdelete.reject-on-missing=true`) 로 게이팅. 기본값 = `false` (도입 직후) → 운영 검증 후 `true` 전환. |

### 2.2 본 슬라이스 비목적 (Out of Scope)

- 외부 mv 자체를 막는 건 본 슬라이스 목적이 아님. 외부 mv 는 운영자의 정당한 도구이고, reconciliation 이 추종한다.
- nudge 메커니즘 변경 없음. 신규 등록 흐름은 그대로.
- 마커 시스템 변경 없음 (비판 5 는 §11.3 으로 분리).
- DriftKind 분류 변경 없음 (비판 6 은 §11.4 로 분리).
- 휴지통 / 복원 / TTL 흐름 변경 없음.

### 2.3 §2.3 명제와의 일관성 회복

명제는 그대로 유지. 적용 범위만 확장 :

> 한 drift / lifecycle 변경이 lifecycle + path 두 의도 변화를 동시 포함하면 자동 적용 제외.

reconciliation 의 12 DriftKind 분류뿐 아니라 **lifecycle 진입점의 사전조건에도 동일하게 적용**. softDelete 시점에 path 가 이미 어긋난 상태면 두 의도가 섞이는 것으로 해석 → 자동 진행 거절 → 사용자 명시 액션 요구.

---

## 3. 코드 변경 지점 — 빠짐없이

> **읽는 법** : 각 항목은 [영향 도메인] · 변경 종류 · 파일 위치 (추정) · 변경 내용 · 검증 포인트 순. 파일 위치는 현재 코드를 모르고 작성한 추정이므로, 실제 작업 시 grep 으로 확인 후 진행할 것.

### 3.1 도메인 lifecycle 진입점 — softDelete 의 사전조건 강화

**[영향 도메인] 5 도메인 중 FS 자원을 가지는 4 종 (OS Image, BIOS, BMC Firmware, Subprogram). Board Model 은 FS 자원이 없으므로 무관.**

#### 3.1.1 LifecycleEntity 의 softDelete 시그니처 변경

**파일** : `src/main/java/.../global/lifecycle/LifecycleEntity.java`

**현재** :
```java
public void softDelete() { this.isDeleted = true; }
```

**변경 후** : `softDelete()` 는 그대로 두고 (도메인 객체의 순수 상태 전이) , **호출 측 service 에서 사전조건을 검사** 한다. Entity 자체에 `Files.exists` 검사를 박는 건 도메인-인프라 결합. 검사는 service 계층의 책임.

→ Entity 변경 없음. service 에서 처리.

#### 3.1.2 각 도메인 service 의 softDelete 메서드 4 곳

**파일 (추정)** :
- `src/main/java/.../management/os/service/OsImageService.java`
- `src/main/java/.../management/bios/service/BiosBundleService.java`
- `src/main/java/.../management/bmc/service/BmcFirmwareService.java`
- `src/main/java/.../management/subprogram/service/SubprogramService.java`

**변경 패턴** (4 도메인 동형) :

```java
@Transactional
public DeleteResult softDelete(Long id, DeleteIntent intent) {
    var entity = repo.findById(id).orElseThrow(...);
    var path = entity.getResourcePath();

    if (!Files.exists(path)) {
        // DCM3-2.1 — 사전조건 위반
        if (intent == null || intent.isFresh()) {
            // 첫 진입 — modal 트리거 위해 reject
            var token = deleteIntentRegistry.issue(entity);
            throw new SoftDeleteRequiresIntentException(
                token, entity.getId(), path, /* ghost 후보 여부 */);
        }
        // intent 가 동반된 두 번째 진입은 아래 분기에서 처리
    }

    return executeNormalSoftDelete(entity);
}

@Transactional
public DeleteResult softDeleteWithIntent(Long id, DeleteIntentToken token, DeleteAction action) {
    var intent = deleteIntentRegistry.consume(token);  // TTL/소유 검증
    if (!intent.matches(id)) throw new TokenMismatchException(...);

    return switch (action) {
        case CORRECT_PATH_THEN_DELETE -> reconcileThenDelete(id);
        case FORCED_CLEAR              -> forcedClear(id);
        // CANCEL 은 컨트롤러에서 처리 — 여기 도달 안 함
    };
}
```

**핵심 검증 포인트** :
- `Files.exists` 검사가 **`@Transactional` 안** 에 들어가 있을 것. 트랜잭션 밖에서 검사 후 안에서 진행하면 race window 가 열린다.
- 4 도메인의 softDelete 메서드명 / 시그니처 통일. 가능하면 SPI 의 default 메서드로 끌어올려 중복 제거 (3.5 항목 참고).
- `executeNormalSoftDelete` 는 기존 trash mv 흐름 그대로. 변경 없음.

#### 3.1.3 reconcileThenDelete 의 saga 구현

```java
private DeleteResult reconcileThenDelete(Long id) {
    var entity = repo.findById(id).orElseThrow(...);

    // 1. 단일 자원 reconciliation 강제 실행
    var driftReport = reconciliationService.scanForResource(
        entity.getResourceType(), id);

    // 2. PATH_DRIFT 가 발견됐는지 확인
    var pathDrift = driftReport.findOfKind(DriftKind.PATH_DRIFT);
    if (pathDrift.isEmpty()) {
        // path 정정이 안 됨 — 자원이 진짜 분실 상태일 수 있음
        throw new PathCorrectionFailedException(
            "자원의 새 위치를 찾지 못했습니다. 강제 정리를 사용하세요.");
    }

    // 3. drift 자동 적용 (auto-apply 설정 무시 — 사용자가 명시 요청한 것)
    reconciliationService.applyDrift(pathDrift.get(), /* forced= */ true);

    // 4. 재조회 후 정상 softDelete
    var refreshed = repo.findById(id).orElseThrow(...);
    return executeNormalSoftDelete(refreshed);
}
```

**검증 포인트** :
- 3 번의 `applyDrift(forced=true)` 가 전역 OFF 옵션 (`reconciliation.auto-apply=false`) 을 *우회* 해야 함. 사용자 명시 요청은 글로벌 설정과 무관.
- 1 번의 `scanForResource` 는 단일 자원 범위 스캔 메서드가 필요. 현재 `MarkableScanner.findActiveMarkables()` 는 전체 스캔이라 신규 추가 필요 (3.5 항목).
- 1~4 단계 사이 외부 mv 가 또 일어날 가능성 = race. 5 분 token TTL 안에서 발생하면 단계 4 에서 다시 reject 되어 사용자에게 두 번째 modal. 무한 재진입은 token 무효화로 차단.

#### 3.1.4 forcedClear 의 기존 경로 재사용

```java
private DeleteResult forcedClear(Long id) {
    // DCM3-2.5 — MK3-1 의 applyGhostClear 재사용
    return scannerSpi.applyGhostClear(id);  // 또는 동등 액션
}
```

기존 코드 재사용이라 신규 작성 없음. 단 호출 시점의 entity 상태가 ghost 정의 (`is_deleted=true + trashed_*=null + Files.notExists`) 와 *다를 수 있음* — softDelete 시점이라 아직 `is_deleted=false`. `applyGhostClear` 가 그 상태를 받아 처리할 수 있는지 검증 필요. 못 받으면 신규 메서드 `applyForcedClear` 분리 (의미 차이는 작지만 호출 의도가 명확해짐).

### 3.2 컨트롤러 — 새 엔드포인트와 응답 형태

**파일 (추정)** : 각 도메인 controller. `OsImageController`, `BiosBundleController`, `BmcFirmwareController`, `SubprogramController`.

#### 3.2.1 기존 DELETE 엔드포인트의 응답 변화

**현재** : `DELETE /management/os/{id}` → 204 No Content

**변경 후** :
- 정상 케이스 → 204 그대로
- 사전조건 위반 → 409 Conflict + JSON body :
  ```json
  {
    "code": "SOFTDELETE_REQUIRES_INTENT",
    "resourceType": "OS_ISO",
    "resourceId": 23,
    "missingPath": "/opt/iso/foo.iso",
    "intentToken": "del-7f3a-...",
    "tokenTtlSeconds": 300,
    "availableActions": ["CORRECT_PATH_THEN_DELETE", "FORCED_CLEAR"],
    "ghostCandidate": true
  }
  ```

프론트엔드는 이 응답을 받아 modal 을 띄운다. NudgeRegistry 가 5 분 TTL 로 nudge 응답을 추적하는 것과 동형 패턴.

#### 3.2.2 신규 엔드포인트 — intent 응답

**경로** (4 도메인 동형) :
```
POST /management/os/{id}/delete-intent/{token}
POST /management/bios/{id}/delete-intent/{token}
POST /management/bmc/{id}/delete-intent/{token}
POST /management/subprogram/{id}/delete-intent/{token}
```

**Request body** :
```json
{ "action": "CORRECT_PATH_THEN_DELETE" }
```

**Response** :
- 204 — 정상 완료
- 409 — 재진입 시 또 사전조건 위반 (외부 mv 가 또 일어남) → 새 token 발급 후 modal 재진입
- 410 Gone — token 만료
- 422 — `CORRECT_PATH_THEN_DELETE` 인데 PATH_DRIFT 못 찾음 (자원 진짜 분실)

#### 3.2.3 도메인 공통 추출 가능성

4 도메인이 동일한 패턴이라 controller 단의 코드 중복이 발생. **AbstractLifecycleController** 같은 super 또는 SPI 기반 분리 검토. 현재 SPI (`MarkableScanner`) 는 reconciliation/trash/ghost 인프라 전용이라 controller 까지 끌어들이긴 부담. 이 슬라이스에선 4 곳 복붙 후 다음 슬라이스에서 통합 리팩토링 권장.

### 3.3 DeleteIntentRegistry — 신규 인프라 컴포넌트

**파일** : `src/main/java/.../global/lifecycle/DeleteIntentRegistry.java` (신규)

**역할** : NudgeRegistry 와 동형. 5 분 TTL 로 (token, resourceType, resourceId, issuedAt) 보관. modal 의 두 번째 호출 시 token 검증.

**구조 (NudgeRegistry 와 같은 형태로)** :
```java
@Component
public class DeleteIntentRegistry {
    private final Map<DeleteIntentToken, DeleteIntent> store
        = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(5);

    public DeleteIntentToken issue(LifecycleEntity entity) {...}
    public DeleteIntent consume(DeleteIntentToken token) {...}  // TTL 검증, 1회용
    public void invalidate(DeleteIntentToken token) {...}

    @Scheduled(fixedDelay = 60_000)
    public void purgeExpired() {...}
}
```

**검증 포인트** :
- token 은 1회용. consume 후 store 에서 제거.
- 5 분 만료. NudgeRegistry 와 TTL 일치 (사용자 일관성).
- in-memory. **§7.4 (분산 인스턴스) 의 미해결 항목으로 동일하게 인계**. 단일 인스턴스 가정 유지.

### 3.4 ReconciliationService — 단일 자원 스캔 메서드 추가

**파일** : `src/main/java/.../global/reconciliation/ReconciliationService.java`

**현재** : `scan()` 은 전체 인벤토리 대상.

**추가 필요** :
```java
public DriftReport scanForResource(ResourceType type, Long id) {
    // 단일 자원에 대해 (a) DB row 조회, (b) DB.path.parent 와 extra-roots 에서
    // 마커 검색, (c) DriftKind 분류. 결과는 신규 DriftReport 또는 in-memory only.
    ...
}

public void applyDrift(Drift drift, boolean forced) {
    // 기존 applyDrift 에 forced 파라미터 추가.
    // forced=true 면 reconciliation.auto-apply 전역 설정과 driftKind 의 자동 ON/OFF
    // 모두 무시하고 apply.
    ...
}
```

**검증 포인트** :
- `scanForResource` 의 결과 영속화 여부 결정 필요. softDelete 흐름의 일부라 영속화 안 하는 게 깔끔 (트랜잭션 일관성). 다만 운영 디버깅 측면에선 영속화가 유리. → **flag 로 결정 ; 기본 미영속화** 권장.
- `forced` 파라미터는 사용자 명시 액션의 강제 적용 통로. audit log 에 "forced apply" 표시 필수.

### 3.5 MarkableScanner SPI — 단일 자원 메서드 + 분리 (비판 3 의 부분 대응)

**파일** : `src/main/java/.../global/marker/MarkableScanner.java`

§11.2 에서 SPI 의 default 메서드 16 개 누적 부담을 분석했고, 본 슬라이스에서 메서드가 더 추가되므로 **인터페이스 분리 (ISP)** 를 동시 진행 권장. 단 §11.2 의 완전한 4 분할은 별도 슬라이스 (CP5 또는 후속 MK3-3) 로 미루고, 본 슬라이스에선 *신규 메서드를 어디에 넣을지만* 결정.

**제안 분리 (전체 그림 — 본 슬라이스에선 부분 적용)** :
```java
// 인벤토리
public sealed interface MarkableInventory
    permits OsIsoMarkableInventory, BiosBundleMarkableInventory, ... {
    ResourceType supportedType();
    List<Markable> findActiveMarkables();
    Optional<Markable> findActiveMarkableById(Long id);   // ← 신규 (3.4 지원)
    Set<Long> findSoftDeletedResourceIds();
}

// drift 적용
public sealed interface MarkableDriftApplier permits ... {
    void applyDriftedPath(Long id, Path newPath);
    Optional<String> recomputeManifestHash(Markable m);
}

// trash 액션 (MK3)
public sealed interface MarkableTrashOperator permits ... {
    List<Markable> findTrashed();
    List<Markable> findTrashedBefore(Instant t);
    List<Markable> findTrashedBetween(Instant s, Instant e);
    void extendTrashTtl(Long id);
    void restoreFromTrash(Long id);
    void purgeFromTrash(Long id);
}

// ghost 액션 (MK3-1, fail-safe 로 유지)
public sealed interface MarkableGhostOperator permits ... {
    boolean isGhost(Long id);
    List<Markable> findGhostMarkables();
    void applyGhostClear(Long id);
}
```

**본 슬라이스 (MK3-2) 의 결정** :
- 신규 메서드 `findActiveMarkableById` 는 `MarkableScanner` 에 default 메서드로 일단 추가 (default 17 → 18). **분리는 본 슬라이스 이후로**.
- 단 분리 방향성을 코드 주석으로 명시 — `// TODO(MK3-3): MarkableInventory 로 이전 예정` 형태.
- 도메인 구현체는 본 슬라이스에서 `findActiveMarkableById` 만 override.

**검증 포인트** :
- sealed interface 로 새 도메인 추가 시 4 곳을 모두 permit 에 등록해야 함 → 추가 인지 부담. 다만 컴파일 타임 강제라 누락 방지 효과 더 큼.
- 분리 전까지 default 메서드 누적은 의식적 부채로 인지. §11.2 에서 별도 추적.

### 3.6 휴지통 페이지 — "ghost 일괄 정리" 액션 추가

**파일** :
- `src/main/java/.../maintenance/trash/TrashController.java`
- `src/main/resources/templates/.../trash.html` (또는 React 컴포넌트)

**추가** : 휴지통 페이지 상단에 "기존 ghost 일괄 정리" 버튼. 클릭 시 :
1. 확인 modal — "현재 시스템에 ghost row 가 N 개 있습니다. 모두 정리하시겠습니까? 이 작업은 되돌릴 수 없습니다."
2. 확정 시 `MarkableGhostOperator.findGhostMarkables` 로 전체 수집 → 각 도메인의 `applyGhostClear` 호출
3. BackgroundJob 으로 등록 (navbar 진행률 표시)

**검증 포인트** :
- 일회성이지만 영구 잔존 액션. 시스템에 ghost 가 한 번도 안 생기더라도 버튼은 남는다 (정책 우회로 발생할 가능성 대비).
- 버튼 옆에 ghost 카운트 실시간 표시 → 0 이면 버튼 비활성.

### 3.7 사용자 메시지 / 문구

**파일** : `src/main/resources/messages*.properties` 또는 i18n 위치

#### 3.7.1 reject modal 문구

```
delete.reject.title=파일을 찾을 수 없습니다
delete.reject.body=시스템이 추적 중인 위치({path})에 파일이 없습니다. 다음 중 하나를 선택해주세요.
delete.reject.action.correct=위치 정정 후 삭제 (권장)
delete.reject.action.correct.hint=파일을 다른 곳으로 옮기셨다면 이 옵션을 선택하세요. 시스템이 새 위치를 찾아 정정한 후 삭제합니다.
delete.reject.action.forced=강제 정리
delete.reject.action.forced.hint=파일이 진짜로 사라졌다면 이 옵션을 선택하세요. 시스템 등록만 정리되고 디스크는 변경되지 않습니다.
delete.reject.action.cancel=취소
```

**중요** : modal 은 *진단 도구* 역할 겸함. "왜 이게 떴는가" 가 사용자에게 즉시 이해되어야 함. "운영자가 이 파일을 옮기셨거나 삭제하셨을 수 있습니다" 같은 어조 권장.

#### 3.7.2 강제 정리 확인 문구

```
delete.forced.confirm.title=강제 정리 확인
delete.forced.confirm.body={resourceName} 의 시스템 등록만 제거됩니다. 디스크 파일은 그대로 유지됩니다(이미 사라진 경우 영향 없음). 이 작업은 되돌릴 수 없습니다.
```

#### 3.7.3 PATH_DRIFT 못 찾았을 때

```
delete.correct.failed.title=새 위치를 찾지 못했습니다
delete.correct.failed.body=자원의 새 위치를 시스템 인벤토리에서 찾을 수 없습니다. 자원이 진짜 분실됐다면 "강제 정리" 를 사용하시거나, 자원을 등록 위치({originalPath}) 또는 인벤토리 스캔 범위 내로 복원하신 후 다시 시도해주세요.
delete.correct.failed.action.forced=강제 정리로 진행
delete.correct.failed.action.cancel=취소
```

### 3.8 Audit log 확장

**파일** : 기존 audit log 컴포넌트 (위치 추정 — 별도 조사 필요)

**추가 이벤트 종류** :
- `SOFTDELETE_REJECTED` — 사전조건 위반으로 reject. payload : missingPath, ghost 후보 여부
- `DELETE_INTENT_ISSUED` — token 발급
- `DELETE_INTENT_CONSUMED` — modal 응답 처리. payload : 선택한 action
- `DELETE_INTENT_EXPIRED` — TTL 만료
- `PATH_CORRECTION_THEN_DELETE` — saga 성공
- `PATH_CORRECTION_FAILED_THEN_FORCED` — 사용자가 fallback 선택
- `FORCED_CLEAR_INVOKED` — 직접 강제 정리

각 이벤트는 사용자 / 시각 / 자원 식별자 필수. 운영 사고 추적의 핵심 자료.

### 3.9 Feature flag

**파일** : `application.yml` 또는 환경별 properties

```yaml
provision:
  softdelete:
    reject-on-missing: false   # 도입 직후 false. 운영 검증 후 true 전환.
```

`false` 일 때는 기존 동작 유지 (사전조건 검사 안 함, ghost 생성 가능). `true` 일 때만 본 슬라이스 동작. 토글로 즉시 롤백 가능.

**검증 포인트** :
- flag 가 false 일 때도 신규 코드 (DeleteIntentRegistry, controller 의 새 엔드포인트) 는 컴파일/존재. dead code 가 되지만 flag 로 활성화 가능 상태로 대기.
- flag 토글 시 진행 중인 intent token 처리 — flag false 로 전환되면 진행 중 token 은 모두 무효화 (5 분 후 자연 만료) .
- 단계적 활성화 — 4 도메인 모두 동시 적용 권장. 일부 도메인만 활성화 시 운영자가 도메인별로 다른 동작을 학습해야 해서 인지 부담.

---

## 4. 구현 순서 (체크포인트)

각 CP 는 독립 PR 로 분리 가능. CP1~CP3 까지는 flag false 로 dormant ; CP4 에서 켠다.

### CP1 : DeleteIntentRegistry + 예외 + 응답 모델
- `DeleteIntentRegistry`, `DeleteIntentToken`, `DeleteIntent` 신설
- `SoftDeleteRequiresIntentException` + GlobalExceptionHandler 매핑 (409)
- `DeleteRejectResponse` DTO
- 컴파일 + unit test 만 통과. 호출하는 곳 없음.

### CP2 : Service 4 곳 변경 + reconcileThenDelete saga
- 각 도메인 service 의 softDelete 메서드에 사전조건 검사 추가 (flag 로 가드)
- `softDeleteWithIntent` 신규 메서드 4 곳
- `ReconciliationService.scanForResource`, `applyDrift(forced)` 추가
- saga 의 race / 실패 보상 단위 테스트

### CP3 : Controller + 신규 엔드포인트 + 메시지 / Audit
- 4 도메인 controller 에 사전조건 분기 + intent 엔드포인트
- 메시지 properties 추가
- Audit 이벤트 7 종 발행 코드

### CP4 : 휴지통 페이지 ghost 일괄 정리 + flag 활성화 준비
- TrashController 의 ghost-bulk-clear 액션
- 기존 ghost row 카운트 표시
- 운영 환경에서 flag false → true 전환 전 dry-run 도구

### CP5 (별도 슬라이스 — MK3-3 또는 S5-x) : SPI 분리 (§11.2)
- `MarkableScanner` deprecated, 4 신규 sealed interface 도입
- 도메인 구현체 4 곳 마이그레이션
- 인프라 컴포넌트 의존성 정리

> CP5 는 본 슬라이스 (MK3-2) 와 분리하는 것이 안전. 본 슬라이스는 정책 변경에 집중.

---

## 5. 마이그레이션 — 기존 ghost row 처리

### 5.1 시점

flag `reject-on-missing` 을 `true` 로 전환하기 *전* 에 처리 권장. 전환 후에는 신규 ghost 가 생성되지 않으니, 처리 누락 시 잔존 ghost 가 지속됨 (사용자가 명시 트리거 안 하면 영원히 잔존).

### 5.2 절차

1. 운영자가 휴지통 페이지에서 ghost 카운트 확인. (CP4 완료 후 가능)
2. 카운트가 의미 있는 수 (예: 10+) 면 운영자 검토 — 어느 자원들이 ghost 인지 목록 export.
3. 검토 후 "ghost 일괄 정리" 버튼 클릭. BackgroundJob 으로 처리.
4. 처리 완료 후 flag `true` 전환.
5. 전환 후 운영 1~2 주 모니터링 — `SOFTDELETE_REJECTED` audit 이벤트 발생 빈도 확인. 발생 패턴이 정상인지 (외부 mv 후 정상 정정 흐름인지, 또는 운영자가 혼란스러워하는지) 판단.

### 5.3 롤백 시나리오

flag `true` 전환 후 운영 사고 발생 시 :
1. flag 즉시 `false` 로 토글. 새 softDelete 는 기존 동작 (ghost 생성 가능) 으로 회귀.
2. 진행 중 intent token 은 자연 만료 (5 분).
3. 사고 원인 분석 후 코드 수정 → 재배포 → flag 재활성화.

flag 게이팅의 가치가 여기서 발휘됨. 본 슬라이스는 핵심 정책 변경이라 안전한 롤백 경로 필수.

---

## 6. 검증 항목 — 누락 시 운영 사고 직결

### 6.1 단위 테스트 (필수)

- softDelete 진입 시 path 부재 → 409 + intentToken 발급 검증
- intent token 의 TTL / 1회용 / 소유 검증
- saga 의 reconciliation 단계 실패 시 422 응답
- saga 의 4 단계 사이 외부 변화 race 의 안전성
- forced clear 가 ghost 가 아닌 상태도 처리 가능한지
- flag false 일 때 사전조건 검사 비활성

### 6.2 통합 테스트 (4 도메인 동형 매트릭스)

| 시나리오 | OS | BIOS | BMC | Subprogram |
|---|---|---|---|---|
| 정상 softDelete | ✓ | ✓ | ✓ | ✓ |
| 외부 mv 후 softDelete (자원 살아있음) | ✓ | ✓ | ✓ | ✓ |
| 외부 rm 후 softDelete (자원 분실) | ✓ | ✓ | ✓ | ✓ |
| modal token 만료 후 재진입 | ✓ | ✓ | ✓ | ✓ |
| saga 중간 외부 mv 추가 발생 | ✓ | ✓ | ✓ | ✓ |
| flag toggle 동작 | ✓ | ✓ | ✓ | ✓ |

각 도메인의 SIDECAR / IN_TREE 마커 차이가 결과에 영향 없는지 확인.

### 6.3 E2E (수동)

- 운영 staging 에서 외부 mv → UI 삭제 → modal 흐름
- modal "위치 정정 후 삭제" → 실제 DB.path 갱신 + trash mv 검증
- modal "강제 정리" → DB row 부재 + 디스크 파일은 잔존
- 휴지통 페이지의 ghost 일괄 정리 액션

### 6.4 Audit 검증

위 시나리오 모두에서 audit 이벤트가 정확히 기록되는지. 특히 `SOFTDELETE_REJECTED` 의 빈도가 운영 후 추적 가능해야 함 (정책의 사용자 영향 측정 지표).

---

## 7. MK3-1 의 위치 재해석 — 코드는 살리되 의미가 바뀐다

### 7.1 무엇을 유지하는가

- `DriftKind.GHOST_DB_ROW` — 정책 우회 / 외부 인입 경로의 fail-safe 로 영구 유지
- `applyGhostClear` — forced clear 액션의 구현체로 재사용 (3.1.4)
- 휴지통 페이지의 ghost 표시 + 정리 액션 — 마이그레이션 도구 + 잔존 fail-safe (3.6)
- `GhostEvaluator.isGhost` 판정 SPI — fail-safe 검출 진입점

### 7.2 무엇을 deprecated 하는가

- `MarkableScanner` 의 ghost 관련 default 메서드들 — CP5 의 SPI 분리 시 `MarkableGhostOperator` 로 이전
- `is_deleted=true + trashed_*=null + Files.exists(path)` 의 "정상 케이스" (A2) 분류 — `DB_FS_CONSISTENCY.md §5.1` 갱신 필요. A2 는 더 이상 정상이 아니다.

### 7.3 문서 갱신 필요 항목

`DB_FS_CONSISTENCY.md` 에 적용할 변경 :

- §2.3 명제의 적용 범위 명시 — reconciliation 분류뿐 아니라 lifecycle 진입점도 포함
- §5.1 의 A2 케이스 재분류 — "정상" 표기 제거, "reconciliation 이 추종해야 할 drift 상태" 로 명시
- §3.4 (Ghost) 의 §6.4 결정 사항 갱신 — DCM3-1.2 "softDelete 정책 변경 안 함" 폐기. DCM3-2.x 로 대체.
- §6 에 MK3-2 항목 추가
- §7 의 미해결 항목 갱신 — 7.1 (Ghost auto-apply default) 는 본 슬라이스에서 자연스럽게 해소 (ghost 신규 생성 차단으로 default 가 무의미해짐)

---

## 8. 의식적으로 남겨두는 미해결 항목 (본 슬라이스 무관)

본 슬라이스에서 다루지 않고 *의도적으로 보류* 하는 사항. Claude Code 가 본 슬라이스 작업 중 이 항목들에 손대지 말 것 :

- **분산 인스턴스 대응** (§7.4) — `DeleteIntentRegistry` 가 in-memory 인 건 `NudgeRegistry` 와 동일 가정. 분산 도입은 별도 슬라이스.
- **ApplicationFileMover** (§7.5, MK4) — UI 에서 명시 mv. 본 슬라이스와 직교.
- **마커 secret 회전** (§7.3) — 별도 슬라이스. §11.3 의 DB backup 컬럼 도입과 연계 검토.

---

## 9. Claude Code 작업 시 주의사항

### 9.1 코드 탐색 우선

본 문서의 파일 경로는 모두 *추정* 이다. 작업 시작 전 다음 grep 으로 실제 위치 확인 :

```bash
# softDelete 호출 지점
grep -rn "softDelete" src/main/java/

# LifecycleEntity 위치
find src -name "LifecycleEntity.java"

# 각 도메인 service 위치
find src -path "*/service/*.java" | grep -iE "(os|bios|bmc|subprogram).*service"

# MarkableScanner 위치 + 구현체들
grep -rln "implements MarkableScanner" src/main/java/

# NudgeRegistry — 동형 패턴 참조
find src -name "NudgeRegistry.java"

# Audit 컴포넌트
grep -rln "AuditLog\|AuditEvent\|@Auditable" src/main/java/

# Reconciliation 진입점
find src -path "*/reconciliation/*" -name "*.java"
```

### 9.2 테스트 우선 작성

각 CP 는 테스트가 동반되어야 한다. 특히 :
- saga 의 race 시나리오는 통합 테스트로 검증 (Mockito 만으로는 부족)
- flag toggle 동작은 `@TestPropertySource` 로 양쪽 모두 테스트

### 9.3 기존 정책 리뷰 권장

본 문서를 받아 작업 시작 전, 다음 두 가지를 확인하면 안전 :
1. `DCM3-1.2` (softDelete 정책 변경 안 함) 의 결정 맥락이 본 진단과 어떻게 충돌하는지
2. `§5.1` 의 A2 케이스 보존 결정의 운영 사례 — 실제로 A2 가 자주 발생하는 환경인지, 거의 안 일어나는 환경인지. 후자라면 본 슬라이스 도입 부담은 매우 작음.

### 9.4 의문 발생 시 진행 중지

본 문서가 다루지 못한 코너 케이스 발견 시 무리한 추정 금지. 다음 항목은 사용자 결정 필요 :
- `Files.exists` 검사가 NFS / 분산 파일시스템에서 의미 있는지 (응답 지연 / 일관성)
- 트랜잭션 안의 `Files.exists` 가 acquire 한 row lock 과 어떻게 상호작용하는지
- saga 의 재시도 정책 (네트워크 일시 단절 시)

---

## 10. 본 분석에 대한 자체 비판

본 분석 자체가 외부 비판자 (Claude Project) 의 시각이므로, 다음을 자체 비판으로 남긴다 :

- **A2 케이스의 실제 빈도를 모름**. A2 가 운영 환경에서 거의 안 일어난다면 본 슬라이스의 정책 변화는 *과한 무게* 일 수 있음. 도입 전 audit log 분석으로 빈도 확인 권장.
- **race 안전성의 형식적 증명 부재**. saga 의 4 단계 사이 외부 변화 race 를 token TTL 로만 막는데, TTL 내에 사용자가 modal 을 응답하지 않고 그 사이 외부 변화가 일어나는 시퀀스가 존재할 수 있음. 모든 race 를 닫는 게 아니라 *의미 있게 좁히는* 것이 본 정책의 한계.
- **MK3-1 코드 폐기 비용을 0 으로 가정**. 실제로는 `applyGhostClear` 가 사용자 명시 액션의 구현체로 재해석되면서 호출 맥락이 바뀜. 기존 호출자의 의미가 변하지 않는지 코드 리뷰 필요.
- **운영자 학습 곡선 추정의 주관성**. "추가 1 클릭이 운영 사고 예방 효과로 상쇄된다" 는 주장은 본 분석자의 의견이고, 실제 운영자 피드백으로 검증돼야 함.

---

# 11. 본 슬라이스 외 시스템 비판 관찰 — Claude Code 가 인지할 것

> **본 장의 4 항목은 MK3-2 슬라이스에서 손대지 않는다.** 그러나 외부 비판 검토에서 도출된 시스템의 약점들이므로, 후속 슬라이스 또는 운영 모니터링 시 우선순위 후보로 인지할 것. 각 항목은 *문제 진단 / 근거 / 제안 방향* 순으로 정리.

## 11.1 비판 2 — 4 메커니즘의 책임 겹침 (PATH_DRIFT ↔ RESOURCE_RENAMED)

### 진단

`DB_FS_CONSISTENCY.md §3.1` 의 12 DriftKind 표에서 :

- `#1 PATH_DRIFT` : DB.path 위치 마커 부재. 다른 active 위치에 (resourceType, resourceId) 마커 발견. 본체 파일도 같이. → 자동 ON
- `#6 RESOURCE_RENAMED` : 같은 부모 디렉토리 내 다른 파일명으로 자원+마커 동시 발견 (sidecar 1:1 매칭). → 자동 ON

이 두 종은 **결과 행위가 동일** 하다. 둘 다 "자원이 active 트리 안에서 이동했다 → DB.path 를 새 위치로 갱신" 으로 수렴한다. 차이는 *분류 단계의 기준* 일 뿐 :

- PATH_DRIFT : 부모 디렉토리가 다를 수 있음
- RESOURCE_RENAMED : 같은 부모 디렉토리 안에서 파일명만 변경

### 근거

시나리오 A : `/opt/iso/foo.iso` → `/opt/iso-archive/foo.iso` (부모 변경) → PATH_DRIFT
시나리오 B : `/opt/iso/foo.iso` → `/opt/iso/foo-old.iso` (이름만 변경) → RESOURCE_RENAMED
시나리오 C : `/opt/iso/foo.iso` → `/opt/iso-archive/foo-old.iso` (둘 다 변경) → 분류기의 우선순위가 결정 ; 결과 행위는 동일

분류기가 시나리오 C 를 어느 enum 으로 떨어뜨릴지가 *디렉토리 비교 우선순위에 의존* 한다. 코드의 분류 로직을 정확히 들여다보지 않으면 어느 쪽으로 갈지 예측이 어렵고, 그것이 결과에 영향을 주지 않는다는 점에서 분류 자체의 가치가 약하다.

### 제안 방향

**옵션 A — 통합** : 두 enum 을 `RESOURCE_MOVED` 같은 단일 종으로 합치고, 디렉토리 변경 / 파일명 변경 / 둘 다는 detail 메시지로 구분. UI 에서 사용자에게 "자원이 이동되었습니다" 라는 단일 메시지 + 이전 경로 / 새 경로 표시.

**옵션 B — 명확한 분리** : 두 enum 을 유지하되 *겹치지 않게* 정의. 예 : 부모 디렉토리가 active 스캔 범위 안에 있는 경우만 RESOURCE_RENAMED, 그 외는 PATH_DRIFT. 분류 우선순위를 명시해 시나리오 C 의 모호성 제거.

옵션 A 가 §11.4 의 "12 DriftKind 카테고리 폭발" 비판과도 부분 정합. 두 비판이 같은 방향을 가리킨다.

### 우선순위

중. 운영 사고로 직결되진 않지만, 운영자가 reconciliation 페이지를 의미 있게 활용하는 데 인지 부담을 준다.

---

## 11.2 비판 3 — SPI default 누적 (`MarkableScanner` 16 메서드, ISP 위반)

### 진단

§8.2 의 자체 진단 :

> `MarkableScanner` 가 MK1 → MK3 → MK3-1 거치며 default 메서드가 16 개로 늘어남. 4 도메인이 모두 동일하게 모든 메서드를 override 하면 default 의미가 사라짐. 새 도메인 추가 시 어디까지 override 해야 하는지 인지 부담.

이는 단순한 메서드 수 누적이 아니라 **ISP (Interface Segregation Principle) 위반의 신호** 다. 16 메서드가 의미 그룹으로 *깔끔하게 4 분할 가능* 하다는 점이 결정적 단서 :

| 의미 그룹 | 메서드 |
|---|---|
| **인벤토리** | `supportedType`, `findActiveMarkables`, `findSoftDeletedResourceIds` |
| **Drift 적용** | `applyDriftedPath`, `recomputeManifestHash` |
| **Trash 액션** (MK3) | `findTrashed`, `findTrashedBefore`, `findTrashedBetween`, `extendTrashTtl`, `restoreFromTrash`, `purgeFromTrash` |
| **Ghost 액션** (MK3-1) | `isGhost`, `findGhostMarkables`, `applyGhostClear` |

### 근거

ISP 의 핵심 — *클라이언트는 자기가 안 쓰는 메서드에 의존하지 않아야 한다* — 가 어긋난다. 현재 :
- Reconciliation 인프라는 인벤토리 + drift 적용만 필요한데, trash / ghost 메서드까지 의존
- Trash controller 는 trash 메서드만 필요한데, 인벤토리 / drift / ghost 메서드까지 의존
- Ghost controller 도 마찬가지

게다가 MK3-2 도입 시 (`findActiveMarkableById` 신규 추가) 17 → 18 로 증가. MK4 (ApplicationFileMover) 도입 시 또 증가. **누적이 멈추지 않는 구조다.**

### 제안 방향

§3.5 에서 제시한 4 분할 :

```java
public sealed interface MarkableInventory permits ... {...}
public sealed interface MarkableDriftApplier permits ... {...}
public sealed interface MarkableTrashOperator permits ... {...}
public sealed interface MarkableGhostOperator permits ... {...}
```

도메인 구현체는 4 인터페이스를 모두 구현하지만, 인프라 컴포넌트는 자기 책임에 맞는 인터페이스만 의존. **sealed interface 를 쓰는 건 새 도메인 추가 시 컴파일 타임에 4 곳 모두 permit 등록을 강제** 하기 위함 — 누락 방지.

### 마이그레이션 전략

MK3-2 와 동시에 진행하면 변경 폭이 너무 커진다. **본 슬라이스 종료 후 MK3-3 (또는 S5-x) 별도 슬라이스로 분리** :

1. 신규 4 인터페이스 신설
2. `MarkableScanner` 에 신규 4 인터페이스 모두 extends 추가 (transition 동안 호환성 유지)
3. 인프라 컴포넌트들 의존성을 신규 인터페이스로 단계적 이전
4. `MarkableScanner` deprecated 표시 → 다음 다음 슬라이스에서 제거

### 우선순위

중. 본 슬라이스 (MK3-2) 가 메서드를 한 개 더 추가하므로, 가까운 시점에 진행 권장.

---

## 11.3 비판 5 — 마커 파일이 SPOF

### 진단

§8.1 의 자체 진단 :

> 운영자가 SSH 로 `cat marker.json` 후 직접 편집 가능 (HMAC 으로 변조 감지는 되지만 우회 가능). 마커가 손상되면 자원 자체는 멀쩡한데 등록 / 사용 불가.

이는 자체 진단의 *겉모습* 인데, 더 깊이 보면 본질은 다른 데 있다.

**근본 문제** : 마커 파일은 `(resourceType, resourceId)` 매핑의 **유일한 디스크 측 증거** 다. DB 에는 path 가 있지만 그 path 의 *정체성* 을 보존하는 매커니즘은 마커 파일이 유일하다. 즉 마커가 손상 / 삭제되면 :

- Reconciliation 이 자원을 ORPHAN 또는 MISSING 으로 분류 → 사용자 명시 액션 요구
- 자원 자체는 디스크에 멀쩡히 있지만, 시스템은 그 자원을 매칭할 길이 없음
- 운영자가 수동으로 마커를 재생성해야 회복 가능

### 근거

대안인 "마커 없이 `(resourceType, manifestHash)` 매칭" 은 §8.1 에서 적었듯 hash 충돌 시 모호해지는데, 더 큰 문제는 **자원 내용이 변경되면 매칭이 끊긴다** 는 점이다. 예 : BIOS 펌웨어 디렉토리 안에 README 를 추가하면 manifestHash 가 바뀌어 새로 매칭하다가 동일성을 잃는다.

즉 마커는 "내용 + 정체성" 을 분리하기 위한 도구이며, *그게 디스크에 박혀 있어야 외부 변화에 따라 정체성이 흔들리지 않는다*. 단순히 SPOF 라는 게 문제가 아니라 *그 SPOF 가 정체성 보존의 비용* 이라는 것이다.

### 제안 방향 — DB backup 컬럼

마커의 *내용물* 을 DB 에도 backup 으로 저장. 마커 발급 / 갱신 시 양쪽 동시 기록.

```sql
ALTER TABLE os_iso ADD COLUMN marker_blob JSON NULL;
ALTER TABLE bios_bundle ADD COLUMN marker_blob JSON NULL;
ALTER TABLE bmc_firmware ADD COLUMN marker_blob JSON NULL;
ALTER TABLE subprogram ADD COLUMN marker_blob JSON NULL;
```

저장 내용 : `MarkerContent` 객체 전체 (`resourceType`, `resourceId`, `attributes`, `manifestHash`, `signature`, `createdAt`).

#### 효과

1. **마커 손상 시 복구** : 디스크 마커가 깨졌거나 사라졌을 때 DB 의 backup 으로 재생성. 운영자 수동 작업 없음.
2. **HMAC secret 회전 (§7.3) 안전성 향상** : `triggerReissueAllSignatures` 가 디스크를 읽지 않고 DB 의 backup 으로 재서명 가능. secret 회전 중 디스크 마커가 일시적으로 깨져도 복구 경로 존재.
3. **무결성 교차 검증** : 디스크 마커와 DB backup 이 어긋나면 변조 의심 신호. 새 DriftKind `MARKER_DB_DIVERGED` 추가 가능.

#### 비용

1. DB row 크기 증가 (BIOS bundle 의 attributes 가 큰 경우 수 KB) — JSON 컬럼이라 검색 인덱스 영향은 적음
2. 마커 발급 / 갱신 시 트랜잭션 안에 DB 쓰기 추가 — 약간의 지연
3. backup 의 일관성 자체가 또 다른 정합성 문제 — DB-truth ↔ FS-truth 분리에 backup 차원이 추가됨

비용 3 이 가장 미묘. **backup 은 FS-truth 의 "보조" 로만 쓰고, 일관성 분쟁 시 항상 FS 가 우선** 이라는 정책을 명시해야 함. backup 이 truth 가 되어버리면 §2.1 의 "Active 트리 = FS-truth" 원칙이 흔들린다.

### 우선순위

중-고. 운영 사고 가능성이 있고 (마커 손상 → 자원 사용 불가), HMAC secret 회전 (§7.3 미해결) 도 함께 풀린다. 별도 슬라이스로 진행 권장.

---

## 11.4 비판 6 — 12 DriftKind 카테고리 폭발 + 3 축 매트릭스 UI 대안

### 진단

§8.3 의 자체 진단 :

> 자동 ON 5종 / OFF 7종. 사용자가 각 의미를 모두 이해해야 reconciliation 페이지를 의미 있게 활용 가능.

자체 진단은 "단순화 가능성" 까지만 제기하고, 자동 적용 액션이 다르므로 합치기 어렵다고 결론. 그러나 **사용자가 이해해야 하는 건 12 종이 아니라 3 개의 축** 이다.

### 근거 — 3 축 분해

12 DriftKind 를 3 축으로 재구성하면 :

| 축 | 가능한 값 |
|---|---|
| **Lifecycle 상태** | active / soft-deleted |
| **FS 위치** | DB.path 위치 정상 / 다른 active 위치 / trash 위치 / 어디에도 없음 |
| **마커 상태** | 정상 / 서명 깨짐 / 해시 불일치 / 부재 |

12 DriftKind 는 이 3 축의 *의미 있는 조합* 이다. 모든 조합 (2 × 4 × 4 = 32) 이 의미 있는 건 아니지만, 12 종은 그 중 의미 있는 부분집합.

예시 매핑 :
- `PATH_DRIFT` = (active) × (다른 active 위치) × (정상)
- `MISSING` = (active) × (어디에도 없음) × (부재)
- `ORPHAN` = (없음 — DB row 부재) × (다른 active 위치) × (정상)
- `SIGNATURE_INVALID` = (active) × (DB.path 위치 정상) × (서명 깨짐)
- `SOFTDEL_ESCAPE_TO_ORIGINAL` = (soft-deleted) × (DB.path 위치 정상) × (정상)
- `GHOST_DB_ROW` = (soft-deleted) × (어디에도 없음) × (부재)

### 제안 방향 — UI 재구성 (코드 변경 최소)

enum 자체는 그대로 유지 (코드 변경 비용 큼). **UI 표현만 3 축 매트릭스로 변환** :

#### Reconciliation 페이지 구조 변경

**현재** (추정) : 12 종 enum 별로 행을 나열, 각 행이 자원 목록.

**변경 후** : 자원 1 건당 1 행. 컬럼은 :
- 자원 식별자 (도메인 / 이름 / ID)
- Lifecycle 상태 컬럼 (`active` / `soft-deleted`)
- FS 위치 컬럼 (정상 / 다른 위치 → 정확한 경로 표시 / trash / 부재)
- 마커 상태 컬럼 (정상 / 서명 깨짐 / 해시 불일치 / 부재)
- 권장 액션 (자동 적용 가능 여부 + 액션 버튼)

각 컬럼은 색상 / 아이콘으로 상태 표현 (정상 = 녹색, 주의 = 노랑, 위험 = 빨강).

#### 사용자 인지 변화

- "이 자원은 [soft-deleted] 인데 [어디에도 없으며] [마커도 부재] 다" 라는 *서술적 이해* 가 가능해짐
- 12 종 enum 명을 외울 필요 없음
- 같은 종류 drift 가 여러 건 있어도 컬럼이 같으니 패턴 인식 용이

#### 코드 변경 범위

- DriftKind enum / 분류 로직 / 자동 적용 정책 — **변경 없음**
- DriftReport DTO 에 3 축 정보 추가 (현재는 enum 만 있을 가능성 큼). 분류 결과를 3 축으로 *전개* 하는 매핑 함수 추가
- UI 페이지 (template / React 컴포넌트) 재구성

### 우선순위

중. 운영 사고로 직결되진 않지만, 운영자의 reconciliation 활용도를 높이는 데 큰 영향. UI 슬라이스로 분리 진행 권장.

### 추가 효과

§11.1 (PATH_DRIFT ↔ RESOURCE_RENAMED 겹침) 도 3 축 표현에선 자연 해소 — 둘 다 "FS 위치 = 다른 active 위치" 컬럼으로 표현되고, 정확한 경로가 표시되니 사용자가 어느 종류인지 알 필요가 없다.

---

# 12. 종합 — 비판 → 슬라이스 매핑

| 비판 | 슬라이스 | 우선순위 | 의존성 |
|---|---|---|---|
| 1. Ghost 가 필요해진 이유 | **MK3-2 (본 슬라이스)** | 최우선 | — |
| 4. Ghost race 의 본질 | **MK3-2 (본 슬라이스)** | 최우선 | 1 과 동시 해소 |
| 3. SPI default 누적 | MK3-3 (별도 슬라이스) | 중-고 | MK3-2 후 권장 |
| 5. 마커 파일 SPOF | MK3-4 또는 별도 (DB backup 컬럼) | 중-고 | §7.3 (secret 회전) 과 함께 |
| 6. 12 DriftKind 카테고리 폭발 | UI 슬라이스 (S5-x) | 중 | 코드 변경 최소, UI 만 |
| 2. 메커니즘 책임 겹침 (PATH ↔ RENAMED) | 6 과 함께 자연 해소 또는 enum 통합 슬라이스 | 중 | 6 과 같이 |

---

> **Claude Code 에게** : 본 문서는 최종 결정이 아니라 *방향 제시* 다. §1~§10 (MK3-2 본문) 은 즉시 작업 대상이고, §11 (비판 관찰 4 종) 은 *후속 슬라이스 후보로 인지만 할 것* — 본 슬라이스 진행 중 이 항목들에 손대지 말 것. 작업 시작 전 §9.1 의 grep 으로 실제 코드를 확인하고 본 문서의 추정과 차이가 있으면 다시 사용자와 협의할 것. 본 문서의 어느 결정이라도 코드 현실과 충돌하면 사용자 판단을 우선할 것.
