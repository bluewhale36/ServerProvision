-- ES-2 · 실행 데이터 모델 정비 (2026-08-20)
--
-- E 실행 준비도 토론(2026-08-15 종결) 확정 D3 · D4 의 스키마 이행이다. 전제 = 정본 DDL 체인이 적용된
-- 깨끗한 DB(schema.sql + U3-1 이후 슬라이스 DDL — FK · 인덱스가 정본 이름을 가진 상태).
-- 실 DB(server_provision)는 Hibernate 자동명 FK · 제약 결손이 있어 이 파일보다 먼저
-- ddl/ES-2_realdb_alignment.sql 로 정본 이름 · 결손을 맞춘 뒤 이 파일을 적용한다.
--
-- 적용에는 ALTER 권한 계정이 필요하다(claude_code 불가). 적용 후 SHOW CREATE TABLE 로 검증한다.

-- ────────────────────────────────────────────────────────────────────────────
-- (A) 원장 개명 — setup_step → provisioning_history (D3)
--     "무엇의 step 인지 · 이력인지 계획인지" 를 이름이 말하게 한다.
-- ────────────────────────────────────────────────────────────────────────────
RENAME TABLE setup_step TO provisioning_history;
-- FK · 인덱스의 정본 이름(fk_provisioning_history_guest · idx_provisioning_history_guest_server)은
-- 실 DB 에선 선행하는 ES-2_realdb_alignment.sql 이, 신규 DB 에선 갱신된 schema.sql 이 보장한다 —
-- 환경마다 다른 옛 이름(Hibernate 자동명 vs 수동명)을 이 파일이 알 필요가 없게 하기 위한 분업이다.

-- ────────────────────────────────────────────────────────────────────────────
-- (B) 커서 step 단위 전환 — current_phase → current_step (D3)
--     ① step 컬럼 신설 → ② 기존 행 값 이행 → ③ 구 컬럼 제거 순서.
-- ────────────────────────────────────────────────────────────────────────────
ALTER TABLE provisioning_progress
    ADD COLUMN current_step ENUM('NETWORK_ALLOCATING','INIT_PERSISTING','DIAGNOSTIC_BOOTING',
        'INFORMATION_COLLECTING','INFORMATION_PERSISTING','IPMI_SETTING','BIOS_UPDATING','BMC_UPDATING',
        'BIOS_SETTING','BMC_SETTING','RAID_CONFIGURATION','OS_INSTALLING','OS_SETTING','TESTING')
        DEFAULT NULL AFTER current_phase;

-- 값 이행 ① — phase 어휘를 대표 step 으로. 커서 의미론 = "도달했거나 향하는 목표 step"(D-1)이므로
-- BOOTSTRAPPING 은 다음 목표(진단 진입)로, 진단 phase 는 원장 최신 행(없으면 진입 step)으로 옮긴다.
UPDATE provisioning_progress p SET p.current_step =
    CASE p.current_phase
        WHEN 'BOOTSTRAPPING'      THEN 'DIAGNOSTIC_BOOTING'
        WHEN 'DIAGNOSE_LINUX'     THEN COALESCE(
            (SELECT h.step_code FROM provisioning_history h
              WHERE h.guest_server_id = p.guest_server_id
                AND h.step_code IN ('DIAGNOSTIC_BOOTING','INFORMATION_COLLECTING','INFORMATION_PERSISTING','IPMI_SETTING')
              ORDER BY h.started_at DESC, h.created_at DESC LIMIT 1),
            'DIAGNOSTIC_BOOTING')
        WHEN 'FIRMWARE_UPDATING'  THEN 'BIOS_UPDATING'
        WHEN 'FIRMWARE_SETTING'   THEN 'BIOS_SETTING'
        WHEN 'RAID_CONFIGURATION' THEN 'RAID_CONFIGURATION'
        WHEN 'OS_INSTALLING'      THEN 'OS_INSTALLING'
        WHEN 'OS_SETTING'         THEN 'OS_SETTING'
        WHEN 'TESTING'            THEN 'TESTING'
    END;

-- 값 이행 ② — 게스트 보고 실패 행은 실패 지점이 정확히 남아 있으므로 커서가 그 step 을 가리키게 한다
-- (D3: "failed_at 시점의 step 커서가 실패 지점을 답한다" 의 소급 성립).
UPDATE provisioning_progress SET current_step = failed_step_code
 WHERE failed_at IS NOT NULL AND failed_step_code IS NOT NULL;

-- 값 이행 ③ — 운영자 수동 실패 전환(구 표식 = failed_step_code IS NULL)의 새 거처(D-5):
-- 원장 instant 행(origin=operator)을 소급 적재해 화면의 '운영자 전환' 구분을 보존한다.
INSERT INTO provisioning_history
    (id, guest_server_id, step_code, status, status_meta, started_at, finished_at, created_at, updated_at)
SELECT UUID(), p.guest_server_id, p.current_step, 'FAILED', '{"origin":"operator"}',
       p.failed_at, p.failed_at, NOW(6), NOW(6)
  FROM provisioning_progress p
 WHERE p.failed_at IS NOT NULL AND p.failed_step_code IS NULL;

-- 구 컬럼 제거 — phase_meta(작성자 0 · 소비자 0, D3) · failed_step_code(커서 파생으로 대체, D3) ·
-- current_phase(step 파생으로 대체).
ALTER TABLE provisioning_progress
    DROP COLUMN phase_meta,
    DROP COLUMN failed_step_code,
    DROP COLUMN current_phase;

-- ────────────────────────────────────────────────────────────────────────────
-- (C) 운동 양태 컬럼 신설 — motion (D4)
--     HOLD 는 값만 선언(작성자 = E2-1 준비도 훅). CHECK 는 백필 뒤에 건다.
-- ────────────────────────────────────────────────────────────────────────────
ALTER TABLE provisioning_progress
    ADD COLUMN motion ENUM('AWAITING_BOOT','STEP_RUNNING','HOLD') DEFAULT NULL AFTER current_step;

-- 실행 창 안(개시됨 · 미실패 · 미종단) 행 백필 — 재부팅 폴링 대기가 안전한 기본값.
UPDATE provisioning_progress SET motion = 'AWAITING_BOOT'
 WHERE started_at IS NOT NULL AND failed_at IS NULL AND completed_at IS NULL;

-- D4 불변식의 DB 이중 방어 — ddl-auto=validate 는 CHECK 를 검사하지 않으므로 수동 DDL 로만 선다:
-- motion ≠ NULL ⇔ 실행 창 안(started_at ≠ NULL ∧ failed_at = NULL ∧ completed_at = NULL).
ALTER TABLE provisioning_progress
    ADD CONSTRAINT chk_progress_motion_window CHECK (
        (motion IS NULL     AND (started_at IS NULL OR failed_at IS NOT NULL OR completed_at IS NOT NULL))
     OR (motion IS NOT NULL AND started_at IS NOT NULL AND failed_at IS NULL AND completed_at IS NULL));

-- ────────────────────────────────────────────────────────────────────────────
-- (D) 할당 스냅샷 개명 — 스냅샷 의미를 이름에 싣는다 (D3, 단수형 = 기존 전 테이블 관례)
--     생성 컬럼(active_guest_id) · 부분 유일성(uk_active_assignment_per_guest)은 RENAME 에 보존된다.
-- ────────────────────────────────────────────────────────────────────────────
RENAME TABLE setting_assignment TO setting_assignment_snapshot;
RENAME TABLE assigned_process  TO assigned_process_snapshot;

ALTER TABLE setting_assignment_snapshot
    DROP FOREIGN KEY fk_setting_assignment_guest,
    ADD CONSTRAINT fk_setting_assignment_snapshot_guest
        FOREIGN KEY (guest_server_id) REFERENCES guest_server (id) ON DELETE CASCADE;

ALTER TABLE assigned_process_snapshot
    DROP FOREIGN KEY fk_assigned_process_assignment,
    ADD CONSTRAINT fk_assigned_process_snapshot_assignment
        FOREIGN KEY (assignment_id) REFERENCES setting_assignment_snapshot (id) ON DELETE CASCADE;

ALTER TABLE assigned_process_snapshot
    RENAME INDEX uk_assigned_process_type TO uk_assigned_process_snapshot_type;
