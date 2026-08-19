-- ============================================================
-- U4-1-1 (v2) : RAID 구성 단계 신설 — 정의서 단계 타입 · 실행 phase · 하위 step 상수 추가에 따른 ENUM 컬럼 확장
-- ------------------------------------------------------------
-- Java enum 에 상수를 더하면 DB ENUM 컬럼도 함께 확장해야 한다 — 누락 시 저장 · 보고가
-- "Data truncated for column ..." 500 으로 실패한다(E1-1 이 setup_step.step_code 에서 겪은 것과 같다).
--
-- 적용 전 확인 (불가침) : 아래 네 컬럼은 환경에 따라 ENUM 이거나 VARCHAR 다.
--   · ddl/U2-3_setting_definition.sql · ddl/U3-1_setting_assignment.sql 은 VARCHAR(32) 로 만들었고,
--   · Hibernate 가 생성한 스키마(샌드박스 · ddl/schema.sql dump)는 ENUM(...) 이다.
--   SHOW CREATE TABLE <table>\G 로 실제 타입을 본 뒤 — ENUM 이면 아래 해당 문을 실행하고, VARCHAR 면 그 문은 생략한다
--   (VARCHAR 는 새 상수명을 그대로 담으므로 변경이 필요 없다).
--   provisioning_progress.failed_step_code 는 E1-0a 가 VARCHAR(25) 로 만들었으나 Hibernate 생성 스키마에서는 ENUM 이다 — 같은 규칙(⑤, ENUM 일 때만).
--   변경 불요 컬럼 : assigned_process.owned_phases(안정 코드 CSV — 'RAID_CONFIG' 는 코드 변환기가 담당).
-- 적용 계정 : ALTER 권한 필요(claude_code 불가). 적용 후 SHOW CREATE TABLE 로 대조.
-- 상수 자리 : SettingProcessType.RAID_CONFIGURATION(BASIC_SETTING 다음) · ProvisioningPhase.RAID_CONFIGURATION(FIRMWARE_SETTING 다음)
--            · ProvisioningPhaseStep.RAID_CONFIGURATION(BMC_SETTING 다음). ENUM 멤버 순서는 저장에 영향 없다(문자열 저장).
-- ============================================================

-- ① 정의서 단계 타입 (SettingProcessType)
ALTER TABLE setting_process
    MODIFY COLUMN process_type ENUM(
        'BASIC_SETTING',
        'BASIC_UPDATE',
        'OS_INSTALLATION',
        'OS_SETTING',
        'RAID_CONFIGURATION'
    ) NOT NULL;

-- ② 할당 스냅샷의 단계 타입 (표시 · 감사 전용 — 권위는 owned_phases)
ALTER TABLE assigned_process
    MODIFY COLUMN process_type ENUM(
        'BASIC_SETTING',
        'BASIC_UPDATE',
        'OS_INSTALLATION',
        'OS_SETTING',
        'RAID_CONFIGURATION'
    ) NOT NULL;

-- ③ 게스트 진행 커서 (ProvisioningPhase)
ALTER TABLE provisioning_progress
    MODIFY COLUMN current_phase ENUM(
        'BOOTSTRAPPING',
        'DIAGNOSE_LINUX',
        'FIRMWARE_SETTING',
        'FIRMWARE_UPDATING',
        'OS_INSTALLING',
        'OS_SETTING',
        'RAID_CONFIGURATION',
        'TESTING'
    ) DEFAULT NULL;

-- ④ SetupStep 원장 (ProvisioningPhaseStep) — E1-1 형식
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
        'RAID_CONFIGURATION',
        'TESTING'
    ) DEFAULT NULL;

-- ⑤ 실패 지점 step (ENUM 인 환경만 — VARCHAR(25) 면 생략)
ALTER TABLE provisioning_progress
    MODIFY COLUMN failed_step_code ENUM(
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
        'RAID_CONFIGURATION',
        'TESTING'
    ) DEFAULT NULL;
