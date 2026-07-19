-- E1-1 — SetupStep 원장에 부팅 마일스톤 step 추가 (DIAGNOSTIC_BOOTING)
-- ProvisioningPhaseStep 에 Java enum 상수를 추가하면 DB ENUM 컬럼도 함께 확장해야 한다 —
-- 누락 시 에이전트의 step 보고가 "Data truncated for column 'step_code'" 500 으로 실패한다
-- (2026-07-19 QEMU 스모크 실측 — plan 의 "스키마 변경 0" 판단을 정정하는 DDL).
-- provisioning_progress.failed_step_code 는 varchar(25) 라 변경 불요.
-- 적용 계정: ALTER 권한 필요 (claude_code 가능). 적용 후 SHOW CREATE TABLE 로 검증.

ALTER TABLE setup_step
    MODIFY COLUMN step_code enum(
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
        'TESTING'
    ) DEFAULT NULL;
