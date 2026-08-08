-- MK4-1 — 드리프트를 '이번 회차의 관측' 에서 '고쳐질 때까지 지속되는 문제' 로 승격.
--
-- 종전 모델에서 drift 한 행은 문제와 관측을 겸했다. 같은 문제가 세 번의 점검에서 발견되면 서로
-- 무관한 세 행이 되어 언제부터 있었는지 · 이번에 새로 생긴 것인지 알 수 없었고(진단 후보 2-1),
-- 해소하면 그 행이 물리 삭제되어 지난 보고서의 건수가 사후에 줄었다(후보 2-4).
--
-- 새 모델은 셋으로 나눈다.
--   · drift             — 문제. 신원 = (resource_type, resource_id, kind). 같은 신원의 열린 문제는 하나.
--   · drift_observation — 회차별 사실. 어느 보고서가 이 문제를 또 봤는가.
--   · drift_handling    — 가해진 처리의 이력. 되돌리기에 필요한 값 둘(previous_path · moved_to_path)을 함께 보관.
--
-- 적용 방법 (claude_code 계정은 ALTER 권한이 없으므로 ALTER 권한 계정으로 실행):
--   mysql -u root -p server_provision < sql/MK4-1_drift_persistence.sql
-- 적용 확인:
--   SHOW CREATE TABLE drift;  SHOW CREATE TABLE drift_observation;  SHOW CREATE TABLE drift_handling;

-- ── 1. 기존 drift 행 정리 ────────────────────────────────────────────────────────
-- 구행은 회차마다 중복 생성된 관측이라, 보고서 연결을 끊는 순간 같은 신원의 열린 문제가 여러 개
-- 남아 새 모델의 유일성 전제를 처음부터 깨뜨린다. 중복을 골라 병합할 근거도 없다 —
-- 어느 행이 '처음 본 시각' 인지는 알아도 그 사이의 처리 이력은 애초에 기록된 적이 없다.
-- 드리프트 목록은 점검을 한 번 돌리면 그대로 재생성되는 파생 데이터이므로 비우고 시작한다.
-- (보고서 자체와 그 메타데이터는 보존한다 — 언제 무엇을 몇 건 봤는지는 역사적 사실이다.)
DELETE FROM drift;

-- ── 2. drift — 보고서 연결 제거 ─────────────────────────────────────────────────
-- 이제 보고서를 가리키는 쪽은 관측이다. 문제는 특정 회차에 속하지 않는다.
ALTER TABLE drift DROP FOREIGN KEY FK5xmpogqbx1vxcadgc1rbqddoa;
ALTER TABLE drift DROP COLUMN drift_report_id;

-- ── 3. drift — 수명 · 상태 컬럼 ─────────────────────────────────────────────────
-- detected_at("이 회차에 봤다")은 의미가 바뀌었으므로 이름도 바꾼다. 옛 이름을 그대로 두면
-- "언제부터 있었나" 를 읽는 코드가 회차 시각으로 오해한다.
ALTER TABLE drift CHANGE COLUMN detected_at first_detected_at DATETIME(6) NOT NULL;

ALTER TABLE drift
    ADD COLUMN last_observed_at  DATETIME(6)  NOT NULL             AFTER first_detected_at,
    ADD COLUMN observation_count INT          NOT NULL DEFAULT 1   AFTER last_observed_at,
    ADD COLUMN status            VARCHAR(16)  NOT NULL DEFAULT 'OPEN' AFTER observation_count,
    ADD COLUMN resolved_at       DATETIME(6)  DEFAULT NULL         AFTER status,
    ADD COLUMN resolved_by       VARCHAR(32)  DEFAULT NULL         AFTER resolved_at,
    ADD COLUMN snooze_until      DATETIME(6)  DEFAULT NULL         AFTER resolved_by,
    ADD COLUMN snooze_window     VARCHAR(32)  DEFAULT NULL         AFTER snooze_until,
    ADD COLUMN snooze_reason     VARCHAR(500) DEFAULT NULL         AFTER snooze_window;

-- 점검이 매 자원마다 신원으로 열린 문제를 찾는다 — 그 조회의 인덱스.
-- 유일 제약이 아니라 인덱스인 이유: 해결된 문제는 재사용하지 않으므로 같은 신원의 RESOLVED 행이
-- 여러 개 쌓인다. 열린 문제의 유일성은 동시 점검 자체가 '이미 실행 중' 으로 막혀 생성 경로가
-- 단일하다는 사실이 보장한다(CP1 결정 D2). 인스턴스가 여럿으로 늘어나는 시점이 이 판단을 다시 볼 시점이다.
CREATE INDEX idx_drift_identity ON drift (resource_type, resource_id, kind, status);

-- ── 4. drift_observation — 회차가 문제를 또 봤다는 기록 ──────────────────────────
CREATE TABLE drift_observation (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    drift_id        BIGINT       NOT NULL,
    drift_report_id BIGINT       NOT NULL,
    observed_at     DATETIME(6)  NOT NULL,
    old_path        VARCHAR(1024) NOT NULL,               -- 그 회차 시점의 스냅샷(문제 쪽은 최신값 유지)
    new_path        VARCHAR(1024) DEFAULT NULL,
    detail          VARCHAR(1024) DEFAULT NULL,
    observed_hash   VARCHAR(64)   DEFAULT NULL,
    KEY idx_drift_observation_drift (drift_id),
    KEY idx_drift_observation_report (drift_report_id),
    -- 한 회차가 같은 문제를 두 번 적재할 수 없다 — 스캔 로직의 버그가 조용히 건수를 부풀리는 것을 막는다.
    CONSTRAINT uk_drift_observation_drift_report UNIQUE (drift_id, drift_report_id),
    CONSTRAINT fk_drift_observation_drift FOREIGN KEY (drift_id)
        REFERENCES drift (id) ON DELETE CASCADE,
    -- 보고서 FIFO retention(기본 100건)이 오래된 회차를 지울 때 그 회차의 관측도 함께 사라진다.
    -- 문제 자체는 남는다 — 관측이 모두 정리돼도 '언제부터 있었나' 는 drift.first_detected_at 이 들고 있다.
    CONSTRAINT fk_drift_observation_report FOREIGN KEY (drift_report_id)
        REFERENCES drift_report (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- ── 5. drift_handling — 문제에 가한 처리의 이력 ─────────────────────────────────
CREATE TABLE drift_handling (
    id            BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    drift_id      BIGINT        NOT NULL,
    handled_at    DATETIME(6)   NOT NULL,
    action        VARCHAR(32)   NOT NULL,
    -- 되돌릴 수 있는 처리였는지를 판정 시점에 얼려 둔다. 규칙(DriftHandlingAction.reversibleFor)이
    -- 나중에 바뀌어도 과거 이력이 말하는 사실은 변하면 안 된다(탐지 수 스냅샷과 같은 원리).
    reversible    BIT(1)        NOT NULL,
    previous_path VARCHAR(1024) DEFAULT NULL,             -- 되돌리기 값 ① 처리 전 경로
    moved_to_path VARCHAR(1024) DEFAULT NULL,             -- 되돌리기 값 ② 파일을 옮겼다면 옮겨 둔 위치
    note          VARCHAR(500)  DEFAULT NULL,             -- 운영자 사유(현재는 '두고 보기' 에서만)
    KEY idx_drift_handling_drift (drift_id, handled_at),
    CONSTRAINT fk_drift_handling_drift FOREIGN KEY (drift_id)
        REFERENCES drift (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
