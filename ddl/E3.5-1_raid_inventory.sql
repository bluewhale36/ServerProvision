-- ============================================================
-- E3.5-1 : RAID 인벤토리 — step 3분할(ENUM 교체) + 인벤토리 적재 컬럼
-- ------------------------------------------------------------
-- ProvisioningPhaseStep 의 RAID_CONFIGURATION 상수를 RAID_INVENTORY_COLLECTING ·
-- RAID_APPLYING · RAID_VERIFYING 셋으로 교체했다(E3.5-0-3 결정 D-10 · plan D-1).
--
-- 적용 전 확인 (불가침, U4-1-1 DDL 과 같은 규칙):
--   · setup_step.step_code · provisioning_progress.failed_step_code 는 환경에 따라 ENUM 또는 VARCHAR 다.
--     SHOW CREATE TABLE 로 실제 타입을 본 뒤 — ENUM 이면 해당 문을 실행하고, VARCHAR 면 생략한다.
--   · 'RAID_CONFIGURATION' 값 제거 전 적재 행이 0 인지 반드시 확인한다(그 phase 에 도달한 게스트가
--     없어야 정상 — 있으면 값을 유지한 채 3 값만 추가하고 세션과 상의):
--       SELECT COUNT(*) FROM setup_step WHERE step_code = 'RAID_CONFIGURATION';
--       SELECT COUNT(*) FROM provisioning_progress WHERE current_step = 'RAID_CONFIGURATION' OR failed_step_code = 'RAID_CONFIGURATION';
--   · provisioning_progress.current_phase(ProvisioningPhase) · setting_process.process_type 은 무변경 —
--     phase · 정의서 단계 타입의 상수는 그대로다.
-- 적용 계정 : claude_code 가 ALTER 보유(2026-08-23 실측) — 적용 후 SHOW CREATE TABLE 대조.
-- ============================================================

-- ① SetupStep 원장 (ProvisioningPhaseStep) — ENUM 인 환경만
ALTER TABLE setup_step
    MODIFY COLUMN step_code ENUM(
        'BIOS_SETTING',
        'BIOS_UPDATING',
        'BMC_SETTING',
        'BMC_UPDATING',
        'DIAGNOSTIC_BOOTING',
        'INFORMATION_COLLECTING',
        'INFORMATION_PERSISTING',
        'INIT_PERSISTING',
        'IPMI_SETTING',
        'NETWORK_ALLOCATING',
        'OS_INSTALLING',
        'OS_SETTING',
        'RAID_APPLYING',
        'RAID_INVENTORY_COLLECTING',
        'RAID_VERIFYING',
        'TESTING'
    ) DEFAULT NULL;

-- ② 게스트 진행 커서 step (Hibernate 생성 스키마의 current_step 이 ENUM 인 환경만)
ALTER TABLE provisioning_progress
    MODIFY COLUMN current_step ENUM(
        'NETWORK_ALLOCATING',
        'INIT_PERSISTING',
        'DIAGNOSTIC_BOOTING',
        'INFORMATION_COLLECTING',
        'INFORMATION_PERSISTING',
        'IPMI_SETTING',
        'BIOS_UPDATING',
        'BMC_UPDATING',
        'BIOS_SETTING',
        'BMC_SETTING',
        'RAID_INVENTORY_COLLECTING',
        'RAID_APPLYING',
        'RAID_VERIFYING',
        'OS_INSTALLING',
        'OS_SETTING',
        'TESTING'
    ) DEFAULT NULL;

-- (참고) provisioning_progress.failed_step_code 는 ES-2 D-5 가 소멸시켰다 — 대상 아님(U4-1-1 DDL ⑤ 는 당시 기준).

-- ③ RAID 인벤토리 적재 컬럼 (plan D-2) — 상세 화면 · 계획 산출(E3.5-2)의 조회 모델
ALTER TABLE guest_server_detail
    ADD COLUMN raid_inventory_json LONGTEXT DEFAULT NULL
        COMMENT 'RAID 카드 · 물리 디스크 · 기존 볼륨 인벤토리(RaidInventory JSON) — 원문은 원장 statusMeta 보존';
